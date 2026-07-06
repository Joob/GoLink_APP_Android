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
        // Reagenda o próximo "tick" de ~1 min enquanto o backup estiver ligado
        // e não tiver sido cancelado (isStopped = utilizador desligou/Doze parou).
        if (preferences.enabled && !isStopped) manager.scheduleNextTick()
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
            safeCount { scanner.countNew(src.collection, src.cursor(), setOf(src.folder)) }
        }
        // Total de bytes por enviar nesta execução — denominador fixo do
        // "X MB de Y MB" na barra de progresso.
        val totalBytesPending = sources.sumOf { src ->
            safeLong { scanner.sumNewBytes(src.collection, src.cursor(), setOf(src.folder)) }
        }

        if (totalPending == 0) {
            preferences.lastError = null
            preferences.lastBackupAt = System.currentTimeMillis()
            BackupNotifications.cancelProgress(applicationContext)
            return Result.success()
        }

        logger.log("AutoBackup", "$totalPending ficheiro(s) por enviar")
        manager.startRun(totalPending, totalBytesPending)

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
            // Avanço do cursor por prefixo contíguo, calculado de forma
            // incremental: `minFailedId` é o menor id que falhou nesta execução
            // e `cursor` o maior id seguido de sucessos abaixo dele. Antes
            // acumulávamos TODOS os ItemResult e re-ordenávamos a lista inteira
            // a cada lote (O(n²) em memória e CPU) — pesado em galerias grandes.
            var minFailedId = Long.MAX_VALUE
            var cursor = source.cursor()

            while (!isStopped) {
                val batch = scanner.scanNew(source.collection, scanId, BATCH_SIZE, setOf(source.folder))
                if (batch.isEmpty()) break
                scanId = batch.maxOf { it.mediaStoreId }

                updateProgressNotification(manager.runProgress.value.done, totalPending, source.collection)

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
                                // vez de saltar por lote. Os bytes do ficheiro
                                // alimentam o "X MB de Y MB".
                                manager.recordProcessed(item.sizeBytes)
                                updateProgressNotification(manager.runProgress.value.done, totalPending, source.collection)
                                result
                            }
                        }
                    }.awaitAll()
                }

                // Avança o cursor persistido pela regra do prefixo contíguo, mas
                // de forma incremental: os lotes são lidos por id ascendente
                // (cada lote tem ids > os do lote anterior), por isso o menor id
                // falhado em qualquer ponto da execução tapa tudo o que vem
                // depois. O cursor fica no maior sucesso abaixo desse limite.
                val batchMinFailed = results.asSequence()
                    .filter { !it.success }
                    .minOfOrNull { it.item.mediaStoreId } ?: Long.MAX_VALUE
                if (batchMinFailed < minFailedId) minFailedId = batchMinFailed
                val batchContiguous = results.asSequence()
                    .filter { it.success && it.item.mediaStoreId < minFailedId }
                    .maxOfOrNull { it.item.mediaStoreId }
                if (batchContiguous != null && batchContiguous > cursor) cursor = batchContiguous
                source.setCursor(cursor)

                val batchFailed = results.count { !it.success }
                uploadedTotal += results.count { it.success && !it.conflict }
                conflictTotal += results.count { it.conflict }
                failedTotal += batchFailed

                // Bound the in-memory task list — see pruneFinishedBackups.
                // keepLast generoso: a lista da UI tem scroll e remover linhas
                // concluídas demasiado cedo fazia-as desaparecer à frente do
                // utilizador (flicker).
                uploadManager.pruneFinishedBackups(keepLast = 30)

                updateProgressNotification(manager.runProgress.value.done, totalPending, source.collection)

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

    private fun collectionLabel(collection: BackupCollection): String = when (collection) {
        BackupCollection.IMAGES -> "imagens"
        BackupCollection.VIDEOS -> "vídeos"
        BackupCollection.AUDIOS -> "áudios"
        BackupCollection.DOCUMENTS -> "documentos"
        BackupCollection.DOWNLOADS -> "downloads"
    }

    // Throttle: um backup grande processa milhares de itens; reemitir a
    // notificação a cada item é desperdício (e o Android limita notify() muito
    // frequente). Actualizamos no máximo ~2x por segundo, e sempre no fim.
    @Volatile private var lastNotifyMs = 0L

    private fun updateProgressNotification(done: Int, total: Int, collection: BackupCollection) {
        if (total <= 0) return
        val now = System.currentTimeMillis()
        if (done < total && now - lastNotifyMs < 500L) return
        lastNotifyMs = now
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            val notification = BackupNotifications.buildProgress(
                context = applicationContext,
                // Título reflecte o que está mesmo a ser enviado — antes dizia
                // sempre "das fotos" mesmo a enviar vídeos/áudios.
                title = "A fazer backup de ${collectionLabel(collection)}",
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
        // Uma fonte = uma pasta escolhida. O scan filtra por esta pasta e o
        // cursor é próprio dela, para pastas adicionadas mais tarde serem
        // enviadas por inteiro (e não a partir do cursor da colecção).
        val folder: String,
        val cursor: () -> Long,
        val setCursor: (Long) -> Unit,
    )

    // Expande cada colecção ligada nas suas pastas seleccionadas. Opt-in: sem
    // pastas, a colecção não gera fontes.
    private fun enabledSources(): List<SourceSpec> = buildList {
        fun addCollection(enabled: Boolean, collection: BackupCollection) {
            if (!enabled) return
            preferences.selectedFolders(collection).sorted().forEach { folder ->
                add(
                    SourceSpec(
                        collection = collection,
                        folder = folder,
                        cursor = { preferences.folderCursor(collection, folder) },
                        setCursor = { preferences.setFolderCursor(collection, folder, it) },
                    ),
                )
            }
        }
        addCollection(preferences.includeImages, BackupCollection.IMAGES)
        addCollection(preferences.includeVideos, BackupCollection.VIDEOS)
        addCollection(preferences.includeAudios, BackupCollection.AUDIOS)
        addCollection(preferences.includeDocuments, BackupCollection.DOCUMENTS)
        addCollection(preferences.includeDownloads, BackupCollection.DOWNLOADS)
    }

    private inline fun safeCount(block: () -> Int): Int = try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (_: Throwable) {
        0
    }

    private inline fun safeLong(block: () -> Long): Long = try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (_: Throwable) {
        0L
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
