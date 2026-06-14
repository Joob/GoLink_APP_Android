package co.golink.tester.data.backup

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.golink.tester.data.AppLogger
import co.golink.tester.data.auth.TokenStore
import co.golink.tester.data.upload.UploadManager
import co.golink.tester.data.upload.UploadTask
import co.golink.tester.network.SettingsApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: AutoBackupPreferences,
    private val scanner: MediaScanner,
    private val uploadManager: UploadManager,
    private val tokenStore: TokenStore,
    private val settingsApi: SettingsApi,
    private val logger: AppLogger,
    private val manager: AutoBackupManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo() = BackupNotifications.foregroundInfo(
        applicationContext,
        BackupNotifications.buildProgress(
            context = applicationContext,
            title = "A preparar backup…",
            text = "A analisar a galeria",
            indeterminate = true,
        ),
    )

    override suspend fun doWork(): Result = try {
        runBackup()
    } catch (c: CancellationException) {
        // Normal cancellation (user toggled off, REPLACE policy, etc.). Let it
        // propagate so WorkManager can mark us as STOPPED cleanly.
        BackupNotifications.cancelProgress(applicationContext)
        throw c
    } catch (t: Throwable) {
        // Last-resort safety net. Anything reaching here would otherwise crash
        // the worker thread and, on some OEM builds, the entire app process.
        logger.log("AutoBackup", "Erro inesperado no worker: ${t.message}")
        preferences.lastError = "Erro inesperado: ${t.message ?: t::class.simpleName}"
        BackupNotifications.cancelProgress(applicationContext)
        Result.retry()
    } finally {
        manager.endRun()
    }

    private suspend fun runBackup(): Result {
        if (!preferences.enabled) return Result.success()

        maybeSetForeground()

        // Auth check: we go straight to the token store. SessionManager is
        // async-initialised and on a freshly-restarted process (which is
        // exactly when WorkManager wakes us up) its state can still be Loading
        // even though the user is perfectly logged in. The OkHttp AuthInterceptor
        // reads the same token, so if it's present we *are* authenticated.
        val storedToken = runCatching { tokenStore.token }.getOrNull()
        if (storedToken.isNullOrBlank()) {
            preferences.lastError = "Sem sessão activa — inicia sessão para activar o backup."
            return Result.retry()
        }

        if (!hasMediaPermissions()) {
            preferences.lastError = "Falta permissão para ler fotos/vídeos. Reactiva a permissão e tenta de novo."
            return Result.failure()
        }

        // Sem "Acesso a todos os ficheiros" o MediaStore esconde os ficheiros
        // não-multimédia de Downloads/Documentos — o backup corre na mesma,
        // mas incompleto. Fica no log para diagnóstico.
        if ((preferences.includeDocuments || preferences.includeDownloads) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            logger.log("AutoBackup", "Sem acesso a todos os ficheiros — Downloads/Documentos limitados a multimédia")
        }

        // Honra a flag remota: a app web pode PAUSAR o backup automático
        // (mobile_backup_enabled=false). Ligar continua a ser exclusivo do
        // dispositivo. Lemos a flag uma vez por execução; se o servidor disser
        // que está desactivado, desligamos localmente e terminamos sem enviar.
        //
        // CRITICAL: runCatching catches every Throwable, including CancellationException.
        // If the worker is cancelled (REPLACE policy, user toggled off, Doze stop,
        // process restart) mid-request, the cancellation must propagate — otherwise
        // the outer try/catch in doWork can't tell the difference between "user
        // cancelled" and "network failed", and we end up showing "Sem ligação ao
        // servidor: Job was cancelled" with the work in a retry loop.
        val serverEnabled = try {
            val response = settingsApi.getMobileBackupSetting()
            when {
                // Falha de leitura é tratada como "manter ligado" para não
                // interromper backups por um corpo malformado.
                response.isSuccessful -> response.body()?.mobile_backup_enabled ?: true
                response.code() == 401 -> {
                    preferences.lastError = "Sessão expirou — volta a iniciar sessão."
                    return Result.retry()
                }
                else -> {
                    val body = response.errorBody()?.string()?.take(200)
                    preferences.lastError = "Backend rejeitou pedido (HTTP ${response.code()}): ${body ?: "?"}"
                    logger.log("AutoBackup", "Leitura backend falhou: ${response.code()}")
                    return Result.retry()
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            preferences.lastError = "Sem ligação ao servidor: ${t.message ?: t::class.simpleName}"
            logger.log("AutoBackup", "Leitura backend erro: ${t.message}")
            return Result.retry()
        }

        if (!serverEnabled) {
            logger.log("AutoBackup", "Backup pausado a partir da Web — a desactivar localmente")
            preferences.lastError = "Backups automáticos pausados a partir da Web."
            manager.disable()
            return Result.success()
        }

        // Snapshot total work for accurate "X of N" progress. Cancellation must
        // still propagate; only treat genuine MediaStore errors as "0 pending".
        val sources = enabledSources()
        val totalPending = sources.sumOf { src ->
            safeCount { scanner.countNew(src.collection, src.cursor()) }
        }

        if (totalPending == 0) {
            preferences.lastError = null
            preferences.lastBackupAt = System.currentTimeMillis()
            BackupNotifications.cancelProgress(applicationContext)
            return Result.success()
        }

        logger.log("AutoBackup", "$totalPending ficheiro(s) por enviar")
        manager.startRun(totalPending)

        var uploadedTotal = 0
        var conflictTotal = 0
        var failedTotal = 0
        val semaphore = Semaphore(PARALLEL)

        // As colecções são processadas sequencialmente. Os cursores de scan
        // avançam por tudo o que foi *visto* nesta execução, mesmo itens que
        // falharam — os cursores persistidos (preferences.last*Id) só avançam
        // pelo prefixo contíguo de sucessos: um ficheiro a falhar nunca faz o
        // scan devolver o mesmo lote em loop, e as falhas são retomadas na
        // execução seguinte.
        for (source in sources) {
            if (isStopped) break
            var scanId = source.cursor()
            val collectionResults = mutableListOf<ItemResult>()

            while (!isStopped) {
                val batch = scanner.scanNew(source.collection, scanId, BATCH_SIZE)
                if (batch.isEmpty()) break
                scanId = batch.maxOf { it.mediaStoreId }

                updateProgressNotification(manager.runProgress.value.done, totalPending)

                val results = coroutineScope {
                    batch.map { item ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val (taskId, job) = uploadManager.enqueueWithId(
                                    item.uri,
                                    parentId = null,
                                    mobileBackup = true,
                                    backupFolder = item.bucket ?: item.collection.defaultFolder,
                                )
                                job.join()
                                val task = uploadManager.tasks.value.firstOrNull { it.id == taskId }
                                val result = when (task?.state) {
                                    UploadTask.State.Completed -> ItemResult(item, success = true)
                                    UploadTask.State.Conflict -> {
                                        uploadManager.skipConflict(taskId)
                                        ItemResult(item, success = true, conflict = true)
                                    }
                                    else -> ItemResult(item, success = false, error = task?.errorMessage)
                                }
                                // Bump the visible counters as soon as each upload finishes.
                                // We intentionally do NOT advance the MediaStore cursor here:
                                // items run in parallel and can finish out of order, so
                                // moving the cursor per-item could skip a still-pending
                                // smaller id. Cursor advancement is done after awaitAll
                                // using the contiguous-prefix rule.
                                if (result.success && !result.conflict) {
                                    preferences.recordUploaded(item.collection, item.sizeBytes)
                                }
                                // Progresso item-a-item (sucesso, conflito ou falha):
                                // a UI e a notificação contam de forma monótona em
                                // vez de saltar por lote.
                                manager.recordProcessed()
                                updateProgressNotification(manager.runProgress.value.done, totalPending)
                                result
                            }
                        }
                    }.awaitAll()
                }

                // Advance the persisted cursor using the contiguous-prefix rule
                // over *all* results of this collection in this run: sorted by
                // id, the cursor moves forward while every smaller item also
                // succeeded. The moment we hit a failure, we stop — so the next
                // run picks up exactly where the gap is, never skipping a file.
                collectionResults += results
                source.setCursor(contiguousMaxId(collectionResults, sinceId = source.cursor()))

                val batchFailed = results.count { !it.success }
                uploadedTotal += results.count { it.success && !it.conflict }
                conflictTotal += results.count { it.conflict }
                failedTotal += batchFailed

                // Bound the in-memory task list — see pruneFinishedBackups.
                // keepLast generoso: a lista da UI tem scroll e remover linhas
                // concluídas demasiado cedo fazia-as desaparecer à frente do
                // utilizador (flicker).
                uploadManager.pruneFinishedBackups(keepLast = 30)

                updateProgressNotification(manager.runProgress.value.done, totalPending)

                // Whole batch failed — back off instead of hammering the API.
                if (batchFailed == batch.size) break
            }
        }

        preferences.lastBackupAt = System.currentTimeMillis()
        BackupNotifications.cancelProgress(applicationContext)

        return if (failedTotal > 0) {
            preferences.lastError = "$failedTotal ficheiro(s) falharam"
            BackupNotifications.showResult(
                applicationContext,
                title = "Backup terminou com erros",
                text = "$uploadedTotal enviados, $failedTotal falharam.",
            )
            Result.retry()
        } else {
            preferences.lastError = null
            if (uploadedTotal > 0) {
                BackupNotifications.showResult(
                    applicationContext,
                    title = "Backup completo",
                    text = "$uploadedTotal ficheiro(s) carregados em segurança.",
                )
            }
            Result.success()
        }
    }

    // Promove o worker a foreground service para sobreviver ao Doze, mas SÓ
    // quando o processo está visível: no Android 12+ iniciar um FGS com a app
    // em background lança ForegroundServiceStartNotAllowedException na main
    // thread do SystemForegroundService — fora de qualquer try/catch nosso —
    // e mata o processo. Era uma das fontes do "app vai abaixo" ao arrancar
    // (o WorkManager retoma o trabalho pendente mal o processo nasce, ainda
    // em background). Sem FGS o worker continua como job normal e o
    // WorkManager volta a tentar se for morto.
    private suspend fun maybeSetForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isProcessForeground()) {
            logger.log("AutoBackup", "Processo em background — a correr sem foreground service")
            return
        }
        try {
            setForeground(getForegroundInfo())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            logger.log("AutoBackup", "setForeground falhou: ${t.message}")
        }
    }

    private fun isProcessForeground(): Boolean = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(false)

    private fun updateProgressNotification(done: Int, total: Int) {
        if (total <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            val notification = BackupNotifications.buildProgress(
                context = applicationContext,
                title = "A fazer backup das fotos",
                text = "$done de $total carregados",
                indeterminate = false,
                progress = done,
                max = total,
            )
            NotificationManagerCompat.from(applicationContext)
                .notify(BackupNotifications.PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private class SourceSpec(
        val collection: BackupCollection,
        val cursor: () -> Long,
        val setCursor: (Long) -> Unit,
    )

    private fun enabledSources(): List<SourceSpec> = buildList {
        if (preferences.includeImages) {
            add(SourceSpec(BackupCollection.IMAGES, { preferences.lastImageId }, { preferences.lastImageId = it }))
        }
        if (preferences.includeVideos) {
            add(SourceSpec(BackupCollection.VIDEOS, { preferences.lastVideoId }, { preferences.lastVideoId = it }))
        }
        if (preferences.includeAudios) {
            add(SourceSpec(BackupCollection.AUDIOS, { preferences.lastAudioId }, { preferences.lastAudioId = it }))
        }
        if (preferences.includeDocuments) {
            add(SourceSpec(BackupCollection.DOCUMENTS, { preferences.lastDocumentId }, { preferences.lastDocumentId = it }))
        }
        if (preferences.includeDownloads) {
            add(SourceSpec(BackupCollection.DOWNLOADS, { preferences.lastDownloadId }, { preferences.lastDownloadId = it }))
        }
    }

    private fun contiguousMaxId(
        results: List<ItemResult>,
        sinceId: Long,
    ): Long {
        val sorted = results.sortedBy { it.item.mediaStoreId }
        var cursor = sinceId
        for (r in sorted) {
            if (!r.success) break
            cursor = maxOf(cursor, r.item.mediaStoreId)
        }
        return cursor
    }

    private inline fun safeCount(block: () -> Int): Int = try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (_: Throwable) {
        0
    }

    private fun hasMediaPermissions(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            buildList {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                // Áudio só é exigido se a fonte estiver activa — não bloquear
                // o backup de fotos por falta de uma permissão que não é usada.
                if (preferences.includeAudios) add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return perms.all { ContextCompat.checkSelfPermission(applicationContext, it) == PackageManager.PERMISSION_GRANTED }
    }

    private data class ItemResult(
        val item: BackupItem,
        val success: Boolean,
        val conflict: Boolean = false,
        val error: String? = null,
    )

    companion object {
        const val WORK_NAME = "auto_backup_periodic"
        const val WORK_ONESHOT = "auto_backup_oneshot"
        private const val BATCH_SIZE = 25
        private const val PARALLEL = 3
    }
}
