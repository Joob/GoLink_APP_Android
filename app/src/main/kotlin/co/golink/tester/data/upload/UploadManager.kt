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
) {
    enum class State { Queued, Uploading, Completed, Failed, Cancelled, Conflict }
}

@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: FilesApi,
    private val e2eKeyManager: E2EKeyManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    )
    private val sources = mutableMapOf<String, Source>()
    private val jobs = mutableMapOf<String, Job>()

    fun enqueue(uri: Uri, parentId: String?): Job = enqueueWithId(uri, parentId).second

    fun enqueueWithId(
        uri: Uri,
        parentId: String?,
        mobileBackup: Boolean = false,
        backupFolder: String? = null,
    ): Pair<String, Job> {
        val metadata = readMetadata(uri)
        val task = UploadTask(
            id = UUID.randomUUID().toString(),
            name = metadata.displayName,
            progress = 0f,
            state = UploadTask.State.Queued,
            sizeBytes = metadata.size,
            mobileBackup = mobileBackup,
        )
        sources[task.id] = Source(uri, parentId, mobileBackup, backupFolder)
        update { list -> list + task }
        return task.id to runTask(task.id, uri, metadata, parentId, mobileBackup = mobileBackup, backupFolder = backupFolder)
    }

    private class ConflictException : RuntimeException("conflict")

    private fun runTask(taskId: String, uri: Uri, metadata: FileMetadata, parentId: String?, overwrite: Boolean = false, mobileBackup: Boolean = false, backupFolder: String? = null): Job {
        val job = scope.launch {
            try {
                // Mobile backup: ficheiros até 25 MB vão no endpoint single-shot
                // (marca source=mobile_backup). Acima disso, um único pedido
                // rebentava o limite do nginx (HTTP 413 "Entity too large") — por
                // isso os grandes (vídeos) vão por chunks, que também marcam a
                // origem e criam a pasta.
                if (mobileBackup) {
                    if (metadata.size > CHUNK_THRESHOLD) {
                        uploadChunked(taskId, uri, metadata, parentId = null, overwrite, mobileBackup = true, backupFolder = backupFolder)
                    } else {
                        uploadMobileBackupSingle(taskId, uri, metadata, overwrite, backupFolder)
                    }
                } else if (e2eKeyManager.shouldEncrypt) {
                    // E2E: cifra o ficheiro (streaming p/ temp) e envia ciphertext +
                    // a data key selada. Grandes (> limite) vão por chunks (senão o
                    // nginx rejeita com 413). Tamanho 0 = provider não expõe SIZE
                    // (Google Photos/Drive) → assumir grande e ir por chunks, como
                    // no caminho não-E2E.
                    if (metadata.size > CHUNK_THRESHOLD || metadata.size == 0L) {
                        uploadEncryptedChunked(taskId, uri, metadata, parentId, overwrite)
                    } else {
                        uploadEncryptedSingle(taskId, uri, metadata, parentId, overwrite)
                    }
                } else if (metadata.size in 1..CHUNK_THRESHOLD) {
                    uploadSingle(taskId, uri, metadata, parentId, overwrite)
                } else {
                    uploadChunked(taskId, uri, metadata, parentId, overwrite)
                }
                update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Completed, progress = 1f, errorMessage = null) else it } }
                _completed.value = System.currentTimeMillis()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (t is ConflictException) {
                    if (overwrite) {
                        // O utilizador pediu "Substituir" e o servidor voltou a
                        // responder 409 — voltar a Conflict fazia o botão parecer
                        // morto. Mostrar a falha com causa.
                        update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Failed, errorMessage = "O servidor recusou substituir o ficheiro (409)") else it } }
                    } else {
                        update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Conflict, errorMessage = null) else it } }
                    }
                } else {
                    update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Failed, errorMessage = t.message) else it } }
                }
            } finally {
                jobs.remove(taskId)
            }
        }
        jobs[taskId] = job
        return job
    }

    fun overwriteConflict(taskId: String) {
        val source = sources[taskId] ?: return
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (current.state != UploadTask.State.Conflict) return
        val metadata = readMetadata(source.uri).copy(displayName = current.name)
        update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Queued, progress = 0f, errorMessage = null) else it } }
        // mobileBackup tem de ser propagado: sem isto o reenvio ia para o
        // endpoint normal (raiz) e voltava a dar 409 — o botão "Substituir"
        // parecia não fazer nada.
        runTask(taskId, source.uri, metadata, source.parentId, overwrite = true, mobileBackup = source.mobileBackup, backupFolder = source.backupFolder)
    }

    fun skipConflict(taskId: String) {
        sources.remove(taskId)
        update { list -> list.filter { it.id != taskId } }
    }

    fun cancel(taskId: String) {
        jobs.remove(taskId)?.cancel()
        sources.remove(taskId)
        update { list -> list.filter { it.id != taskId } }
    }

    fun retry(taskId: String) {
        val source = sources[taskId] ?: return
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (current.state != UploadTask.State.Failed) return
        val metadata = readMetadata(source.uri).copy(displayName = current.name)
        update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Queued, progress = 0f, errorMessage = null) else it } }
        runTask(taskId, source.uri, metadata, source.parentId, mobileBackup = source.mobileBackup, backupFolder = source.backupFolder)
    }

    fun retryFailed() {
        _tasks.value.filter { it.state == UploadTask.State.Failed }.forEach { retry(it.id) }
    }

    fun clearFinished() {
        val keptIds = _tasks.value.filter { it.state == UploadTask.State.Queued || it.state == UploadTask.State.Uploading }.map { it.id }.toSet()
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
            val response = api.upload(
                name = textPart(metadata.baseName),
                extension = textPart(metadata.extension),
                parentId = parentId?.let { textPart(it) },
                overwriteExisting = if (overwrite) textPart("1") else null,
                file = filePart,
                encrypted = textPart("1"),
                wrappedDataKey = textPart(wrapped),
                mediaType = textPart(mediaType),
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
    ) {
        markUploading(taskId)
        val dataKey = Envelope.generateDataKey()
        val mediaType = mediaTypeFor(metadata.mimeType)

        val memberKeys = parentId?.let { folderMemberKeys(it) }
        val ownerPublicKey = memberKeys?.owner?.public_key
        val recipients = memberKeys?.recipients.orEmpty().filter { !it.public_key.isNullOrBlank() }
        val wrapped = if (ownerPublicKey.isNullOrBlank()) {
            e2eKeyManager.sealForSelf(dataKey)
        } else {
            e2eKeyManager.sealForPublicKey(dataKey, ownerPublicKey)
        }

        val encryptedThumb: ByteArray? = try {
            generateThumbnailJpeg(uri, metadata.mimeType)?.let { EncryptedFileCodec.encrypt(it, dataKey) }
        } catch (e: Throwable) {
            null
        }

        val temp = File.createTempFile("e2e_", ".enc", context.cacheDir)
        try {
            // 1) cifra para o temp (memory-safe)
            (resolver.openInputStream(uri) ?: error("não foi possível ler o ficheiro")).use { input ->
                temp.outputStream().use { out ->
                    EncryptedFileCodec.encryptStream(input, out, dataKey)
                }
            }

            // 2) envia o ciphertext por chunks (tamanho exato do temp)
            val cipherName = "${UUID.randomUUID()}-${metadata.displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}"
            val total = temp.length()
            var sent = 0L
            var lastBody: String? = null

            temp.inputStream().use { fin ->
                val buffer = ByteArray(CHUNK_SIZE)
                while (sent < total) {
                    val toRead = minOf(CHUNK_SIZE.toLong(), total - sent).toInt()
                    val filled = readUpToN(fin, buffer, toRead, 0)
                    if (filled <= 0) break
                    val isLast = sent + filled >= total
                    val chunkBytes = if (filled == buffer.size) buffer.copyOf() else buffer.copyOf(filled)
                    val chunkPart = MultipartBody.Part.createFormData(
                        "chunk", cipherName,
                        chunkBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
                    )
                    val response = api.uploadChunk(
                        name = textPart(metadata.baseName),
                        extension = textPart(metadata.extension),
                        parentId = parentId?.let { textPart(it) },
                        isLastChunk = textPart(if (isLast) "1" else "0"),
                        overwriteExisting = if (overwrite && isLast) textPart("1") else null,
                        chunk = chunkPart,
                        encrypted = textPart("1"),
                        wrappedDataKey = textPart(wrapped),
                        mediaType = textPart(mediaType),
                    )
                    if (response.code() == 409) throw ConflictException()
                    if (!response.isSuccessful) error(httpErrorMessage(response.code(), response.errorBody()?.string()))
                    if (isLast) lastBody = response.body()?.string()
                    sent += filled
                    if (total > 0L) updateProgress(taskId, sent.toFloat() / total.toFloat())
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

            updateProgress(taskId, 1f)
        } finally {
            temp.delete()
        }
    }

    private suspend fun uploadMobileBackupSingle(
        taskId: String,
        uri: Uri,
        metadata: FileMetadata,
        overwrite: Boolean,
        backupFolder: String?,
    ) {
        markUploading(taskId)
        val fileBody = streamingRequestBody(uri, metadata.mimeType, metadata.size) { p ->
            updateProgress(taskId, p)
        }
        val filePart = MultipartBody.Part.createFormData("file", metadata.displayName, fileBody)
        // Envia só a pasta de origem; o servidor monta o caminho por tipo
        // (/Imagens/Camera, /Vídeos/Camera…) e cria/reutiliza as pastas.
        val response = api.uploadMobileBackup(
            name = textPart(metadata.baseName),
            extension = textPart(metadata.extension),
            overwriteExisting = if (overwrite) textPart("1") else null,
            folder = backupFolder?.takeIf { it.isNotBlank() }?.let { textPart(it) },
            file = filePart,
        )
        if (response.code() == 409) throw ConflictException()
        if (!response.isSuccessful) error(httpErrorMessage(response.code(), response.errorBody()?.string()))
        updateProgress(taskId, 1f)
    }

    private suspend fun uploadChunked(
        taskId: String,
        uri: Uri,
        metadata: FileMetadata,
        parentId: String?,
        overwrite: Boolean,
        mobileBackup: Boolean = false,
        backupFolder: String? = null,
    ) {
        markUploading(taskId)
        val safeDisplay = metadata.displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val chunkOriginalName = "${UUID.randomUUID()}-$safeDisplay"
        // Só a pasta de origem; o servidor monta o caminho por tipo no último
        // chunk. Enviada sempre — inofensivo nos chunks intermédios.
        val backupBucket = if (mobileBackup) backupFolder?.takeIf { it.isNotBlank() } else null
        val mobileFlag = if (mobileBackup) "1" else null
        val total = metadata.size
        var sent = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            // Carry-over: when we peek 1 byte past the chunk to detect EOF, we
            // stash it here and prepend it to the next chunk. -1 means no peek.
            var carryOver = -1
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
                    overwriteExisting = if (overwrite && isLast) textPart("1") else null,
                    mobileBackup = mobileFlag?.let { textPart(it) },
                    folder = backupBucket?.let { textPart(it) },
                    chunk = chunkPart,
                )
                if (response.code() == 409) throw ConflictException()
                if (!response.isSuccessful) error(httpErrorMessage(response.code(), response.errorBody()?.string()))
                sent += filled
                if (total > 0L) updateProgress(taskId, sent.toFloat() / total.toFloat())
                if (isLast) break
            }
        } ?: error("Não foi possível ler o ficheiro")
    }

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
        private const val CHUNK_THRESHOLD = 1024L * 1024 * 25
    }
}
