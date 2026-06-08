package co.golink.tester.data.upload

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import co.golink.tester.network.FilesApi
import dagger.hilt.android.qualifiers.ApplicationContext
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
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

data class UploadTask(
    val id: String,
    val name: String,
    val progress: Float,
    val state: State,
    val sizeBytes: Long = 0L,
    val errorMessage: String? = null,
) {
    enum class State { Queued, Uploading, Completed, Failed, Cancelled, Conflict }
}

@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: FilesApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolver: ContentResolver get() = context.contentResolver

    private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val tasks: StateFlow<List<UploadTask>> = _tasks.asStateFlow()

    private val _completed = MutableStateFlow<Long>(0)
    val completedTick: StateFlow<Long> = _completed.asStateFlow()

    private data class Source(val uri: Uri, val parentId: String?)
    private val sources = mutableMapOf<String, Source>()
    private val jobs = mutableMapOf<String, Job>()

    fun enqueue(uri: Uri, parentId: String?): Job {
        val metadata = readMetadata(uri)
        val task = UploadTask(
            id = UUID.randomUUID().toString(),
            name = metadata.displayName,
            progress = 0f,
            state = UploadTask.State.Queued,
            sizeBytes = metadata.size,
        )
        sources[task.id] = Source(uri, parentId)
        update { list -> list + task }
        return runTask(task.id, uri, metadata, parentId)
    }

    private class ConflictException : RuntimeException("conflict")

    private fun runTask(taskId: String, uri: Uri, metadata: FileMetadata, parentId: String?, overwrite: Boolean = false): Job {
        val job = scope.launch {
            try {
                // Chunked path also covers unknown size (size <= 0): some content
                // providers (Google Photos, Drive) don't expose OpenableColumns.SIZE
                // and a single multipart request of an unknown-size body trips PHP's
                // upload_max_filesize / memory_limit on large videos.
                if (metadata.size in 1..CHUNK_THRESHOLD) {
                    uploadSingle(taskId, uri, metadata, parentId, overwrite)
                } else {
                    uploadChunked(taskId, uri, metadata, parentId, overwrite)
                }
                update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Completed, progress = 1f, errorMessage = null) else it } }
                _completed.value = System.currentTimeMillis()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (t is ConflictException) {
                    update { list -> list.map { if (it.id == taskId) it.copy(state = UploadTask.State.Conflict, errorMessage = null) else it } }
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
        runTask(taskId, source.uri, metadata, source.parentId, overwrite = true)
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
        runTask(taskId, source.uri, metadata, source.parentId)
    }

    fun retryFailed() {
        _tasks.value.filter { it.state == UploadTask.State.Failed }.forEach { retry(it.id) }
    }

    fun clearFinished() {
        val keptIds = _tasks.value.filter { it.state == UploadTask.State.Queued || it.state == UploadTask.State.Uploading }.map { it.id }.toSet()
        sources.keys.retainAll(keptIds)
        update { list -> list.filter { it.id in keptIds } }
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
        val fileBody = streamingRequestBody(uri, metadata.mimeType, metadata.size)
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

    private suspend fun uploadChunked(
        taskId: String,
        uri: Uri,
        metadata: FileMetadata,
        parentId: String?,
        overwrite: Boolean,
    ) {
        markUploading(taskId)
        val safeDisplay = metadata.displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val chunkOriginalName = "${UUID.randomUUID()}-$safeDisplay"
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
    private fun streamingRequestBody(uri: Uri, mimeType: String, size: Long): RequestBody =
        object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun contentLength() = if (size > 0L) size else -1L
            // One-shot prevents OkHttp interceptors (logger, retries) from re-reading
            // the body — re-reading would re-open the content URI and double the I/O.
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArray(8 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) sink.write(buf, 0, n)
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
