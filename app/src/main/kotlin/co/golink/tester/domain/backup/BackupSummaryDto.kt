package co.golink.tester.domain.backup

import kotlinx.serialization.Serializable

/** GET api/browse/mobile-backup/summary — cartões dos telemóveis. */
@Serializable
data class BackupSummary(
    val enabled: Boolean = true,
    val devices: List<BackupDevice> = emptyList(),
    /** Ficheiros antigos sem aparelho quando não há Android registado (id "legacy"). */
    val legacy: BackupDevice? = null,
    val totals: BackupTotals = BackupTotals(),
)

@Serializable
data class BackupDevice(
    val id: String,
    val platform: String = "android",
    val os_version: String? = null,
    val name: String = "Android",
    val paused: Boolean = false,
    /** É este telemóvel (o servidor compara o X-Device-Id). */
    val current: Boolean = false,
    val last_backup_at: String? = null,
    val folders: Int = 0,
    val items: Int = 0,
    val bytes: Long = 0,
    /** Pastas de origem por separador (photos/videos/audios/files). */
    val tabs: Map<String, Int> = emptyMap(),
    /** Ficheiros por separador (photos/videos/audios/files); null em servidores antigos. */
    val counts: Map<String, Int>? = null,
)

@Serializable
data class BackupTotals(
    val folders: Int = 0,
    val items: Int = 0,
    val bytes: Long = 0,
    val last_backup_at: String? = null,
    val all_encrypted: Boolean = false,
)

@Serializable
data class PauseDeviceBody(val paused: Boolean)

/** POST mobile-backup/check: até 500 upload_key; o servidor devolve as que já existem. */
@Serializable
data class BackupKeysCheckBody(val upload_keys: List<String>)

@Serializable
data class BackupKeysCheckResponse(
    val existing: List<String> = emptyList(),
    // false = servidor sem a coluna upload_key (não sabe responder).
    val supported: Boolean = true,
)
