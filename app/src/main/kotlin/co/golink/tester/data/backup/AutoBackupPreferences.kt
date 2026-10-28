package co.golink.tester.data.backup

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    fun isIncluded(collection: BackupCollection): Boolean = _state.value.isIncluded(collection)

    // --- Pastas seleccionadas por colecção (opt-in) -------------------------
    // O backup de cada colecção inclui apenas as pastas aqui guardadas. Vazio =
    // nada dessa colecção é enviado.

    fun selectedFolders(collection: BackupCollection): Set<String> =
        prefs.getStringSet(folderKey(collection), emptySet())?.toSet() ?: emptySet()

    fun setFolderSelected(collection: BackupCollection, folder: String, selected: Boolean) {
        val next = selectedFolders(collection).toMutableSet()
        if (selected) next.add(folder) else next.remove(folder)
        prefs.edit { putStringSet(folderKey(collection), next) }
        refresh()
    }

    fun setFolders(collection: BackupCollection, folders: Set<String>) {
        prefs.edit { putStringSet(folderKey(collection), folders) }
        refresh()
    }

    private fun folderKey(collection: BackupCollection): String = when (collection) {
        BackupCollection.IMAGES -> KEY_FOLDERS_IMAGES
        BackupCollection.VIDEOS -> KEY_FOLDERS_VIDEOS
        BackupCollection.AUDIOS -> KEY_FOLDERS_AUDIOS
        BackupCollection.DOCUMENTS -> KEY_FOLDERS_DOCUMENTS
        BackupCollection.DOWNLOADS -> KEY_FOLDERS_DOWNLOADS
    }

    private fun allSelectedFolders(): Map<BackupCollection, Set<String>> =
        BackupCollection.entries.associateWith { selectedFolders(it) }

    // --- Cursor por pasta ----------------------------------------------------
    // O cursor de "já enviado" é por pasta, não por colecção. Antes era por
    // colecção: ao adicionar uma pasta nova mais tarde, os ficheiros dela com id
    // abaixo do cursor da colecção eram saltados (o "9 de 34"). Por pasta, cada
    // pasta nova arranca do zero e é enviada por inteiro.

    fun folderCursor(collection: BackupCollection, folder: String): Long =
        prefs.getLong(folderCursorKey(collection, folder), 0L)

    fun setFolderCursor(collection: BackupCollection, folder: String, value: Long) {
        // Sem refresh(): os cursores não fazem parte do estado do ecrã e isto
        // corre uma vez por lote.
        prefs.edit { putLong(folderCursorKey(collection, folder), value) }
    }

    private fun folderCursorKey(collection: BackupCollection, folder: String): String =
        "$FOLDER_CURSOR_PREFIX${collection.name}|$folder"

    // --- Falhas por pasta ----------------------------------------------------
    // O cursor avança por tudo o que foi visto; o que falhou fica aqui (id →
    // tentativas) e é retomado no início da execução seguinte. Antes o cursor
    // ficava parado na primeira falha e tudo o que vinha depois era reenviado a
    // cada execução — com E2E o servidor não deteta duplicados (sem 409).

    @Synchronized
    fun failedIds(collection: BackupCollection, folder: String): Map<Long, Int> =
        prefs.getString(failedKey(collection, folder), null)
            ?.split(',')
            ?.mapNotNull { entry ->
                val id = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                id to (entry.substringAfter(':', "0").toIntOrNull() ?: 0)
            }
            ?.toMap()
            .orEmpty()

    @Synchronized
    fun setFailedIds(collection: BackupCollection, folder: String, value: Map<Long, Int>) {
        prefs.edit {
            if (value.isEmpty()) remove(failedKey(collection, folder))
            else putString(failedKey(collection, folder), value.entries.joinToString(",") { "${it.key}:${it.value}" })
        }
    }

    /** "Procurar de novo": volta a tentar também as falhas que já tinham desistido. */
    @Synchronized
    fun requeueFailed() {
        prefs.edit {
            prefs.all.filterKeys { it.startsWith(FAILED_PREFIX) }.forEach { (key, value) ->
                val reset = (value as? String)?.split(',')
                    ?.joinToString(",") { "${it.substringBefore(':')}:0" }
                if (reset.isNullOrEmpty()) remove(key) else putString(key, reset)
            }
        }
    }

    private fun failedKey(collection: BackupCollection, folder: String): String =
        "$FAILED_PREFIX${collection.name}|$folder"

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_AT, 0L)
        set(value) { prefs.edit { putLong(KEY_LAST_BACKUP_AT, value) }; refresh() }

    val backedUpCount: Int
        get() = prefs.getInt(KEY_BACKED_UP_COUNT, 0)

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) { prefs.edit { putString(KEY_LAST_ERROR, value) }; refresh() }

    // Ligado no telemóvel mas o servidor ainda não sabe (POST por confirmar).
    // Enquanto true, um "desligado" lido do servidor é a flag antiga — não
    // pode desligar localmente o que o utilizador acabou de ligar.
    var pendingServerEnable: Boolean
        get() = prefs.getBoolean(KEY_PENDING_SERVER_ENABLE, false)
        set(value) = prefs.edit { putBoolean(KEY_PENDING_SERVER_ENABLE, value) }

    // Desligado no telemóvel mas o POST de pausa deste aparelho falhou: repete
    // na próxima sincronização, senão a web continuava a mostrá-lo activo.
    var pendingServerDisable: Boolean
        get() = prefs.getBoolean(KEY_PENDING_SERVER_DISABLE, false)
        set(value) = prefs.edit { putBoolean(KEY_PENDING_SERVER_DISABLE, value) }

    // Este aparelho está em pausa na web (device_paused). Não desliga o backup
    // localmente, mas o ecrã mostra-o e oferece "Retomar".
    var devicePaused: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_PAUSED, false)
        set(value) { if (value != devicePaused) { prefs.edit { putBoolean(KEY_DEVICE_PAUSED, value) }; refresh() } }

    /**
     * Associa o estado do backup à conta [userId]. Outra conta no mesmo
     * telemóvel começa do zero: com os cursores da anterior, tudo o que estava
     * abaixo deles nunca chegava à conta nova. Sem dono guardado (instalações
     * anteriores) assume a conta actual.
     */
    @Synchronized
    fun claimOwner(userId: String) {
        val owner = prefs.getString(KEY_OWNER_ID, null)
        if (owner == userId) return
        prefs.edit {
            if (owner != null) clear()
            putString(KEY_OWNER_ID, userId)
            // clear() leva também a migração do wifi_only: repõe os defaults.
            putBoolean(KEY_ALLOW_WIFI, true)
            putBoolean(KEY_ALLOW_CELLULAR, false)
        }
        refresh()
    }

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
        // Só os contadores mudam: refresh() relia todas as prefs (5 StringSets)
        // a cada ficheiro enviado.
        _state.update {
            it.copy(
                backedUpCount = prefs.getInt(KEY_BACKED_UP_COUNT, 0),
                photosCount = prefs.getInt(KEY_PHOTOS_COUNT, 0),
                photosBytes = prefs.getLong(KEY_PHOTOS_BYTES, 0L),
                videosCount = prefs.getInt(KEY_VIDEOS_COUNT, 0),
                videosBytes = prefs.getLong(KEY_VIDEOS_BYTES, 0L),
            )
        }
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
        selectedFolders = allSelectedFolders(),
        lastBackupAt = prefs.getLong(KEY_LAST_BACKUP_AT, 0L),
        backedUpCount = prefs.getInt(KEY_BACKED_UP_COUNT, 0),
        photosCount = prefs.getInt(KEY_PHOTOS_COUNT, 0),
        photosBytes = prefs.getLong(KEY_PHOTOS_BYTES, 0L),
        videosCount = prefs.getInt(KEY_VIDEOS_COUNT, 0),
        videosBytes = prefs.getLong(KEY_VIDEOS_BYTES, 0L),
        lastError = prefs.getString(KEY_LAST_ERROR, null),
        devicePaused = prefs.getBoolean(KEY_DEVICE_PAUSED, false),
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
        const val KEY_FOLDERS_IMAGES = "folders_images"
        const val KEY_FOLDERS_VIDEOS = "folders_videos"
        const val KEY_FOLDERS_AUDIOS = "folders_audios"
        const val KEY_FOLDERS_DOCUMENTS = "folders_documents"
        const val KEY_FOLDERS_DOWNLOADS = "folders_downloads"
        const val FOLDER_CURSOR_PREFIX = "fcursor|"
        const val FAILED_PREFIX = "ffailed|"
        const val KEY_PENDING_SERVER_ENABLE = "pending_server_enable"
        const val KEY_PENDING_SERVER_DISABLE = "pending_server_disable"
        const val KEY_DEVICE_PAUSED = "device_paused"
        const val KEY_OWNER_ID = "owner_id"
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
    val selectedFolders: Map<BackupCollection, Set<String>> = emptyMap(),
    val lastBackupAt: Long,
    val backedUpCount: Int,
    val photosCount: Int = 0,
    val photosBytes: Long = 0L,
    val videosCount: Int = 0,
    val videosBytes: Long = 0L,
    val lastError: String? = null,
    val devicePaused: Boolean = false,
) {
    fun isIncluded(collection: BackupCollection): Boolean = when (collection) {
        BackupCollection.IMAGES -> includeImages
        BackupCollection.VIDEOS -> includeVideos
        BackupCollection.AUDIOS -> includeAudios
        BackupCollection.DOCUMENTS -> includeDocuments
        BackupCollection.DOWNLOADS -> includeDownloads
    }

    /** O que o worker envia de facto: colecção ligada E com pastas escolhidas (opt-in). */
    fun activeCollections(): Set<BackupCollection> =
        BackupCollection.entries.filter { isIncluded(it) && selectedFolders[it].orEmpty().isNotEmpty() }.toSet()

    /** Erro que pede acção do utilizador (não a pausa nem falhas que o próximo tick repete). */
    val needsAttention: Boolean
        get() = lastError != null &&
            lastError != AutoBackupManager.DEVICE_PAUSED_ERROR &&
            !lastError.startsWith(AutoBackupManager.OFFLINE_ERROR) &&
            !lastError.endsWith(AutoBackupManager.FAILED_FILES_ERROR)
}
