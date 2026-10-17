package co.golink.tester.domain.backup

import kotlinx.serialization.Serializable

/** GET api/browse/mobile-backup/summary — cartões dos telemóveis. */
@Serializable
data class BackupSummary(
    val enabled: Boolean = true,
    val devices: List<BackupDevice> = emptyList(),
    val totals: BackupTotals = BackupTotals(),
)

@Serializable
data class BackupDevice(
    val id: String,
    val platform: String = "android",
    val name: String = "Android",
    val paused: Boolean = false,
    /** É este telemóvel (o servidor compara o X-Device-Id). */
    val current: Boolean = false,
    val last_backup_at: String? = null,
    val folders: Int = 0,
    val items: Int = 0,
    val bytes: Long = 0,
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
