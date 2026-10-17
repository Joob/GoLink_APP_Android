package co.golink.tester.data.upload

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.data.encryption.EncryptedFileCodec
import co.golink.tester.data.encryption.Envelope
import co.golink.tester.network.FilesApi
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

data class UploadTask(
    val id: String,
    val name: String,
    val progress: Float,
    val state: State,
    val sizeBytes: Long = 0L,
    val errorMessage: String? = null,
    // Tags uploads queued by the auto-backup worker so the global UI banner
    // can exclude them — those uploads belong inside the Backups Automáticos
    // screen, not the browser's bottom bar.
    val mobileBackup: Boolean = false,
    // Sem rede neste momento: o envio está parado e retoma sozinho.
    val waitingForNetwork: Boolean = false,
    // Falhou por rede: volta a tentar sozinho quando a ligação regressar.
    val networkError: Boolean = false,
    // Falhou por a encriptação estar trancada: repete sozinho ao desbloquear.
    val e2eLocked: Boolean = false,
    // Vai cifrado (E2E): cadeado no anel do painel, como na web.
    val encrypted: Boolean = false,
) {
    enum class State { Queued, Uploading, Completed, Failed, Cancelled, Conflict }
}

private const val MAX_PARALLEL_USER_UPLOADS = 3

@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: FilesApi,
    private val e2eKeyManager: E2EKeyManager,
    private val e2eShareService: co.golink.tester.data.encryption.E2EShareService,
    private val connectivity: co.golink.tester.data.network.ConnectivityMonitor,
    private val sessionManager: co.golink.tester.data.auth.SessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Fila e progresso persistidos: a app fechada a meio retoma ao voltar.
    private val resumeStore = UploadResumeStore(context)
    @Volatile private var restoredFor: String? = null

    init {
        // Sessão: ao entrar, retoma os uploads deixados a meio por este
        // utilizador; ao sair, cancela tudo e apaga o que estava guardado.
        scope.launch {
            sessionManager.state.collect { s ->
                when (s) {
                    is co.golink.tester.data.auth.AuthState.Authenticated -> if (restoredFor != s.user.id) {
                        restoredFor = s.user.id
                        restore(s.user.id)
                    }
                    is co.golink.tester.data.auth.AuthState.Unauthenticated -> if (restoredFor != null) {
                        restoredFor = null
                        clearAll()
                        resumeStore.clear()
                    }
                    else -> Unit
                }
            }
        }
        // Encriptação desbloqueada: repete o que falhou por estar trancada.
        scope.launch {
            e2eKeyManager.unlocked.collect { unlocked ->
                if (unlocked) {
                    _tasks.value.filter { it.state == UploadTask.State.Failed && it.e2eLocked }
                        .forEach { retry(it.id) }
                }
            }
        }
        // A rede voltou: repete sozinho o que falhou por falta dela.
        scope.launch {
            connectivity.online.collect { online ->
                if (online) {
                    _tasks.value.filter { it.state == UploadTask.State.Failed && it.networkError }
                        .forEach { retry(it.id) }
                }
            }
        }
    }
    private val resolver: ContentResolver get() = context.contentResolver

    private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val tasks: StateFlow<List<UploadTask>> = _tasks.asStateFlow()

    private val _completed = MutableStateFlow<Long>(0)
    val completedTick: StateFlow<Long> = _completed.asStateFlow()

    private data class Source(
        val uri: Uri,
        val parentId: String?,
        val mobileBackup: Boolean = false,
        val backupFolder: String? = null,
        // Caminho real no telemóvel ("DCIM/Camera") — mostrado na web.
        val backupPath: String? = null,
        // Id do ficheiro que este upload substitui (conflito detetado no cliente).
        val replacesId: String? = null,
        // Pasta SAF de origem (upload de pasta): a permissão persistente é dela.
        val treeUri: Uri? = null,
    )
    private val sources = mutableMapOf<String, Source>()
    private val jobs = mutableMapOf<String, Job>()

    fun enqueue(uri: Uri, parentId: String?, existingFiles: Map<String, String> = emptyMap()): Job =
        enqueueWithId(uri, parentId, existingFiles = existingFiles).second

    fun enqueueWithId(
        uri: Uri,
        parentId: String?,
        mobileBackup: Boolean = false,
        backupFolder: String? = null,
        backupPath: String? = null,
        existingFiles: Map<String, String> = emptyMap(),
        treeUri: Uri? = null,
    ): Pair<String, Job> {
        val metadata = readMetadata(uri)

        // Nome cifrado: o servidor guarda o placeholder e NÃO consegue detetar o
        // duplicado (o 409 só existe no caminho em claro), por isso o "substituir?"
        // tinha deixado de aparecer. Quem tem o nome em claro é o cliente — os
        // nomes visíveis da pasta de destino chegam de quem inicia o upload.
        val replacesId = existingFiles[metadata.displayName]
        if (e2eKeyManager.canSealNames && replacesId != null) {
            val task = UploadTask(
                id = UUID.randomUUID().toString(),
                name = metadata.displayName,
                progress = 0f,
                state = UploadTask.State.Conflict,
                sizeBytes = metadata.size,
                mobileBackup = mobileBackup,
            )
            sources[task.id] = Source(uri, parentId, mobileBackup, backupFolder, backupPath, replacesId)
            update { list -> list + task }
            return task.id to scope.launch { }
        }
        val task = UploadTask(
            id = UUID.randomUUID().toString(),
            name = metadata.displayName,
            progress = 0f,
            state = UploadTask.State.Queued,
            sizeBytes = metadata.size,
            mobileBackup = mobileBackup,
        )
        sources[task.id] = Source(uri, parentId, mobileBackup, backupFolder, backupPath, treeUri = treeUri)
        update { list -> list + task }
        if (!mobileBackup) track(task.id, uri, metadata, parentId, treeUri = treeUri)
        return task.id to runTask(task.id, uri, metadata, parentId, mobileBackup = mobileBackup, backupFolder = backupFolder, backupPath = backupPath)
    }

    // ---- retoma após fechar a app ----------------------------------------------

    /** Regista o upload para sobreviver ao fecho da app (só uploads do utilizador). */
    private fun track(taskId: String, uri: Uri, metadata: FileMetadata, parentId: String?, overwrite: Boolean = false, replacesId: String? = null, treeUri: Uri? = null) {
        val userId = restoredFor ?: return
        if (uri.scheme == "file") return // temporários da própria app (ex. ficheiro de texto novo)
        // Sem permissão persistente o URI deixa de abrir quando o processo morre.
        runCatching {
            resolver.takePersistableUriPermission(treeUri ?: uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val existing = resumeStore.get(taskId)
        resumeStore.put(
            existing?.copy(overwrite = overwrite || existing.overwrite, replacesId = replacesId ?: existing.replacesId)
                ?: UploadResumeStore.Record(
                    taskId = taskId,
                    userId = userId,
                    uri = uri.toString(),
                    name = metadata.displayName,
                    size = metadata.size,
                    parentId = parentId,
                    treeUri = treeUri?.toString(),
                    overwrite = overwrite,
                    replacesId = replacesId,
                ),
        )
    }

    /** Esquece o registo de retoma e liberta a permissão persistente se já ninguém a usa. */
    private fun forgetTask(taskId: String) {
        val r = resumeStore.forget(taskId) ?: return
        val target = r.treeUri ?: r.uri
        val inUse = if (r.treeUri != null) resumeStore.treeInUse(r.treeUri) else resumeStore.uriInUse(r.uri)
        if (!inUse) {
            runCatching {
                resolver.releasePersistableUriPermission(Uri.parse(target), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    /** Volta a pôr na fila os uploads que este utilizador deixou a meio. */
    private fun restore(userId: String) {
        val pending = resumeStore.forUser(userId).filter { r -> _tasks.value.none { it.id == r.taskId } }
        for (r in pending) {
            val uri = Uri.parse(r.uri)
            val readable = runCatching { resolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
            if (!readable) {
                forgetTask(r.taskId)
                update { list ->
                    list + UploadTask(
                        id = r.taskId,
                        name = r.name,
                        progress = 0f,
                        state = UploadTask.State.Failed,
                        sizeBytes = r.size,
                        errorMessage = "Sem acesso ao ficheiro · escolhe-o outra vez para enviar".tr(),
                    )
                }
                continue
            }
            val metadata = readMetadata(uri)
            // O ficheiro mudou desde então: o progresso guardado já não serve.
            if (metadata.size != r.size) {
                r.e2e?.tempPath?.let { File(it).delete() }
                resumeStore.update(r.taskId) { it.copy(size = metadata.size, chunks = null, e2e = null) }
            }
            sources[r.taskId] = Source(uri, r.parentId, replacesId = r.replacesId, treeUri = r.treeUri?.let(Uri::parse))
            update { list ->
                list + UploadTask(
                    id = r.taskId,
                    name = metadata.displayName,
                    progress = 0f,
                    state = UploadTask.State.Queued,
                    sizeBytes = metadata.size,
                )
            }
            runTask(r.taskId, uri, metadata, r.parentId, overwrite = r.overwrite, replacesId = r.replacesId)
        }
    }

    /**
     * Cria um ficheiro de texto novo. Escreve o conteúdo num ficheiro temporário
     * e passa-o pelo caminho normal de upload — quota, cifragem E2E do conteúdo
     * e do nome, thumbnails e chaves de partilha já estão lá tratados.
     * O conteúdo inicial nunca é vazio: 0 bytes cai no caminho por chunks e não
     * havia nada para enviar.
     */
    fun createTextFile(name: String, content: String, parentId: String?): Pair<String, Job> {
        val temp = File.createTempFile("newtext_", ".txt", context.cacheDir)
        temp.writeText(content.ifEmpty { "\n" })

        val dot = name.lastIndexOf('.')
        val metadata = FileMetadata(
            displayName = name,
            baseName = if (dot > 0) name.substring(0, dot) else name,
            extension = if (dot in 0 until name.lastIndex) name.substring(dot + 1) else "txt",
            mimeType = "text/plain",
            size = temp.length(),
        )
        val uri = Uri.fromFile(temp)

        val task = UploadTask(
            id = UUID.randomUUID().toString(),
            name = name,
            progress = 0f,
            state = UploadTask.State.Queued,
            sizeBytes = metadata.size,
        )
        sources[task.id] = Source(uri, parentId)
        update { list -> list + task }

        val job = runTask(task.id, uri, metadata, parentId)
        job.invokeOnCompletion { temp.delete() }

        return task.id to job
    }

    private class ConflictException : RuntimeException("conflict")

    private fun runTask(taskId: String, uri: Uri, metadata: FileMetadata, parentId: String?, overwrite: Boolean = false, mobileBackup: Boolean = false, backupFolder: String? = null, backupPath: String? = null, replacesId: String? = null): Job {
        val job = scope.launch {
            // Uploads do utilizador: no máximo 3 ficheiros em simultâneo (antes
            // uma pasta de 100 ficheiros cifrava e enviava os 100 ao mesmo tempo).
            // O backup automático já tem o seu limite no worker.
            val permit = !mobileBackup
            if (permit) userUploadPermits.acquire()
            try {
                // Mobile backup: ficheiros até 25 MB vão no endpoint single-shot
                // (marca source=mobile_backup). Acima disso, um único pedido
                // rebentava o limite do nginx (HTTP 413 "Entity too large") — por
                // isso os grandes (vídeos) vão por chunks, que também marcam a
                // origem e criam a pasta.
                // E2E configurada mas trancada: NÃO enviar em claro. O backup
                // automático corre sem UI, por isso não há forma de pedir a
                // passphrase — falha e é retomado no próximo ciclo, já
                // desbloqueado. Enviar sem cifrar contornava silenciosamente o
                // E2E justamente nos ficheiros que o utilizador nunca escolheu
                // enviar à mão.
                // Com E2E configurada TUDO vai cifrado — incluindo o backup
                // automático (antes ia em claro). Para cifrar basta a chave
                // pública: desbloqueado, ou trancado com a pública fixada e
                // verificada (o worker chama verifyPinnedKey antes).
                val e2eRequired = e2eKeyManager.isConfiguredOrUnknown()
                if (e2eRequired) update { list -> list.map { if (it.id == taskId) it.copy(encrypted = true) else it } }
                if (e2eRequired && !e2eKeyManager.canEncrypt) {
                    error("e2e_locked")
                }
                retryOnNetwork(taskId) {
                if (mobileBackup && e2eRequired) {
                    if (metadata.size > CHUNK_THRESHOLD || metadata.size == 0L) {
                        uploadEncryptedChunked(taskId, uri, metadata, parentId = null, overwrite, mobileBackup = true, backupFolder = backupFolder, backupPath = backupPath)
                    } else {
                        uploadEncryptedSingle(taskId, uri, metadata, parentId = null, overwrite, mobileBackup = true, backupFolder = backupFolder, backupPath = backupPath)
                    }
                } else if (mobileBackup) {
                    // Backup automático nunca vai em claro: sem E2E configurada na
                    // conta não envia (o worker já pára antes; isto é a 2.ª barreira).
                    error("e2e_not_configured")
                } else if (e2eRequired) {
                    // E2E: cifra o ficheiro (streaming p/ temp) e envia ciphertext +
                    // a data key selada. Grandes (> limite) vão por chunks (senão o
                    // nginx rejeita com 413). Tamanho 0 = provider não expõe SIZE
                    // (Google Photos/Drive) → assumir grande e ir por chunks, como
                    // no caminho não-E2E.
                    if (metadata.size > CHUNK_THRESHOLD || metadata.size == 0L) {
                        uploadEncryptedChunked(taskId, uri, metadata, parentId, overwrite, replacesId)
                    } else {
                        uploadEncryptedSingle(taskId, uri, metadata, parentId, overwrite, replacesId)
                    }
                } else if (metadata.size in 1..CHUNK_THRESHOLD) {
                    uploadSingle(taskId, uri, metadata, parentId, overwrite)
                } else {
                    uploadChunked(taskId, uri, metadata, parentId, overwrite)
                }
                }
                update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Completed, progress = 1f, errorMessage = null, waitingForNetwork = false, networkError = false) else it } }
                forgetTask(taskId)
                _completed.value = System.currentTimeMillis()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (t is ConflictException) {
                    forgetTask(taskId)
                    if (overwrite) {
                        // O utilizador pediu "Substituir" e o servidor voltou a
                        // responder 409 — voltar a Conflict fazia o botão parecer
                        // morto. Mostrar a falha com causa.
                        update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Failed, errorMessage = "O servidor recusou substituir o ficheiro (409)") else it } }
                    } else {
                        update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Conflict, errorMessage = null) else it } }
                    }
                } else {
                    val isNetwork = t is java.io.IOException
                    val isLocked = t.message == "e2e_locked"
                    val message = when {
                        isLocked -> "Desbloqueia a encriptação para enviar este ficheiro.".tr()
                        isNetwork -> "Sem ligação · retoma quando a rede voltar".tr()
                        else -> t.message
                    }
                    // Rede / encriptação trancada: guarda o progresso para retomar.
                    // Outros erros são definitivos.
                    if (!isNetwork && !isLocked) forgetTask(taskId)
                    update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Failed, errorMessage = message, waitingForNetwork = false, networkError = isNetwork, e2eLocked = isLocked) else it } }
                }
            } finally {
                if (permit) userUploadPermits.release()
                jobs.remove(taskId)
            }
        }
        jobs[taskId] = job
        return job
    }

    private val userUploadPermits = kotlinx.coroutines.sync.Semaphore(MAX_PARALLEL_USER_UPLOADS)

    /** Nome e tamanho de cada ficheiro (limite de envio e espaço, antes de enviar). */
    fun sizes(uris: List<Uri>): List<Pair<String, Long>> = uris.map { uri ->
        runCatching { readMetadata(uri).let { it.displayName to it.size } }.getOrDefault("ficheiro" to 0L)
    }

    fun overwriteConflict(taskId: String) {
        val source = sources[taskId] ?: return
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (current.state != UploadTask.State.Conflict) return
        val metadata = readMetadata(source.uri).copy(displayName = current.name)
        update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Queued, progress = 0f, errorMessage = null) else it } }
        if (!source.mobileBackup) track(taskId, source.uri, metadata, source.parentId, overwrite = true, replacesId = source.replacesId, treeUri = source.treeUri)
        // mobileBackup tem de ser propagado: sem isto o reenvio ia para o
        // endpoint normal (raiz) e voltava a dar 409 — o botão "Substituir"
        // parecia não fazer nada.
        runTask(taskId, source.uri, metadata, source.parentId, overwrite = true, mobileBackup = source.mobileBackup, backupFolder = source.backupFolder, backupPath = source.backupPath, replacesId = source.replacesId)
    }

    fun skipConflict(taskId: String) {
        forgetTask(taskId)
        sources.remove(taskId)
        update { list -> list.filter { it.id != taskId } }
    }

    fun cancel(taskId: String) {
        jobs.remove(taskId)?.cancel()
        forgetTask(taskId)
        sources.remove(taskId)
        update { list -> list.filter { it.id != taskId } }
    }

    fun retry(taskId: String) {
        val source = sources[taskId] ?: return
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (current.state != UploadTask.State.Failed) return
        val metadata = readMetadata(source.uri).copy(displayName = current.name)
        update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Queued, progress = 0f, errorMessage = null, networkError = false, e2eLocked = false) else it } }
        if (!source.mobileBackup) track(taskId, source.uri, metadata, source.parentId, replacesId = source.replacesId, treeUri = source.treeUri)
        runTask(taskId, source.uri, metadata, source.parentId, mobileBackup = source.mobileBackup, backupFolder = source.backupFolder, backupPath = source.backupPath, replacesId = source.replacesId)
    }

    fun retryFailed() {
        _tasks.value.filter { it.state == UploadTask.State.Failed }.forEach { retry(it.id) }
    }

    fun clearFinished() {
        val keptIds = _tasks.value.filter { it.state == UploadTask.State.Queued || it.state == UploadTask.State.Uploading }.map { it.id }.toSet()
        // Falhados descartados pelo utilizador: deixam de ser retomados.
        _tasks.value.filter { it.id !in keptIds }.forEach { forgetTask(it.id) }
        sources.keys.retainAll(keptIds)
        update { list -> list.filter { it.id in keptIds } }
    }

    // O worker de backup chama isto após cada lote: um backup completo da
    // galeria pode ter milhares de itens e manter todas as linhas terminadas
    // em memória degrada a UI e acaba em OOM. Só toca em tarefas mobileBackup;
    // uploads do browser ficam intactos.
    fun pruneFinishedBackups(keepLast: Int = 0) {
        val finished = setOf(UploadTask.State.Completed, UploadTask.State.Cancelled)
        val done = _tasks.value.filter { it.mobileBackup && it.state in finished }
        if (done.size <= keepLast) return
        val removeIds = done.dropLast(keepLast).map { it.id }.toSet()
        sources.keys.removeAll(removeIds)
        update { list -> list.filterNot { it.id in removeIds } }
    }

    // Cancela e remove todos os uploads de backup, em curso ou não. Chamado ao
    // desactivar o backup automático: os jobs vivem num scope próprio e não
    // morrem com o worker.
    fun cancelBackups() {
        val ids = _tasks.value.filter { it.mobileBackup }.map { it.id }.toSet()
        ids.forEach { jobs.remove(it)?.cancel() }
        sources.keys.removeAll(ids)
        update { list -> list.filterNot { it.id in ids } }
    }

    fun clearAll() {
        _tasks.value.filterNot { it.mobileBackup }.forEach { forgetTask(it.id) }
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        sources.clear()
        update { emptyList() }
    }

    private suspend fun uploadSingle(
        taskId: String,
        uri: Uri,
        metadata: FileMetadata,
        parentId: String?,
        overwrite: Boolean,
    ) {
        markUploading(taskId)
        val fileBody = streamingRequestBody(uri, metadata.mimeType, metadata.size) { p ->
            updateProgress(taskId, p)
        }
        val filePart = MultipartBody.Part.createFormData("file", metadata.displayName, fileBody)
        val response = api.upload(
            name = textPart(metadata.baseName),
            extension = textPart(metadata.extension),
            parentId = parentId?.let { textPart(it) },
            overwriteExisting = if (overwrite) textPart("1") else null,
            file = filePart,
        )
        if (response.code() == 409) throw ConflictException()
        if (!response.isSuccessful) error(httpErrorMessage(response.code(), response.errorBody()?.string()))
        updateProgress(taskId, 1f)
    }

    private suspend fun uploadEncryptedSingle(
        taskId: String,
        uri: Uri,
        metadata: FileMetadata,
        parentId: String?,
        overwrite: Boolean,
        replacesId: String? = null,
        mobileBackup: Boolean = false,
        backupFolder: String? = null,
        backupPath: String? = null,
    ) {
        markUploading(taskId)
        val dataKey = Envelope.generateDataKey()
        val mediaType = mediaTypeFor(metadata.mimeType)

        // Team folder: o ficheiro pertence ao DONO (user_id = dono), por isso a
        // wrapped_data_key primária tem de ser selada à pública do dono; os membros
        // recebem-na depois via share-key. Sem team folder, dono = próprio.
        val memberKeys = parentId?.let { folderMemberKeys(it) }
        val ownerPublicKey = memberKeys?.owner?.public_key
        val recipients = memberKeys?.recipients.orEmpty().filter { !it.public_key.isNullOrBlank() }
        val wrapped = if (ownerPublicKey.isNullOrBlank()) {
            e2eKeyManager.sealForSelf(dataKey)
        } else {
            e2eKeyManager.sealForPublicKey(dataKey, ownerPublicKey)
        }

        // E2E Fase 2: cifra o nome só no espaço privado (ficheiro do próprio, sem
        // dono de team folder). Em team folders/partilhas mantém-se em claro.
        val nameEncrypted = if (ownerPublicKey.isNullOrBlank() && e2eKeyManager.canSealUploadNames)
            e2eKeyManager.sealName(metadata.baseName) else null

        // Thumbnail E2E (imagem/vídeo): gerado no cliente e cifrado com a MESMA
        // data key. Best-effort — se falhar, o ficheiro fica com ícone.
        val encryptedThumb: ByteArray? = try {
            generateThumbnailJpeg(uri, metadata.mimeType)?.let { jpeg ->
                EncryptedFileCodec.encrypt(jpeg, dataKey)
            }
        } catch (e: Throwable) {
            null
        }

        val temp = File.createTempFile("e2e_", ".enc", context.cacheDir)
        try {
            (resolver.openInputStream(uri) ?: error("não foi possível ler o ficheiro")).use { input ->
                temp.outputStream().use { out ->
                    EncryptedFileCodec.encryptStream(input, out, dataKey)
                }
            }
            val fileBody = temp.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", metadata.displayName, fileBody)
            val response = if (mobileBackup) api.uploadMobileBackup(
                name = textPart(if (nameEncrypted != null) "•" else metadata.baseName),
                extension = textPart(metadata.extension),
                overwriteExisting = if (overwrite) textPart("1") else null,
                folder = backupFolder?.takeIf { it.isNotBlank() }?.let { textPart(it) },
                file = filePart,
                devicePath = backupPath?.takeIf { it.isNotBlank() }?.let { textPart(it) },
                encrypted = textPart("1"),
                wrappedDataKey = textPart(wrapped),
                mediaType = textPart(mediaType),
                nameEncrypted = nameEncrypted?.let { textPart(it) },
            ) else api.upload(
                overwriteFileId = replacesId?.takeIf { overwrite }?.let { textPart(it) },
                name = textPart(if (nameEncrypted != null) "•" else metadata.baseName),
                extension = textPart(metadata.extension),
                parentId = parentId?.let { textPart(it) },
                overwriteExisting = if (overwrite) textPart("1") else null,
                file = filePart,
                encrypted = textPart("1"),
                wrappedDataKey = textPart(wrapped),
                mediaType = textPart(mediaType),
                nameEncrypted = nameEncrypted?.let { textPart(it) },
            )
            if (response.code() == 409) throw ConflictException()
            if (!response.isSuccessful) error(httpErrorMessage(response.code(), response.errorBody()?.string()))

            val fileId = response.body()?.data?.id

            // Partilhar a data key com os membros da team folder (share-key) para
            // eles decifrarem o ficheiro acabado de enviar. Best-effort por membro.
            if (fileId != null && recipients.isNotEmpty()) {
                for (r in recipients) {
                    try {
                        val sealed = e2eKeyManager.sealForPublicKey(dataKey, r.public_key!!)
                        api.shareFileKey(fileId, co.golink.tester.domain.encryption.ShareKeyBody(r.user_id, sealed))
                    } catch (e: Throwable) {
                        // sem acesso p/ este membro; segue
                    }
                }
            }

            // Enviar o thumbnail cifrado agora que o ficheiro existe (best-effort).
            if (encryptedThumb != null && fileId != null) {
                try {
                    api.uploadEncryptedThumbnail(
                        fileId,
                        encryptedThumb.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
                    )
                } catch (e: Throwable) {
                    // sem thumb; segue
                }
            }

            // Upload do DONO para uma pasta já partilhada: regista o nome nas
            // partilhas ancestrais (o visitante do link passa a ver o nome).
            if (fileId != null && nameEncrypted != null) {
                e2eShareService.registerItemNameForOwnerShares(fileId, metadata.baseName)
            }
            // ...e a data key: sem ela o nome aparecia no link mas o ficheiro não abria.
            if (fileId != null && ownerPublicKey.isNullOrBlank()) {
                e2eShareService.registerFileKeyForOwnerShares(fileId, dataKey)
            }

            updateProgress(taskId, 1f)
        } finally {
            temp.delete()
        }
    }

    // Parser para extrair o id do ficheiro criado na resposta do último chunk.
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    // Cache por pasta (uploads em lote vão para a mesma pasta) das chaves dos
    // destinatários E2E. null = pasta sem team folder / sem info.
    private val memberKeysCache = java.util.concurrent.ConcurrentHashMap<String, co.golink.tester.domain.encryption.FolderMemberKeysResponse>()

    private suspend fun folderMemberKeys(folderId: String): co.golink.tester.domain.encryption.FolderMemberKeysResponse? {
        memberKeysCache[folderId]?.let { return it }
        return try {
            // Só cachear SUCESSO: numa team folder, uma falha cacheada faria selar
            // ao próprio (fallback) → o dono não abriria o ficheiro. Falha
            // transitória → tenta de novo no próximo upload.
            api.folderMemberKeys(folderId).body()?.also { memberKeysCache[folderId] = it }
        } catch (e: Throwable) {
            null
        }
    }

    private fun mediaTypeFor(mime: String): String = when {
        mime.startsWith("image/") -> "image"
        mime.startsWith("video/") -> "video"
        mime.startsWith("audio/") -> "audio"
        else -> "file"
    }

    /** Gera um JPEG (~960px) de imagem ou vídeo. null se não aplicável/falhar. */
    private fun generateThumbnailJpeg(uri: Uri, mime: String, maxDim: Int = 960): ByteArray? {
        val bitmap: Bitmap = when {
            mime.startsWith("image/") -> decodeScaledImage(uri, maxDim)
            mime.startsWith("video/") -> decodeVideoFrame(uri)
            else -> null
        } ?: return null

        val scaled = scaleBitmap(bitmap, maxDim)
        return try {
            java.io.ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        } finally {
            if (scaled !== bitmap) bitmap.recycle()
        }
    }

    private fun decodeScaledImage(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxOrig = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxOrig <= 0) return null
        var sample = 1
        while (maxOrig / sample > maxDim * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun decodeVideoFrame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(3_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime()
        } catch (e: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun scaleBitmap(bmp: Bitmap, maxDim: Int): Bitmap {
        val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
        if (scale >= 1f) return bmp
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    /**
     * Upload E2E de ficheiros grandes (> limite): cifra para um temp (streaming) e
     * envia o ciphertext por CHUNKS com as flags E2E, à imagem da Web. No fim,
     * partilha a data key com os membros (share-key) e envia o thumbnail cifrado.
     */
    private suspend fun uploadEncryptedChunked(
        taskId: String,
        uri: Uri,
        metadata: FileMetadata,
        parentId: String?,
        overwrite: Boolean,
        replacesId: String? = null,
        mobileBackup: Boolean = false,
        backupFolder: String? = null,
        backupPath: String? = null,
    ) {
        markUploading(taskId)
        // Retoma (app fechada a meio): o ciphertext já está em disco e a data key
        // guardada no Keystore — continua dos chunks que o servidor já tem.
        val tracked = !mobileBackup && resumeStore.isTracked(taskId)
        val saved = resumeStore.get(taskId)?.e2e?.takeIf { tracked && File(it.tempPath).exists() }
        val savedKey = saved?.let { runCatching { resumeStore.unwrapKey(it.keyIv, it.keyCt) }.getOrNull() }
        val resumed = saved != null && savedKey != null

        val dataKey: ByteArray
        val mediaType: String
        val recipients: List<co.golink.tester.domain.encryption.E2EKeyEntry>
        val ownerSelf: Boolean
        val wrapped: String
        val nameEncrypted: String?
        val encryptedThumb: ByteArray?
        if (resumed) {
            dataKey = savedKey!!
            mediaType = saved!!.mediaType
            recipients = saved.recipients.map { co.golink.tester.domain.encryption.E2EKeyEntry(it.user_id, it.public_key) }
            ownerSelf = saved.ownerSelf
            wrapped = saved.wrapped
            nameEncrypted = saved.nameEncrypted
            encryptedThumb = saved.thumb?.let { resumeStore.unb64(it) }
        } else {
            dataKey = Envelope.generateDataKey()
            mediaType = mediaTypeFor(metadata.mimeType)

            val memberKeys = parentId?.let { folderMemberKeys(it) }
            val ownerPublicKey = memberKeys?.owner?.public_key
            ownerSelf = ownerPublicKey.isNullOrBlank()
            recipients = memberKeys?.recipients.orEmpty().filter { !it.public_key.isNullOrBlank() }
            wrapped = if (ownerSelf) {
                e2eKeyManager.sealForSelf(dataKey)
            } else {
                e2eKeyManager.sealForPublicKey(dataKey, ownerPublicKey!!)
            }

            // E2E Fase 2: nome cifrado só no espaço privado (ficheiro do próprio).
            nameEncrypted = if (ownerSelf && e2eKeyManager.canSealUploadNames)
                e2eKeyManager.sealName(metadata.baseName) else null

            encryptedThumb = try {
                generateThumbnailJpeg(uri, metadata.mimeType)?.let { EncryptedFileCodec.encrypt(it, dataKey) }
            } catch (e: Throwable) {
                null
            }
        }

        val temp = when {
            resumed -> File(saved!!.tempPath)
            tracked -> resumeStore.tempFileFor(taskId)
            else -> File.createTempFile("e2e_", ".enc", context.cacheDir)
        }
        try {
            // 1) cifra para o temp (memory-safe)
            if (!resumed) {
                (resolver.openInputStream(uri) ?: error("não foi possível ler o ficheiro")).use { input ->
                    temp.outputStream().use { out ->
                        EncryptedFileCodec.encryptStream(input, out, dataKey)
                    }
                }
                if (tracked) {
                    val (iv, ct) = resumeStore.wrapKey(dataKey)
                    resumeStore.update(taskId) {
                        it.copy(
                            chunks = null,
                            e2e = UploadResumeStore.E2EState(
                                tempPath = temp.absolutePath,
                                wrapped = wrapped,
                                nameEncrypted = nameEncrypted,
                                mediaType = mediaType,
                                keyIv = iv,
                                keyCt = ct,
                                thumb = encryptedThumb?.let { b -> resumeStore.b64(b) },
                                recipients = recipients.map { r -> UploadResumeStore.Recipient(r.user_id, r.public_key!!) },
                                ownerSelf = ownerSelf,
                            ),
                        )
                    }
                }
            }

            // 2) envia o ciphertext por chunks (tamanho exato do temp), vários
            //    em paralelo; o temp lê-se em qualquer posição.
            val total = temp.length()
            val chunkCount = maxOf(1, ((total + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt())
            val plan = chunkPlan(taskId, tracked, total, chunkCount) {
                "${UUID.randomUUID()}-${metadata.displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}"
            }
            val cipherName = plan.chunkName
            val lastBody: String? = java.io.FileInputStream(temp).channel.use { channel ->
                sendChunks(
                    taskId = taskId,
                    chunkCount = chunkCount,
                    totalBytes = total,
                    readChunk = { i -> readAt(channel, i.toLong() * CHUNK_SIZE, minOf(CHUNK_SIZE.toLong(), total - i.toLong() * CHUNK_SIZE).toInt()) },
                    alreadyUploaded = plan.uploaded.toSet(),
                    onChunkDone = { i -> if (tracked) resumeStore.markChunk(taskId, i) },
                ) { chunkIndex, chunkBytes, isLast ->
                    api.uploadChunk(
                        name = textPart(if (nameEncrypted != null) "•" else metadata.baseName),
                        extension = textPart(metadata.extension),
                        parentId = parentId?.let { textPart(it) },
                        isLastChunk = textPart(if (isLast) "1" else "0"),
                        chunkIndex = textPart(chunkIndex.toString()),
                        overwriteExisting = if (overwrite && isLast) textPart("1") else null,
                        overwriteFileId = replacesId?.takeIf { overwrite && isLast }?.let { textPart(it) },
                        mobileBackup = if (mobileBackup) textPart("1") else null,
                        folder = if (mobileBackup) backupFolder?.takeIf { it.isNotBlank() }?.let { textPart(it) } else null,
                        devicePath = if (mobileBackup) backupPath?.takeIf { it.isNotBlank() }?.let { textPart(it) } else null,
                        chunk = MultipartBody.Part.createFormData(
                            "chunk", cipherName,
                            chunkBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
                        ),
                        encrypted = textPart("1"),
                        wrappedDataKey = textPart(wrapped),
                        mediaType = textPart(mediaType),
                        nameEncrypted = nameEncrypted?.let { textPart(it) },
                    )
                }
            }

            // 3) id do ficheiro criado (resposta do último chunk)
            val fileId = lastBody?.let {
                runCatching {
                    json.decodeFromString<co.golink.tester.domain.browse.BrowseEntryEnvelope>(it).data.id
                }.getOrNull()
            }

            // 4) partilhar a data key com os membros da team folder (best-effort)
            if (fileId != null && recipients.isNotEmpty()) {
                for (r in recipients) {
                    try {
                        val sealed = e2eKeyManager.sealForPublicKey(dataKey, r.public_key!!)
                        api.shareFileKey(fileId, co.golink.tester.domain.encryption.ShareKeyBody(r.user_id, sealed))
                    } catch (e: Throwable) {
                    }
                }
            }

            // 5) thumbnail cifrado (best-effort)
            if (encryptedThumb != null && fileId != null) {
                try {
                    api.uploadEncryptedThumbnail(
                        fileId,
                        encryptedThumb.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
                    )
                } catch (e: Throwable) {
                }
            }

            // Upload do DONO para uma pasta já partilhada: regista o nome nas
            // partilhas ancestrais (o visitante do link passa a ver o nome).
            if (fileId != null && nameEncrypted != null) {
                e2eShareService.registerItemNameForOwnerShares(fileId, metadata.baseName)
            }
            // ...e a data key: sem ela o nome aparecia no link mas o ficheiro não abria.
            if (fileId != null && ownerSelf) {
                e2eShareService.registerFileKeyForOwnerShares(fileId, dataKey)
            }

            updateProgress(taskId, 1f)
        } finally {
            // Registado para retoma: o ciphertext fica até o upload sair da fila
            // (concluído/cancelado apaga-o via forgetTask).
            if (!tracked) temp.delete()
        }
    }

    private suspend fun uploadChunked(
        taskId: String,
        uri: Uri,
        metadata: FileMetadata,
        parentId: String?,
        overwrite: Boolean,
        mobileBackup: Boolean = false,
        backupFolder: String? = null,
        backupPath: String? = null,
    ) {
        markUploading(taskId)
        val safeDisplay = metadata.displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val chunkOriginalName = "${UUID.randomUUID()}-$safeDisplay"
        // Só a pasta de origem; o servidor monta o caminho por tipo no último
        // chunk. Enviada sempre — inofensivo nos chunks intermédios.
        val backupBucket = if (mobileBackup) backupFolder?.takeIf { it.isNotBlank() } else null
        val mobileFlag = if (mobileBackup) "1" else null

        // Fonte com acesso aleatório (a maioria: ficheiros locais, MediaStore):
        // chunks em paralelo e com retoma. Streams sem posição (alguns
        // fornecedores na nuvem) seguem pelo envio sequencial abaixo.
        val seekable = openSeekable(uri)
        if (seekable != null) {
            seekable.use { (pfd, channel) ->
                val total = channel.size()
                val chunkCount = maxOf(1, ((total + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt())
                val tracked = !mobileBackup && resumeStore.isTracked(taskId)
                val plan = chunkPlan(taskId, tracked, total, chunkCount) { chunkOriginalName }
                sendChunks(
                    taskId = taskId,
                    chunkCount = chunkCount,
                    totalBytes = total,
                    readChunk = { i -> readAt(channel, i.toLong() * CHUNK_SIZE, minOf(CHUNK_SIZE.toLong(), total - i.toLong() * CHUNK_SIZE).toInt()) },
                    alreadyUploaded = plan.uploaded.toSet(),
                    onChunkDone = { i -> if (tracked) resumeStore.markChunk(taskId, i) },
                ) { chunkIndex, chunkBytes, isLast ->
                    api.uploadChunk(
                        name = textPart(metadata.baseName),
                        extension = textPart(metadata.extension),
                        parentId = parentId?.let { textPart(it) },
                        isLastChunk = textPart(if (isLast) "1" else "0"),
                        chunkIndex = textPart(chunkIndex.toString()),
                        overwriteExisting = if (overwrite && isLast) textPart("1") else null,
                        mobileBackup = mobileFlag?.let { textPart(it) },
                        folder = backupBucket?.let { textPart(it) },
                        devicePath = if (mobileBackup) backupPath?.takeIf { it.isNotBlank() }?.let { textPart(it) } else null,
                        chunk = MultipartBody.Part.createFormData(
                            "chunk", plan.chunkName,
                            chunkBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
                        ),
                    )
                }
            }
            return
        }

        val total = metadata.size
        var sent = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            // Carry-over: when we peek 1 byte past the chunk to detect EOF, we
            // stash it here and prepend it to the next chunk. -1 means no peek.
            var carryOver = -1
            var plainChunkIndex = 0
            while (true) {
                val startOffset = if (carryOver >= 0) {
                    buffer[0] = carryOver.toByte()
                    1
                } else 0
                val filled = startOffset + readUpToN(input, buffer, CHUNK_SIZE - startOffset, startOffset)
                if (filled == 0) break

                // Determine last chunk by trying to read one more byte. EOF -> last.
                val peek = input.read()
                val reachedEof = peek == -1
                carryOver = peek
                // Prefer the size-based signal when available (more reliable for
                // providers that report size correctly); otherwise fall back to EOF.
                val isLast = reachedEof || (total > 0L && sent + filled >= total)

                val chunkBytes = if (filled == buffer.size) buffer.copyOf() else buffer.copyOf(filled)
                val chunkBody = chunkBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                val chunkPart = MultipartBody.Part.createFormData("chunk", chunkOriginalName, chunkBody)
                val response = api.uploadChunk(
                    name = textPart(metadata.baseName),
                    extension = textPart(metadata.extension),
                    parentId = parentId?.let { textPart(it) },
                    isLastChunk = textPart(if (isLast) "1" else "0"),
                    chunkIndex = textPart(plainChunkIndex.toString()),
                    overwriteExisting = if (overwrite && isLast) textPart("1") else null,
                    mobileBackup = mobileFlag?.let { textPart(it) },
                    folder = backupBucket?.let { textPart(it) },
                    chunk = chunkPart,
                )
                if (response.code() == 409) throw ConflictException()
                if (!response.isSuccessful) error(httpErrorMessage(response.code(), response.errorBody()?.string()))
                sent += filled
                plainChunkIndex++
                if (total > 0L) updateProgress(taskId, sent.toFloat() / total.toFloat())
                if (isLast) break
            }
        } ?: error("Não foi possível ler o ficheiro")
    }

    private class ChunkPlan(val chunkName: String, val uploaded: List<Int>)

    /**
     * Nome do .part e chunks já aceites. Retoma o plano guardado se for do mesmo
     * tamanho e recente (o servidor apaga as partes com 24 h); senão começa um
     * novo com [newName] e grava-o.
     */
    private fun chunkPlan(taskId: String, tracked: Boolean, total: Long, chunkCount: Int, newName: () -> String): ChunkPlan {
        if (!tracked) return ChunkPlan(newName(), emptyList())
        val rec = resumeStore.get(taskId)
        val saved = rec?.chunks?.takeIf {
            it.totalBytes == total && it.chunkCount == chunkCount &&
                System.currentTimeMillis() - rec.updatedAt < UploadResumeStore.CHUNKS_MAX_AGE
        }
        if (saved != null) return ChunkPlan(saved.chunkName, saved.uploaded)
        val name = newName()
        resumeStore.update(taskId) { it.copy(chunks = UploadResumeStore.ChunkState(name, chunkCount, total)) }
        return ChunkPlan(name, emptyList())
    }

    private class MissingChunksException(val indices: List<Int>) : RuntimeException("missing_chunks")

    // O envio por chunks já esgotou as suas próprias tentativas: não o
    // recomeçar do zero aqui — fica "falhou por rede" e repete quando a rede voltar.
    private class ChunkNetworkException(message: String) : java.io.IOException(message)

    /**
     * Envia os chunks 0..n-2 em paralelo (até [CHUNK_PARALLEL]) e o último só no
     * fim — é ele que faz o servidor juntar as partes, por isso tem de chegar
     * depois de todas. Cada chunk repete-se sozinho: sem rede espera que volte
     * (sem gastar tentativas) e continua do mesmo chunk; com 429/5xx faz
     * backoff. Se o servidor disser que falta alguma parte (422
     * missing_chunks), reenvia só essas e repete o último.
     *
     * @return corpo da resposta do último chunk (o ficheiro criado)
     */
    private suspend fun sendChunks(
        taskId: String,
        chunkCount: Int,
        totalBytes: Long,
        readChunk: (Int) -> ByteArray,
        alreadyUploaded: Set<Int> = emptySet(),
        onChunkDone: (Int) -> Unit = {},
        send: suspend (index: Int, bytes: ByteArray, isLast: Boolean) -> retrofit2.Response<okhttp3.ResponseBody>,
    ): String? = kotlinx.coroutines.coroutineScope {
        val uploaded = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        val lastIndex = chunkCount - 1
        // Retoma: os chunks que o servidor já aceitou não voltam a ser enviados
        // (se alguma parte tiver desaparecido, o 422 missing_chunks repõe-na).
        alreadyUploaded.filter { it in 0 until lastIndex }.forEach { i ->
            uploaded[i] = minOf(CHUNK_SIZE.toLong(), totalBytes - i.toLong() * CHUNK_SIZE)
        }
        if (uploaded.isNotEmpty() && totalBytes > 0L) updateProgress(taskId, uploaded.values.sum().toFloat() / totalBytes.toFloat())

        fun reportProgress() {
            if (totalBytes > 0L) updateProgress(taskId, uploaded.values.sum().toFloat() / totalBytes.toFloat())
        }

        suspend fun sendOne(i: Int, isLast: Boolean): String? {
            var attempt = 0
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().let { ctx ->
                    if (ctx[Job]?.isActive == false) throw kotlinx.coroutines.CancellationException()
                }
                val bytes = readChunk(i)
                val result = try {
                    send(i, bytes, isLast)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: java.io.IOException) {
                    null // rede: repete abaixo
                }
                if (result != null) {
                    if (result.code() == 409) throw ConflictException()
                    if (result.isSuccessful) {
                        val body = if (isLast) result.body()?.string() else { result.body()?.close(); null }
                        uploaded[i] = bytes.size.toLong()
                        if (!isLast) onChunkDone(i)
                        reportProgress()
                        return body
                    }
                    val errBody = result.errorBody()?.string()
                    if (isLast && result.code() == 422 && errBody?.contains("missing_chunks") == true) {
                        val missing = Regex("""\d+""").findAll(errBody.substringAfter("missing_chunks\":", ""))
                            .mapNotNull { it.value.toIntOrNull() }.toList()
                        throw MissingChunksException(missing)
                    }
                    val retryable = result.code() == 429 || result.code() >= 500
                    if (!retryable) error(httpErrorMessage(result.code(), errBody))
                    attempt++
                    if (attempt >= CHUNK_MAX_ATTEMPTS) error(httpErrorMessage(result.code(), errBody))
                    val retryAfter = result.headers()["Retry-After"]?.toLongOrNull()
                    connectivity.delayOrRetryNow(retryAfter?.times(1000) ?: backoffMs(attempt))
                    continue
                }
                if (!isOnline()) {
                    setWaiting(taskId, true)
                    try { waitForNetwork() } finally { setWaiting(taskId, false) }
                    continue
                }
                attempt++
                // Lança IOException: a tarefa fica "falhou por rede" e repete sozinha quando a ligação voltar.
                if (attempt >= CHUNK_MAX_ATTEMPTS) throw ChunkNetworkException("Falha de rede ao enviar o ficheiro".tr())
                setWaiting(taskId, true)
                try { connectivity.delayOrRetryNow(backoffMs(attempt)) } finally { setWaiting(taskId, false) }
            }
        }

        val permits = kotlinx.coroutines.sync.Semaphore(CHUNK_PARALLEL)
        suspend fun sendAll(indices: List<Int>) {
            indices.filter { it != lastIndex && !uploaded.containsKey(it) }
                .map { i -> async { permits.withPermit { sendOne(i, isLast = false) } } }
                .awaitAll()
        }

        sendAll((0 until lastIndex).toList())

        var rounds = 0
        while (true) {
            try {
                return@coroutineScope sendOne(lastIndex, isLast = true)
            } catch (e: MissingChunksException) {
                if (++rounds > 3 || e.indices.isEmpty()) error("O servidor não recebeu todas as partes do ficheiro".tr())
                e.indices.forEach { uploaded.remove(it) }
                reportProgress()
                sendAll(e.indices)
            }
        }
        null
    }

    private fun backoffMs(attempt: Int): Long = minOf(30_000L, 1000L shl (attempt - 1).coerceIn(0, 5))

    private fun isOnline(): Boolean = connectivity.isOnline()

    // Sem rede: espera até ela voltar (máx. 30 min) e o envio continua.
    private suspend fun waitForNetwork() {
        if (!connectivity.awaitOnline(30 * 60_000L)) throw java.io.IOException("Sem ligação à internet".tr())
        // A rede acabou de voltar: dá-lhe um instante para estabilizar.
        kotlinx.coroutines.delay(1000)
    }

    private fun setWaiting(taskId: String, waiting: Boolean) =
        update { list -> list.map { if (it.id == taskId) it.copy(waitingForNetwork = waiting) else it } }

    /**
     * Repete um envio de um só pedido quando a rede falha: sem rede espera que
     * ela volte; com rede (servidor inalcançável, timeout) tenta até 5 vezes
     * com backoff, mostrando a contagem no aviso de ligação.
     */
    private suspend fun <T> retryOnNetwork(taskId: String, block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                if (e is ChunkNetworkException) throw e
                if (!isOnline()) {
                    setWaiting(taskId, true)
                    try { waitForNetwork() } finally { setWaiting(taskId, false) }
                    continue
                }
                attempt++
                if (attempt >= 5) throw e
                setWaiting(taskId, true)
                try { connectivity.delayOrRetryNow(backoffMs(attempt)) } finally { setWaiting(taskId, false) }
            }
        }
    }

    /** Lê [len] bytes a partir de [position] (leitura posicional: segura em paralelo). */
    private fun readAt(channel: java.nio.channels.FileChannel, position: Long, len: Int): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(len)
        var pos = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, pos)
            if (n < 0) break
            pos += n
        }
        return if (buffer.position() == len) buffer.array() else buffer.array().copyOf(buffer.position())
    }

    private class SeekableSource(
        val pfd: android.os.ParcelFileDescriptor,
        val channel: java.nio.channels.FileChannel,
    ) : java.io.Closeable {
        operator fun component1() = pfd
        operator fun component2() = channel
        override fun close() {
            runCatching { channel.close() }
            runCatching { pfd.close() }
        }
    }

    // Fonte com acesso aleatório e tamanho conhecido, ou null (pipe/stream).
    private fun openSeekable(uri: Uri): SeekableSource? = runCatching {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return null
        val channel = java.io.FileInputStream(pfd.fileDescriptor).channel
        val ok = runCatching { channel.size() > 0L && channel.position(0L) != null }.getOrDefault(false)
        if (!ok) {
            runCatching { channel.close() }
            runCatching { pfd.close() }
            null
        } else {
            SeekableSource(pfd, channel)
        }
    }.getOrNull()

    private fun httpErrorMessage(code: Int, body: String?): String {
        val snippet = body?.take(180)?.replace(Regex("\\s+"), " ")?.trim()
        return if (snippet.isNullOrBlank()) "HTTP $code" else "HTTP $code: $snippet"
    }

    // Streams directly from the content URI without loading the whole file into memory.
    // onProgress recebe a fracção enviada (0..1) — só é chamado quando o
    // percentil inteiro muda, para não martelar o StateFlow a cada buffer.
    private fun streamingRequestBody(
        uri: Uri,
        mimeType: String,
        size: Long,
        onProgress: ((Float) -> Unit)? = null,
    ): RequestBody =
        object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun contentLength() = if (size > 0L) size else -1L
            // One-shot prevents OkHttp interceptors (logger, retries) from re-reading
            // the body — re-reading would re-open the content URI and double the I/O.
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.use { input ->
                    // 256 KB buffer keeps the syscall count low on modern devices and
                    // matches a typical TLS record / OkHttp segment size.
                    val buf = ByteArray(256 * 1024)
                    var n: Int
                    var sent = 0L
                    var lastPct = -1
                    while (input.read(buf).also { n = it } != -1) {
                        sink.write(buf, 0, n)
                        sent += n
                        if (onProgress != null && size > 0L) {
                            val pct = ((sent * 100) / size).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(sent.toFloat() / size)
                            }
                        }
                    }
                } ?: error("Não foi possível ler o ficheiro")
            }
        }

    // InputStream.readNBytes() only exists from API 33; this works on all supported versions.
    private fun readUpToN(input: java.io.InputStream, buffer: ByteArray, len: Int, off: Int = 0): Int {
        var total = 0
        while (total < len) {
            val n = input.read(buffer, off + total, len - total)
            if (n == -1) break
            total += n
        }
        return total
    }

    private fun markUploading(taskId: String) =
        update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Uploading) else it } }

    private fun updateProgress(taskId: String, value: Float) =
        update { list -> list.map { if (it.id == taskId) it.copy(progress = value.coerceIn(0f, 1f)) else it } }

    // Synchronized: com PARALLEL uploads + progresso por bytes há escritas
    // concorrentes — um read-modify-write sem lock perdia actualizações
    // (tarefas presas em estados antigos).
    @Synchronized
    private fun update(transform: (List<UploadTask>) -> List<UploadTask>) {
        _tasks.value = transform(_tasks.value)
    }

    private fun textPart(value: String): RequestBody =
        value.toRequestBody("text/plain".toMediaTypeOrNull())

    private fun readMetadata(uri: Uri): FileMetadata {
        var displayName = "ficheiro"
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0 && !c.isNull(nameIdx)) displayName = c.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        // Some providers (Google Photos, Drive) don't expose OpenableColumns.SIZE;
        // fall back to the file descriptor's statSize so the upload path picks chunked.
        if (size <= 0L) {
            size = runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { it.statSize.coerceAtLeast(0L) } ?: 0L
            }.getOrDefault(0L)
        }
        val dot = displayName.lastIndexOf('.')
        val baseName = if (dot > 0) displayName.substring(0, dot) else displayName
        val extension = if (dot in 0 until displayName.lastIndex) displayName.substring(dot + 1) else ""
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        return FileMetadata(displayName, baseName, extension, mimeType, size)
    }

    private data class FileMetadata(
        val displayName: String,
        val baseName: String,
        val extension: String,
        val mimeType: String,
        val size: Long,
    )

    companion object {
        private const val CHUNK_SIZE = 1024 * 1024 * 5
        private const val CHUNK_PARALLEL = 3
        private const val CHUNK_MAX_ATTEMPTS = 8
        private const val CHUNK_THRESHOLD = 1024L * 1024 * 25
    }
}
