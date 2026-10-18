package co.golink.tester.data.download

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import co.golink.tester.R
import co.golink.tester.data.auth.TokenStore
import co.golink.tester.data.config.BackendUrlHolder
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.download.ZipManifestEntry
import co.golink.tester.domain.download.ZipManifestResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class FileDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backendUrlHolder: BackendUrlHolder,
    private val tokenStore: TokenStore,
    private val e2eKeyManager: co.golink.tester.data.encryption.E2EKeyManager,
    private val userEncryptionApi: co.golink.tester.network.UserEncryptionApi,
    // Cliente de downloads: sem limite de duração total (o callTimeout de 10 min
    // do "longRunning" cortava qualquer ficheiro de vários GB) e com retoma por
    // Range quando a ligação cai (ResumableHttpInputStream).
    @Named("download") private val httpClient: OkHttpClient,
) {
    sealed interface Event {
        data class Started(val name: String) : Event
        data class Failed(val name: String, val message: String) : Event
        data class Completed(val name: String) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = _events

    private val pending = mutableMapOf<Long, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notifId = AtomicInteger(2000)

    private val zipJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    companion object {
        // Canal único (LOW: progresso sem som). O antigo "golink_downloads" era
        // criado com importâncias diferentes conforme quem chegava primeiro.
        const val CHANNEL_ID = "golink_transfers"
        private const val LEGACY_CHANNEL_ID = "golink_downloads"
        // 64 KB por leitura — menos syscalls, mais throughput em ficheiros grandes.
        private const val DEFAULT_COPY_BUFFER = 64 * 1024
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id < 0) return
            val name = synchronized(pending) { pending.remove(id) } ?: return
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(id)
            manager.query(query)?.use { c ->
                if (!c.moveToFirst()) { _events.tryEmit(Event.Failed(name, "cancelado")); return }
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> _events.tryEmit(Event.Completed(name))
                    DownloadManager.STATUS_FAILED -> _events.tryEmit(Event.Failed(name, describeReason(reason)))
                    else -> Unit
                }
            }
        }
    }

    init {
        ensureChannel(context)
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    /**
     * Download de um ficheiro. Corre no foreground service ([ZipDownloadService]):
     * num scope normal o SO matava o processo ao sair da app e o download morria
     * a meio. Lá tem notificação com a % e retoma sozinho se a rede cair.
     */
    fun downloadFile(id: String, name: String, basename: String? = null, encrypted: Boolean = false, mimetype: String? = null): Result<Unit> = runCatching {
        if (encrypted && !e2eKeyManager.isUnlocked) error("Desbloqueia a encriptação para descarregar")
        val label = sanitize(name)
        _events.tryEmit(Event.Started(label))
        ZipDownloadService.startFile(context, id, label, basename, encrypted, mimetype)
    }

    /** Trabalho do download de UM ficheiro, chamado pelo serviço já em foreground. */
    suspend fun runFileDownload(
        id: String,
        name: String,
        basename: String?,
        encrypted: Boolean,
        mimetype: String?,
        onProgress: (loaded: Long, total: Long) -> Unit,
    ): Outcome {
        val backend = backendUrlHolder.current.trimEnd('/')
        val url = "$backend/api/file/${Uri.encode(id)}/download"
        return try {
            // E2E: abrir a data key selada (precisa da privada desbloqueada).
            var dataKey: ByteArray? = null
            if (encrypted) {
                if (!e2eKeyManager.isUnlocked) error("Desbloqueia a encriptação para descarregar")
                val wrapped = runCatching { userEncryptionApi.fileKey(id).body()?.wrapped_data_key }.getOrNull()
                    ?: error("Sem chave de encriptação")
                dataKey = e2eKeyManager.openFileDataKey(wrapped)
            }
            val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(300)?.trim()
                response.close()
                error(if (errBody.isNullOrBlank()) "HTTP ${response.code}" else "HTTP ${response.code}: $errBody")
            }
            val body = ResumableHttpInputStream(httpClient, url, response, onBytes = { loaded ->
                onProgress(loaded, response.body?.contentLength() ?: -1L)
            })
            body.use { input ->
                // O nome do próprio item (já decifrado na app) manda: com nomes E2E
                // o servidor só conhece o nome cifrado ("•"), e nomes com acentos
                // chegavam estragados no header. O Content-Disposition fica como
                // último recurso (item sem nome).
                val serverName = parseContentDispositionFilename(response.header("Content-Disposition"))
                val resolvedName = when {
                    name.isNotBlank() && name != "•" -> {
                        val ext = basename?.substringAfterLast('.', "").orEmpty()
                        if (!name.contains('.') && ext.isNotBlank() && ext.length <= 5) "$name.$ext" else name
                    }
                    serverName != null -> serverName
                    else -> "download"
                }
                // Garantir SEMPRE a extensão correta a partir do mimetype (que
                // guarda a extensão, ex.: "MP4") — o nome/Content-Disposition
                // podia vir sem extensão e o ficheiro saía como .txt/.bin.
                val named = ensureExtension(resolvedName, mimetype ?: basename?.substringAfterLast('.', ""))
                val finalName = uniqueDestination(sanitize(named))
                writeToDownloads(finalName, mimeTypeFor(finalName), input, dataKey)
                _events.tryEmit(Event.Completed(finalName))
                Outcome.Completed(finalName)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.tryEmit(Event.Failed(sanitize(name), e.message ?: "Erro desconhecido"))
            Outcome.Failed(e.message ?: "Erro desconhecido")
        }
    }

    /**
     * Anexo de ticket de suporte (GET autenticado, sem E2E) → pasta Downloads.
     * [path] é o download_url relativo devolvido pela API.
     */
    fun downloadSupportAttachment(path: String, name: String): Result<Unit> = runCatching {
        val url = backendUrlHolder.current.trimEnd('/') + "/" + path.trimStart('/')
        val label = sanitize(name)
        val nid = notifId.getAndIncrement()
        _events.tryEmit(Event.Started(label))
        notify(nid, label, inProgress = true)
        scope.launch {
            try {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        notifManager.cancel(nid)
                        _events.tryEmit(Event.Failed(label, "HTTP ${response.code}"))
                        return@launch
                    }
                    val finalName = uniqueDestination(sanitize(name))
                    writeToDownloads(finalName, mimeTypeFor(finalName), body.byteStream())
                    notifManager.cancel(nid)
                    notify(nid, finalName, inProgress = false, success = true)
                    _events.tryEmit(Event.Completed(finalName))
                }
            } catch (e: Exception) {
                notifManager.cancel(nid)
                _events.tryEmit(Event.Failed(label, e.message ?: "Erro desconhecido"))
            }
        }
    }

    fun downloadFile(file: BrowseItem.File): Result<Unit> =
        downloadFile(file.id, file.name, file.basename, file.encrypted, file.mimetype)

    // Acrescenta a extensão do mimetype se o nome ainda não a tiver (case-insensitive).
    private fun ensureExtension(name: String, ext: String?): String {
        val clean = ext?.trim()?.removePrefix(".")?.lowercase()
            ?.takeIf { it.isNotBlank() && it.length <= 6 && it.all { c -> c.isLetterOrDigit() } }
            ?: return name
        return if (name.endsWith(".$clean", ignoreCase = true)) name else "$name.$clean"
    }

    fun downloadFolder(folder: BrowseItem.Folder): Result<Unit> =
        downloadZip(listOf(folder), suggestedName = "${folder.name}.zip")

    // Resultado para o foreground service decidir a notificação final.
    sealed interface Outcome {
        // `failed`: ficheiros que ficaram fora do zip (não passam em silêncio).
        data class Completed(val name: String, val failed: List<String> = emptyList()) : Outcome
        data class Failed(val message: String) : Outcome
        // Sem ficheiros cifrados: delegado ao DownloadManager (tem a sua notif).
        object ServerZipEnqueued : Outcome
    }

    /**
     * Zip de pastas / vários itens. E2E: o servidor só tem ciphertext e não pode
     * zipar — buscamos o manifesto (lista plana + caminhos) e, se houver QUALQUER
     * ficheiro cifrado, decifra-se cada um e monta-se o zip no cliente. Caso
     * contrário usa o zip do servidor (DownloadManager), como antes.
     *
     * Corre num FOREGROUND SERVICE ([ZipDownloadService]): pastas grandes demoram
     * e, num scope normal, o SO matava o processo se o utilizador saísse da app →
     * o zip morria a meio e a entrada MediaStore ficava presa (invisível). O
     * foreground service mantém o processo vivo e a notificação persistente.
     */
    fun downloadZip(items: List<BrowseItem>, suggestedName: String): Result<Unit> = runCatching {
        if (items.isEmpty()) error("Sem itens para descarregar")
        val token = tokenStore.token ?: error("Sessão inválida")
        val payload = items.joinToString(",") { item ->
            val type = if (item is BrowseItem.Folder) "folder" else "file"
            "${item.id}|$type"
        }
        _events.tryEmit(Event.Started(suggestedName))
        ZipDownloadService.startZip(context, payload, suggestedName, token)
    }

    /**
     * Trabalho pesado do zip, chamado pelo [ZipDownloadService] já em foreground.
     * `onProgress(bytes, totalBytes)` atualiza a notificação persistente. Emite os
     * eventos (Started foi emitido em [downloadZip]) e devolve o [Outcome].
     */
    suspend fun runZipDownload(
        payload: String,
        suggestedName: String,
        token: String,
        onProgress: (loaded: Long, total: Long) -> Unit,
        onEncrypted: () -> Unit = {},
    ): Outcome {
        val backend = backendUrlHolder.current.trimEnd('/')
        return try {
            val manifest = runCatching {
                val murl = "$backend/api/zip-manifest?items=${Uri.encode(payload)}"
                httpClient.newCall(Request.Builder().url(murl).build()).execute().use { r ->
                    if (!r.isSuccessful) error("manifest HTTP ${r.code}")
                    zipJson.decodeFromString<ZipManifestResponse>(r.body?.string() ?: error("vazio"))
                }
            }.getOrNull()

            val hasEncrypted = manifest?.files?.any { it.encrypted } == true
            if (hasEncrypted) {
                onEncrypted()
                if (!e2eKeyManager.isUnlocked) {
                    _events.tryEmit(Event.Failed(suggestedName, "Desbloqueia a encriptação para descarregar"))
                    return Outcome.Failed("Desbloqueia a encriptação para descarregar")
                }
                val finalName = uniqueDestination(sanitize(suggestedName))
                val failed = writeZipToDownloads(finalName, decryptManifestNames(manifest!!), backend, onProgress)
                _events.tryEmit(Event.Completed(finalName))
                Outcome.Completed(finalName, failed)
            } else {
                // Sem ficheiros cifrados: zip do servidor via DownloadManager.
                val url = "$backend/api/zip?items=${Uri.encode(payload)}&token=${Uri.encode(token)}"
                enqueueViaManager(url, suggestedName)
                Outcome.ServerZipEnqueued
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.tryEmit(Event.Failed(suggestedName, e.message ?: "Erro no zip"))
            Outcome.Failed(e.message ?: "Erro no zip")
        }
    }

    // Decifra cada ficheiro e escreve-o como entrada do zip (streaming, sem
    // compressão = rápido; media/cifrado não comprime na mesma). Preserva caminhos.
    /**
     * Decifra os nomes do manifesto (em E2E o servidor só tem o placeholder) e
     * recompõe o caminho de cada entrada — sem isto o zip sai com '•' em todos
     * os ficheiros e pastas.
     */
    private fun decryptManifestNames(manifest: ZipManifestResponse): List<ZipManifestEntry> {
        if (manifest.names.isEmpty()) return manifest.files

        val plain = manifest.names.mapNotNull { (id, enc) ->
            e2eKeyManager.openNameOrNull(enc)?.let { id to it }
        }.toMap()

        if (plain.isEmpty()) return manifest.files

        return manifest.files.map { entry ->
            val name = plain[entry.id] ?: entry.name
            val dirs = entry.path_ids.mapNotNull { plain[it] }
            entry.copy(
                name = name,
                path = if (dirs.isEmpty()) name else dirs.joinToString("/") + "/" + name,
            )
        }
    }

    /** Escreve o zip em streaming; devolve os ficheiros que falharam (ficam fora). */
    private suspend fun writeZipToDownloads(
        name: String,
        files: List<ZipManifestEntry>,
        backend: String,
        onProgress: (loaded: Long, total: Long) -> Unit,
    ): List<String> {
        val failed = mutableListOf<String>()
        // % por bytes (soma dos tamanhos do manifesto), não por nº de ficheiros.
        val totalBytes = files.sumOf { it.filesize.coerceAtLeast(0L) }
        var doneBytes = 0L
        withDownloadsOutput(name, "application/zip") { raw ->
            java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(raw)).use { zos ->
                zos.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
                val used = HashSet<String>()
                for (f in files) {
                    kotlinx.coroutines.currentCoroutineContext().let { ctx -> if (ctx[kotlinx.coroutines.Job]?.isActive == false) throw kotlinx.coroutines.CancellationException("cancelado") }
                    var fileBytes = 0L
                    try {
                        var dataKey: ByteArray? = null
                        if (f.encrypted) {
                            val wrapped = runCatching { userEncryptionApi.fileKey(f.id).body()?.wrapped_data_key }.getOrNull()
                                ?: error("sem chave")
                            dataKey = e2eKeyManager.openFileDataKey(wrapped)
                        }
                        val url = "$backend/api/file/${Uri.encode(f.id)}/download"
                        val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
                        if (!resp.isSuccessful) {
                            resp.close()
                            error("HTTP ${resp.code}")
                        }
                        ResumableHttpInputStream(httpClient, url, resp, onBytes = { n ->
                            fileBytes = n
                            onProgress(doneBytes + n, totalBytes)
                        }).use { input ->
                            val entryName = uniqueZipPath(ensureExtension(f.path, f.mimetype), used)
                            zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                            if (dataKey != null) {
                                co.golink.tester.data.encryption.EncryptedFileCodec.decryptStream(input, zos, dataKey)
                            } else {
                                input.copyTo(zos, DEFAULT_COPY_BUFFER)
                            }
                            zos.closeEntry()
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Uma entrada a meio fica truncada mas o zip continua válido
                        // (o diretório central só lista as entradas fechadas).
                        runCatching { zos.closeEntry() }
                        failed.add(f.name)
                    }
                    doneBytes += maxOf(fileBytes, f.filesize)
                }
            }
        }
        return failed
    }

    /**
     * Abre o destino em Downloads e corre [block]. Em erro (ou cancelamento)
     * apaga a entrada — antes ficava presa em IS_PENDING, invisível mas a ocupar
     * espaço.
     */
    private suspend fun withDownloadsOutput(name: String, mime: String, block: suspend (java.io.OutputStream) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Não foi possível criar ficheiro em Downloads")
            try {
                val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Sem acesso a Downloads")
                out.use { block(it) }
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw t
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = java.io.File(dir, name)
            try {
                file.outputStream().use { block(it) }
            } catch (t: Throwable) {
                file.delete()
                throw t
            }
        }
    }

    // Evita nomes/caminhos repetidos dentro do zip (corrompem alguns extractores).
    private fun uniqueZipPath(path: String, used: HashSet<String>): String {
        if (used.add(path)) return path
        val dot = path.lastIndexOf('.')
        val base = if (dot > 0) path.substring(0, dot) else path
        val ext = if (dot > 0) path.substring(dot) else ""
        var i = 1
        while (!used.add("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    private fun enqueueViaManager(url: String, name: String): Long {
        val safe = uniqueDestination(sanitize(name))
        val request = DownloadManager.Request(Uri.parse(url))
            .addRequestHeader("Accept", "*/*")
            .setTitle(safe)
            .setDescription("GoLink")
            .setMimeType(mimeTypeFor(name))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safe)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        synchronized(pending) { pending[id] = safe }
        return id
    }

    private fun notify(id: Int, name: String, inProgress: Boolean, success: Boolean = false) {
        val icon = if (inProgress || success) android.R.drawable.stat_sys_download
                   else android.R.drawable.stat_notify_error
        val title = if (inProgress) "A transferir…"
                    else if (success) "Transferência concluída"
                    else "Falha na transferência"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(name)
            .setOngoing(inProgress)
            .setAutoCancel(!inProgress)
        if (inProgress) builder.setProgress(0, 0, true)
        notifManager.notify(id, builder.build())
    }

    // dataKey != null => decifra o stream (E2E) enquanto escreve; senão copia.
    private suspend fun writeToDownloads(name: String, mime: String, input: java.io.InputStream, dataKey: ByteArray? = null) {
        withDownloadsOutput(name, mime) { out ->
            if (dataKey != null) {
                co.golink.tester.data.encryption.EncryptedFileCodec.decryptStream(input, out, dataKey)
            } else {
                input.copyTo(out, DEFAULT_COPY_BUFFER)
            }
        }
    }

    private fun parseContentDispositionFilename(header: String?): String? {
        if (header == null) return null
        // RFC 5987: filename*=UTF-8''encoded-name.ext
        Regex("""filename\*\s*=\s*[Uu][Tt][Ff]-8''(.+)""").find(header)?.let { m ->
            return runCatching { java.net.URLDecoder.decode(m.groupValues[1].trim(), "UTF-8") }.getOrNull()
        }
        // Basic: filename="name.ext" or filename=name.ext
        return Regex("""filename\s*=\s*["']?([^"';\r\n]+)["']?""").find(header)
            ?.groupValues?.get(1)?.trim()
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180)

    private fun uniqueDestination(safe: String): String {
        val exists: (String) -> Boolean = { n ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mediaStoreDownloadExists(n)
            else java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), n
            ).exists()
        }
        if (!exists(safe)) return safe
        val dot = safe.lastIndexOf('.')
        val base = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var i = 1
        while (exists("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    private fun mediaStoreDownloadExists(name: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(name),
                null,
            )?.use { c -> c.count > 0 } ?: false
        }.getOrDefault(false)
    }

    private fun mimeTypeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase().ifBlank { return "application/octet-stream" }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun describeReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "não pode resumir"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "armazenamento indisponível"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "ficheiro já existe"
        DownloadManager.ERROR_FILE_ERROR -> "erro de escrita no ficheiro"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "erro nos dados HTTP"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "espaço insuficiente"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "demasiados redirecionamentos"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "código HTTP não suportado"
        DownloadManager.ERROR_UNKNOWN -> "erro desconhecido"
        in 400..599 -> "HTTP $reason"
        else -> "código $reason"
    }
}

/** Canal único das transferências (cria-o e apaga o antigo, que tinha importâncias diferentes). */
fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(FileDownloader.CHANNEL_ID) == null) {
        nm.createNotificationChannel(
            NotificationChannel(FileDownloader.CHANNEL_ID, "Transferências", NotificationManager.IMPORTANCE_LOW),
        )
    }
    nm.deleteNotificationChannel("golink_downloads")
}
