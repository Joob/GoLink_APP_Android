package co.golink.tester.data.backup

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AutoBackupPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)

    init {
        // Migração do antigo toggle único "wifi_only" para os dois toggles
        // independentes (Wi-Fi / dados móveis), que podem estar ambos activos.
        if (!prefs.contains(KEY_ALLOW_WIFI)) {
            val legacyWifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true)
            prefs.edit {
                putBoolean(KEY_ALLOW_WIFI, true)
                putBoolean(KEY_ALLOW_CELLULAR, !legacyWifiOnly)
            }
        }
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AutoBackupState> = _state.asStateFlow()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit { putBoolean(KEY_ENABLED, value) }; refresh() }

    var allowWifi: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_WIFI, true)
        set(value) { prefs.edit { putBoolean(KEY_ALLOW_WIFI, value) }; refresh() }

    var allowCellular: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_CELLULAR, false)
        set(value) { prefs.edit { putBoolean(KEY_ALLOW_CELLULAR, value) }; refresh() }

    var chargingOnly: Boolean
        get() = prefs.getBoolean(KEY_CHARGING_ONLY, false)
        set(value) { prefs.edit { putBoolean(KEY_CHARGING_ONLY, value) }; refresh() }

    var includeImages: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_IMAGES, true)
        set(value) { prefs.edit { putBoolean(KEY_INCLUDE_IMAGES, value) }; refresh() }

    var includeVideos: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_VIDEOS, true)
        set(value) { prefs.edit { putBoolean(KEY_INCLUDE_VIDEOS, value) }; refresh() }

    var includeAudios: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_AUDIOS, false)
        set(value) { prefs.edit { putBoolean(KEY_INCLUDE_AUDIOS, value) }; refresh() }

    var includeDocuments: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_DOCUMENTS, false)
        set(value) { prefs.edit { putBoolean(KEY_INCLUDE_DOCUMENTS, value) }; refresh() }

    var includeDownloads: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_DOWNLOADS, false)
        set(value) { prefs.edit { putBoolean(KEY_INCLUDE_DOWNLOADS, value) }; refresh() }

    var lastImageId: Long
        get() = prefs.getLong(KEY_LAST_IMAGE_ID, 0L)
        set(value) { prefs.edit { putLong(KEY_LAST_IMAGE_ID, value) }; refresh() }

    var lastVideoId: Long
        get() = prefs.getLong(KEY_LAST_VIDEO_ID, 0L)
        set(value) { prefs.edit { putLong(KEY_LAST_VIDEO_ID, value) }; refresh() }

    var lastAudioId: Long
        get() = prefs.getLong(KEY_LAST_AUDIO_ID, 0L)
        set(value) { prefs.edit { putLong(KEY_LAST_AUDIO_ID, value) }; refresh() }

    var lastDocumentId: Long
        get() = prefs.getLong(KEY_LAST_DOCUMENT_ID, 0L)
        set(value) { prefs.edit { putLong(KEY_LAST_DOCUMENT_ID, value) }; refresh() }

    var lastDownloadId: Long
        get() = prefs.getLong(KEY_LAST_DOWNLOAD_ID, 0L)
        set(value) { prefs.edit { putLong(KEY_LAST_DOWNLOAD_ID, value) }; refresh() }

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_AT, 0L)
        set(value) { prefs.edit { putLong(KEY_LAST_BACKUP_AT, value) }; refresh() }

    val backedUpCount: Int
        get() = prefs.getInt(KEY_BACKED_UP_COUNT, 0)

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) { prefs.edit { putString(KEY_LAST_ERROR, value) }; refresh() }

    // Synchronized: o worker faz uploads em paralelo e este é um
    // read-modify-write — sem lock os incrementos perdem-se.
    @Synchronized
    fun recordUploaded(collection: BackupCollection, bytes: Long) {
        prefs.edit {
            putInt(KEY_BACKED_UP_COUNT, backedUpCount + 1)
            when (collection) {
                BackupCollection.VIDEOS -> {
                    putInt(KEY_VIDEOS_COUNT, prefs.getInt(KEY_VIDEOS_COUNT, 0) + 1)
                    putLong(KEY_VIDEOS_BYTES, prefs.getLong(KEY_VIDEOS_BYTES, 0L) + bytes.coerceAtLeast(0L))
                }
                BackupCollection.IMAGES -> {
                    putInt(KEY_PHOTOS_COUNT, prefs.getInt(KEY_PHOTOS_COUNT, 0) + 1)
                    putLong(KEY_PHOTOS_BYTES, prefs.getLong(KEY_PHOTOS_BYTES, 0L) + bytes.coerceAtLeast(0L))
                }
                // Áudios/documentos/downloads só contam para o total geral.
                else -> {
                    putInt(KEY_OTHERS_COUNT, prefs.getInt(KEY_OTHERS_COUNT, 0) + 1)
                    putLong(KEY_OTHERS_BYTES, prefs.getLong(KEY_OTHERS_BYTES, 0L) + bytes.coerceAtLeast(0L))
                }
            }
        }
        refresh()
    }

    fun resetCursors() {
        prefs.edit {
            putLong(KEY_LAST_IMAGE_ID, 0L)
            putLong(KEY_LAST_VIDEO_ID, 0L)
            putLong(KEY_LAST_AUDIO_ID, 0L)
            putLong(KEY_LAST_DOCUMENT_ID, 0L)
            putLong(KEY_LAST_DOWNLOAD_ID, 0L)
        }
        refresh()
    }

    fun clearError() {
        prefs.edit { remove(KEY_LAST_ERROR) }
        refresh()
    }

    private fun refresh() {
        _state.value = read()
    }

    private fun read() = AutoBackupState(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        allowWifi = prefs.getBoolean(KEY_ALLOW_WIFI, true),
        allowCellular = prefs.getBoolean(KEY_ALLOW_CELLULAR, false),
        chargingOnly = prefs.getBoolean(KEY_CHARGING_ONLY, false),
        includeImages = prefs.getBoolean(KEY_INCLUDE_IMAGES, true),
        includeVideos = prefs.getBoolean(KEY_INCLUDE_VIDEOS, true),
        includeAudios = prefs.getBoolean(KEY_INCLUDE_AUDIOS, false),
        includeDocuments = prefs.getBoolean(KEY_INCLUDE_DOCUMENTS, false),
        includeDownloads = prefs.getBoolean(KEY_INCLUDE_DOWNLOADS, false),
        lastBackupAt = prefs.getLong(KEY_LAST_BACKUP_AT, 0L),
        backedUpCount = prefs.getInt(KEY_BACKED_UP_COUNT, 0),
        photosCount = prefs.getInt(KEY_PHOTOS_COUNT, 0),
        photosBytes = prefs.getLong(KEY_PHOTOS_BYTES, 0L),
        videosCount = prefs.getInt(KEY_VIDEOS_COUNT, 0),
        videosBytes = prefs.getLong(KEY_VIDEOS_BYTES, 0L),
        lastError = prefs.getString(KEY_LAST_ERROR, null),
    )

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_ALLOW_WIFI = "allow_wifi"
        const val KEY_ALLOW_CELLULAR = "allow_cellular"
        const val KEY_CHARGING_ONLY = "charging_only"
        const val KEY_INCLUDE_IMAGES = "include_images"
        const val KEY_INCLUDE_VIDEOS = "include_videos"
        const val KEY_INCLUDE_AUDIOS = "include_audios"
        const val KEY_INCLUDE_DOCUMENTS = "include_documents"
        const val KEY_INCLUDE_DOWNLOADS = "include_downloads"
        const val KEY_LAST_IMAGE_ID = "last_image_id"
        const val KEY_LAST_VIDEO_ID = "last_video_id"
        const val KEY_LAST_AUDIO_ID = "last_audio_id"
        const val KEY_LAST_DOCUMENT_ID = "last_document_id"
        const val KEY_LAST_DOWNLOAD_ID = "last_download_id"
        const val KEY_OTHERS_COUNT = "others_count"
        const val KEY_OTHERS_BYTES = "others_bytes"
        const val KEY_LAST_BACKUP_AT = "last_backup_at"
        const val KEY_BACKED_UP_COUNT = "backed_up_count"
        const val KEY_PHOTOS_COUNT = "photos_count"
        const val KEY_PHOTOS_BYTES = "photos_bytes"
        const val KEY_VIDEOS_COUNT = "videos_count"
        const val KEY_VIDEOS_BYTES = "videos_bytes"
        const val KEY_LAST_ERROR = "last_error"
    }
}

data class AutoBackupState(
    val enabled: Boolean,
    val allowWifi: Boolean,
    val allowCellular: Boolean,
    val chargingOnly: Boolean,
    val includeImages: Boolean = true,
    val includeVideos: Boolean,
    val includeAudios: Boolean = false,
    val includeDocuments: Boolean = false,
    val includeDownloads: Boolean = false,
    val lastBackupAt: Long,
    val backedUpCount: Int,
    val photosCount: Int = 0,
    val photosBytes: Long = 0L,
    val videosCount: Int = 0,
    val videosBytes: Long = 0L,
    val lastError: String? = null,
)
