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
    @Named("authed") private val httpClient: OkHttpClient,
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

    companion object {
        private const val CHANNEL_ID = "golink_downloads"
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    fun downloadFile(id: String, name: String, basename: String? = null): Result<Unit> = runCatching {
        val backend = backendUrlHolder.current.trimEnd('/')
        val url = "$backend/api/file/${Uri.encode(id)}/download"
        val tempLabel = sanitize(name)
        val nid = notifId.getAndIncrement()
        _events.tryEmit(Event.Started(tempLabel))
        notify(nid, tempLabel, inProgress = true)
        scope.launch {
            try {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string()?.take(300)?.trim()
                        val msg = if (errBody.isNullOrBlank()) "HTTP ${response.code}" else "HTTP ${response.code}: $errBody"
                        notifManager.cancel(nid)
                        _events.tryEmit(Event.Failed(tempLabel, msg))
                        return@launch
                    }
                    val body = response.body ?: run {
                        notifManager.cancel(nid)
                        _events.tryEmit(Event.Failed(tempLabel, "Resposta vazia"))
                        return@launch
                    }
                    // Server sends Content-Disposition with the correct name + extension.
                    // Fall back to appending extension from basename if name lacks a dot.
                    val serverName = parseContentDispositionFilename(response.header("Content-Disposition"))
                    val resolvedName = when {
                        serverName != null -> serverName
                        basename != null && !name.contains('.') -> {
                            val ext = basename.substringAfterLast('.', "")
                            if (ext.isNotBlank() && ext.length <= 5) "$name.$ext" else name
                        }
                        else -> name
                    }
                    val finalName = uniqueDestination(sanitize(resolvedName))
                    writeToDownloads(finalName, mimeTypeFor(finalName), body.byteStream())
                    notifManager.cancel(nid)
                    notify(nid, finalName, inProgress = false, success = true)
                    _events.tryEmit(Event.Completed(finalName))
                }
            } catch (e: Exception) {
                notifManager.cancel(nid)
                _events.tryEmit(Event.Failed(tempLabel, e.message ?: "Erro desconhecido"))
            }
        }
    }

    fun downloadFile(file: BrowseItem.File): Result<Unit> =
        downloadFile(file.id, file.name, file.basename)

    fun downloadFolder(folder: BrowseItem.Folder): Result<Long> =
        downloadZip(listOf(folder), suggestedName = "${folder.name}.zip")

    fun downloadZip(items: List<BrowseItem>, suggestedName: String): Result<Long> = runCatching {
        if (items.isEmpty()) error("Sem itens para descarregar")
        val backend = backendUrlHolder.current.trimEnd('/')
        val token = tokenStore.token ?: error("Sessão inválida")
        val payload = items.joinToString(",") { item ->
            val type = if (item is BrowseItem.Folder) "folder" else "file"
            "${item.id}|$type"
        }
        val url = "$backend/api/zip?items=${Uri.encode(payload)}&token=${Uri.encode(token)}"
        enqueueViaManager(url, suggestedName).also { _events.tryEmit(Event.Started(suggestedName)) }
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

    private fun writeToDownloads(name: String, mime: String, input: java.io.InputStream) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Não foi possível criar ficheiro em Downloads")
            context.contentResolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
            val update = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, update, null, null)
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            java.io.File(dir, name).outputStream().use { input.copyTo(it) }
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
