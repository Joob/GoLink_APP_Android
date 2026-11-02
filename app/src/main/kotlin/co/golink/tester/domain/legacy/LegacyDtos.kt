package co.golink.tester.domain.legacy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Respostas de routes/legacy.php (LegacyPresenter no servidor). Só ciphertext: decifra o cliente. */

@Serializable
data class LegacyVaultResponse(
    val data: LegacyFullDto? = null,
    val guarding_count: Int = 0,
)

@Serializable
data class LegacyDataResponse(val data: LegacyFullDto? = null)

/** full (vault + linhas), roster (vault + guardians) ou summary (só vault). */
@Serializable
data class LegacyFullDto(
    val vault: LegacyVaultDto,
    val guardians: List<LegacyGuardianDto>? = null,
    val recipients: List<LegacyRecipientDto>? = null,
    val items: List<LegacyItemDto>? = null,
)

@Serializable
data class LegacyVaultDto(
    val id: String,
    val status: String,
    val threshold: Int = 1,
    val release_after_days: Int = 90,
    val wait_days: Int = 7,
    val key_epoch: Int = 0,
    val rekey_required: Boolean = false,
    val sealed_vault_key: String,
    val vault_check: String,
    val checkin_web: Boolean = true,
    val checkin_android: Boolean = true,
    val checkin_email: Boolean = true,
    val last_checkin_at: String? = null,
    val paused_until: String? = null,
    val notified_at: String? = null,
    val releases_at: String? = null,
    val released_at: String? = null,
    val opened_at: String? = null,
    val timeline: LegacyTimelineDto,
    val ready: LegacyReadyDto = LegacyReadyDto(),
)

@Serializable
data class LegacyTimelineDto(
    val days_since: Int = 0,
    val reminders: List<Int> = emptyList(),
    val guardians_day: Int = 0,
    val opens_day: Int = 0,
    val next_reminder: Int? = null,
)

@Serializable
data class LegacyReadyDto(
    val guardians: Boolean = false,
    val recipients: Boolean = false,
    val items: Boolean = false,
)

@Serializable
data class LegacyGuardianDto(
    val id: String,
    val email: String,
    val slot: Int? = null,
    val status: String,
    val meta_enc: String? = null,
    val share_vault: String? = null,
    val sealed: Boolean = false,
    val sealed_pubkey: String? = null,
    val public_key: String? = null,
    val invited_at: String? = null,
    val accepted_at: String? = null,
    val confirmed_at: String? = null,
)

@Serializable
data class LegacyRecipientDto(
    val id: String,
    val email: String,
    val meta_enc: String? = null,
    val code_vault: String? = null,
    val link_sent_at: String? = null,
    val first_opened_at: String? = null,
)

@Serializable
data class LegacyItemDto(
    val id: String,
    val type: String,
    val state: String,
    val meta_enc: String? = null,
    val key_vault: String? = null,
    val delay_days: Int = 0,
    val deliver_at: String? = null,
    val created_at: String? = null,
    val recipient_ids: List<String> = emptyList(),
    val file_count: Int = 0,
    val bytes: Long = 0,
)

@Serializable
data class LegacyEventsResponse(
    val data: List<LegacyEventDto> = emptyList(),
    val next_cursor: String? = null,
)

@Serializable
data class LegacyEventDto(
    val id: String,
    val category: String,
    val payload: String,
    val created_at: String,
)

@Serializable
data class LegacyCheckinResponse(
    val counted: Boolean = false,
    val data: LegacyFullDto? = null,
)

/** Linha decifrada: `meta` vazio com [unreadable] se o meta_enc não abriu. */
data class LegacyMeta(val json: JsonObject, val unreadable: Boolean = false) {
    fun str(key: String): String? = (json[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
}

data class LegacyGuardian(val dto: LegacyGuardianDto, val meta: LegacyMeta)

data class LegacyRecipient(val dto: LegacyRecipientDto, val meta: LegacyMeta, val code: String?)

data class LegacyItem(val dto: LegacyItemDto, val meta: LegacyMeta)

// ---- itens (Fase 2) ----

@Serializable
data class LegacyItemResponse(val data: LegacyItemDto)

/** GET /items/{id}: o resumo + payload_enc (só aqui, até 4 MiB). */
@Serializable
data class LegacyItemPayloadResponse(val data: LegacyItemPayloadDto)

@Serializable
data class LegacyItemPayloadDto(
    val id: String,
    val key_vault: String,
    val payload_enc: String,
)

@Serializable
data class LegacyFolderManifestResponse(val data: LegacyFolderManifest)

@Serializable
data class LegacyFolderManifest(
    val folders: List<LegacyManifestRow> = emptyList(),
    val files: List<LegacyManifestRow> = emptyList(),
    val skipped: Int = 0,
)

@Serializable
data class LegacyManifestRow(
    val id: String,
    val parent_id: String? = null,
    val name: String? = null,
    val name_encrypted: String? = null,
    val filesize: Long? = null,
    val mimetype: String? = null,
)

// ---- pessoas (Fase 3) ----

@Serializable
data class LegacyGuardianResponse(val data: LegacyGuardianDto)

@Serializable
data class LegacyRecipientResponse(val data: LegacyRecipientDto)

// ---- guardião e páginas públicas (Fase 4) ----

@Serializable
data class LegacyGuardingRow(
    val id: String,
    val owner_name: String = "",
    val status: String,
    val slot: Int? = null,
    val threshold: Int = 1,
    val count: Int = 0,
    val vault_status: String = "",
    val releases_at: String? = null,
    val sealed: Boolean = false,
    val sealed_challenge: String? = null,
    val confirmed_at: String? = null,
    val contributed: Boolean = false,
)

@Serializable
data class LegacyGuardingListResponse(val data: List<LegacyGuardingRow> = emptyList())

@Serializable
data class LegacyGuardingRowResponse(val data: LegacyGuardingRow)

@Serializable
data class LegacyReleaseRecipientDto(
    val id: String,
    val email: String,
    val meta_enc: String? = null,
    val code_vault: String? = null,
    val link_sent_at: String? = null,
    val first_opened_at: String? = null,
)

@Serializable
data class LegacyReleaseDto(
    val vault_id: String,
    val epoch: Int,
    val slot: Int,
    val threshold: Int,
    val vault_check: String,
    val sealed_share: String,
    val relays: List<String> = emptyList(),
    val contributed: Boolean = false,
    val contributors: Int = 0,
    val status: String = "",
    val recipients: List<LegacyReleaseRecipientDto> = emptyList(),
)

@Serializable
data class LegacyReleaseResponse(val data: LegacyReleaseDto)

@Serializable
data class LegacyInviteDto(val owner_name: String = "", val status: String, val threshold: Int = 1, val email: String = "")

@Serializable
data class LegacyInviteResponse(val data: LegacyInviteDto)

@Serializable
data class LegacyPublicCheckinResponse(val counted: Boolean = false)

@Serializable
data class LegacyOpenItemDto(
    val id: String,
    val type: String,
    val locked: Boolean = false,
    val available_at: String? = null,
    val file_count: Int = 0,
    val bytes: Long = 0,
    val wrapped_key: String? = null,
    val payload_enc: String? = null,
)

@Serializable
data class LegacyOpenDto(val recipient_id: String, val owner_name: String = "", val items: List<LegacyOpenItemDto> = emptyList())

@Serializable
data class LegacyOpenResponse(val data: LegacyOpenDto)

/** filesize pode vir como número ou texto (DB::table). */
@Serializable
data class LegacyOpenFileRow(
    val file_id: String,
    val wrapped_key: String,
    val meta_enc: String,
    val filesize: kotlinx.serialization.json.JsonPrimitive? = null,
)

@Serializable
data class LegacyOpenFilesResponse(val data: List<LegacyOpenFileRow> = emptyList(), val has_more: Boolean = false)
