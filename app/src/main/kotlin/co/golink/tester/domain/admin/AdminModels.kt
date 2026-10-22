package co.golink.tester.domain.admin

import co.golink.tester.domain.user.UserResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ---------------------------------------------------------------------------
// Dashboard  (GET api/admin/dashboard)
// ---------------------------------------------------------------------------

@Serializable
data class DashboardResponse(
    val users: DashboardUsers = DashboardUsers(),
    val disk: DashboardDisk = DashboardDisk(),
    val app: DashboardApp = DashboardApp(),
)

@Serializable
data class DashboardUsers(
    val total: Int = 0,
    val online: Int = 0,
    val guests: Int = 0,
    val usersPremiumTotal: Int = 0,
)

@Serializable
data class DashboardDisk(
    val used: String? = null,
    val days: Int = 45,
    val download: DashboardTraffic = DashboardTraffic(),
    val upload: DashboardTraffic = DashboardTraffic(),
)

@Serializable
data class DashboardTraffic(
    val total: String? = null,
    val records: List<TrafficRecord> = emptyList(),
)

/** Um dia de tráfego; [percentage] é relativa ao máximo do período. */
@Serializable
data class TrafficRecord(
    val created_at: String? = null,
    val percentage: Double = 0.0,
    val amount: String? = null,
)

@Serializable
data class DashboardApp(
    val license: String? = null,
    val version: String? = null,
    val earnings: String? = null,
    val cron: DashboardCron = DashboardCron(),
)

@Serializable
data class DashboardCron(
    val isRunning: Boolean = false,
)

// ---------------------------------------------------------------------------
// Analytics  (GET api/admin/analytics)
// ---------------------------------------------------------------------------

// GET api/admin/analytics/summary?range=24h|7d|30d|90d
@Serializable
data class AnalyticsResponse(
    val range: String? = null,
    val granularity: String? = null,
    val cards: AnalyticsCards = AnalyticsCards(),
    val series: AnalyticsSeries = AnalyticsSeries(),
    val countries: List<RankedItem> = emptyList(),
    val cities: List<RankedItem> = emptyList(),
    val devices: List<RankedItem> = emptyList(),
    val browsers: List<RankedItem> = emptyList(),
    val os: List<RankedItem> = emptyList(),
    val referrers: List<RankedItem> = emptyList(),
    val events: List<RankedItem> = emptyList(),
    val behavior: AnalyticsBehavior = AnalyticsBehavior(),
)

@Serializable
data class AnalyticsCards(
    val visitors: Int = 0,
    val visits: Int = 0,
    val visitorsChange: Double = 0.0,
    val visitsChange: Double = 0.0,
    val botsFiltered: Int = 0,
)

/** Série temporal do sumário: um ponto por dia (ou por hora em 24h). */
@Serializable
data class AnalyticsSeries(
    val labels: List<String> = emptyList(),
    val visitors: List<Int> = emptyList(),
    val visits: List<Int> = emptyList(),
)

@Serializable
data class RankedItem(
    val label: String? = null,
    val code: String? = null,
    val value: Int = 0,
    val percentage: Double = 0.0,
)

@Serializable
data class AnalyticsBehavior(
    val new: Int = 0,
    val returning: Int = 0,
)

// ---------------------------------------------------------------------------
// Users  (GET api/admin/users) — reuses the JSON:API UserResponse envelope
// ---------------------------------------------------------------------------

@Serializable
data class AdminUsersResponse(
    val data: List<UserResponse> = emptyList(),
    val meta: AdminPageMeta? = null,
)

@Serializable
data class AdminPageMeta(
    val current_page: Int = 1,
    val last_page: Int = 1,
    val total: Int = 0,
)

// ---------------------------------------------------------------------------
// Invite registers  (GET/POST/DELETE api/admin/invite-registers)
// ---------------------------------------------------------------------------

@Serializable
data class InviteRegistersResponse(
    val data: List<InviteEnvelope> = emptyList(),
    val meta: AdminPageMeta? = null,
)

@Serializable
data class InviteEnvelope(
    val data: InviteData,
)

@Serializable
data class InviteData(
    val id: JsonElement? = null,
    val attributes: InviteAttributes = InviteAttributes(),
    val relationships: InviteRelationships? = null,
) {
    val idString: String
        get() = (id as? JsonPrimitive)?.contentOrNull.orEmpty()
}

@Serializable
data class InviteAttributes(
    val email: String? = null,
    val status: String? = null,
    val token: String? = null,
    val sent_at: String? = null,
    val completed_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class InviteRelationships(
    val inviter: InviterEnvelope? = null,
)

@Serializable
data class InviterEnvelope(
    val data: InviterData? = null,
)

@Serializable
data class InviterData(
    val attributes: InviterAttributes = InviterAttributes(),
)

@Serializable
data class InviterAttributes(
    val name: String? = null,
    val email: String? = null,
)

@Serializable
data class CreateInviteRequest(
    val emails: List<String>,
)

/**
 * Suspensão de conta. [unit]/[value] a null = suspensão indefinida; o backend
 * exige value sempre que unit vier preenchido.
 */
@Serializable
data class SuspendUserRequest(
    val unit: String? = null,
    val value: Int? = null,
    val reason: String? = null,
)

// ---------------------------------------------------------------------------
// UI models
// ---------------------------------------------------------------------------

data class AdminUserItem(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val avatar: String?,
    val isSuspended: Boolean = false,
    val suspendedUntil: String? = null,
    val suspendedReason: String? = null,
    val storage: String? = null,
    val plan: String? = null,
    val lastLoginAt: String? = null,
    val deletionScheduledFor: String? = null,
)

data class InviteItem(
    val id: String,
    val email: String,
    val status: String,
    val inviterName: String?,
    val createdAt: String?,
)

// ---------------------------------------------------------------------------
// Admin > Users (lista leve: AdminUserListResource, ?status=&search=)
// ---------------------------------------------------------------------------

private fun JsonElement?.idOrEmpty(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()

@Serializable
data class AdminUserListResponse(
    val data: List<AdminUserListEnvelope> = emptyList(),
    val meta: AdminPageMeta? = null,
    val counts: AdminUserCounts? = null,
)

@Serializable
data class AdminUserCounts(
    val all: Int = 0,
    val active: Int = 0,
    val suspended: Int = 0,
    val deleted: Int = 0,
)

@Serializable
data class AdminUserListEnvelope(val data: AdminUserListData)

@Serializable
data class AdminUserListData(
    val id: JsonElement? = null,
    val attributes: AdminUserListAttributes = AdminUserListAttributes(),
) {
    val idString: String get() = id.idOrEmpty()
}

@Serializable
data class AdminUserListAttributes(
    val name: String? = null,
    val avatar: String? = null,
    val email: String? = null,
    val role: String? = null,
    val storage: AdminStorageSummary? = null,
    val created_at: String? = null,
    val last_login_at: String? = null,
    val is_suspended: Boolean = false,
    val suspended_until: String? = null,
    val suspended_reason: String? = null,
    val is_pending_deletion: Boolean = false,
    val plan: String? = null,
    val deletion_scheduled_for: String? = null,
)

@Serializable
data class AdminStorageSummary(
    val used_formatted: String? = null,
    val capacity_formatted: String? = null,
)

/** Admin > Users > Eliminados (DeletedUsersController::row). */
@Serializable
data class DeletedUsersResponse(
    val data: List<DeletedUserRow> = emptyList(),
    val meta: AdminPageMeta? = null,
)

@Serializable
data class DeletedUserRow(
    val id: JsonElement? = null,
    val user_id: JsonElement? = null,
    /** scheduled | deleting | deleted */
    val state: String = "deleted",
    /** user | admin | support */
    val requested_by: String = "user",
    val name: String? = null,
    val email: String? = null,
    val avatar: String? = null,
    val role: String? = null,
    val plan: String? = null,
    val scheduled_for: String? = null,
    val deleted_at: String? = null,
) {
    val userIdString: String? get() = user_id.idOrEmpty().ifBlank { null }
}

@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String,
    val password: String,
    val password_confirmation: String,
    val role: String,
)

@Serializable
data class RoleAttributes(val role: String)

@Serializable
data class ChangeRoleRequest(val attributes: RoleAttributes)

@Serializable
data class CapacityAttributes(val max_storage_amount: Int)

@Serializable
data class ChangeCapacityRequest(val attributes: CapacityAttributes)

@Serializable
data class AdminEmailChangeRequest(val email: String, val password: String)

@Serializable
data class AdminCodeRequest(val code: String)

@Serializable
data class AdminDeleteRequest(val name: String, val password: String)

@Serializable
data class AdminDeleteConfirm(val name: String, val code: String)

/** GET api/admin/users/{id}/storage (UserStorageResource). */
@Serializable
data class UserStorageResponse(val data: UserStorageData = UserStorageData())

@Serializable
data class UserStorageData(
    val attributes: UserStorageAttributes = UserStorageAttributes(),
    val meta: UserStorageMeta? = null,
)

@Serializable
data class UserStorageAttributes(
    val used: String? = null,
    val capacity: String? = null,
    val percentage: Double = 0.0,
    val used_bytes: Long = 0,
    val capacity_bytes: Long = 0,
    val has_limit: Boolean = true,
)

@Serializable
data class UserStorageMeta(
    val traffic: UserTraffic? = null,
    val images: UserStorageSlice? = null,
    val audios: UserStorageSlice? = null,
    val videos: UserStorageSlice? = null,
    val documents: UserStorageSlice? = null,
    val others: UserStorageSlice? = null,
)

@Serializable
data class UserTraffic(val download: String? = null, val upload: String? = null)

@Serializable
data class UserStorageSlice(val used: String? = null, val percentage: Double = 0.0)

// ---------------------------------------------------------------------------
// Audit log (GET api/admin/audit-logs)
// ---------------------------------------------------------------------------

@Serializable
data class AuditLogsResponse(
    val data: List<AuditLogEnvelope> = emptyList(),
    val meta: AdminPageMeta? = null,
)

@Serializable
data class AuditLogEnvelope(val data: AuditLogData)

@Serializable
data class AuditLogData(
    val id: JsonElement? = null,
    val attributes: AuditLogAttributes = AuditLogAttributes(),
) {
    val idString: String get() = id.idOrEmpty()
}

@Serializable
data class AuditLogAttributes(
    val actor_name: String? = null,
    val action: String? = null,
    val method: String? = null,
    val target_name: String? = null,
    val path: String? = null,
    val params: JsonElement? = null,
    val payload: JsonElement? = null,
    val status: Int? = null,
    val ip: String? = null,
    val user_agent: String? = null,
    val created_at: String? = null,
)

// ---------------------------------------------------------------------------
// Dashboard extra: transações recentes e migração E2E
// ---------------------------------------------------------------------------

@Serializable
data class TransactionsResponse(
    val data: List<TransactionEnvelope> = emptyList(),
    val meta: AdminPageMeta? = null,
)

@Serializable
data class TransactionEnvelope(val data: TransactionData)

@Serializable
data class TransactionData(
    val id: JsonElement? = null,
    val attributes: TransactionAttributes = TransactionAttributes(),
    val relationships: TransactionRelationships? = null,
) {
    val idString: String get() = id.idOrEmpty()
}

@Serializable
data class TransactionAttributes(
    val type: String? = null,
    val status: String? = null,
    val note: String? = null,
    val price: String? = null,
    val driver: String? = null,
    val reference: String? = null,
    val created_at: String? = null,
    val is_refunded: Boolean = false,
)

@Serializable
data class TransactionRelationships(val user: TransactionUserEnvelope? = null)

@Serializable
data class TransactionUserEnvelope(val data: TransactionUser? = null)

@Serializable
data class TransactionUser(val attributes: TransactionUserAttributes = TransactionUserAttributes())

@Serializable
data class TransactionUserAttributes(val name: String? = null, val email: String? = null)

@Serializable
data class EncryptionMigrationResponse(
    val data: List<EncryptionMigrationRow> = emptyList(),
    val meta: AdminPageMeta? = null,
)

@Serializable
data class EncryptionMigrationRow(
    val id: JsonElement? = null,
    val name: String? = null,
    val email: String? = null,
    val created_at: String? = null,
    val encryption_configured: Boolean = false,
    val migration: EncryptionMigrationState = EncryptionMigrationState(),
    val plain_names: Int = 0,
)

@Serializable
data class EncryptionMigrationState(
    /** empty | none | partial | done */
    val status: String = "empty",
    val percent: Int? = null,
    val total_files: Int = 0,
    val encrypted_files: Int = 0,
)

// ---------------------------------------------------------------------------
// Analytics extra: tempo real e eventos (product-usage)
// ---------------------------------------------------------------------------

@Serializable
data class RealtimeResponse(
    val cards: RealtimeCards = RealtimeCards(),
    val series: AnalyticsSeries = AnalyticsSeries(),
    val activity: List<RealtimeActivity> = emptyList(),
)

@Serializable
data class RealtimeCards(
    val visitors: Int = 0,
    val visits: Int = 0,
    val onlineNow: Int = 0,
    val botsFiltered: Int = 0,
)

@Serializable
data class RealtimeActivity(
    val country_code: String? = null,
    val country_name: String? = null,
    val city: String? = null,
    val browser: String? = null,
    val os: String? = null,
    val referrer: String? = null,
    /** user | guest */
    val type: String = "guest",
    val visited_at: String? = null,
)

@Serializable
data class EventsResponse(
    val range: String? = null,
    val granularity: String? = null,
    val enabled: Boolean = true,
    val cards: EventsCards = EventsCards(),
    val series: EventsSeries = EventsSeries(),
    val byName: List<RankedItem> = emptyList(),
    val byCategory: List<RankedItem> = emptyList(),
    val recent: List<RecentEvent> = emptyList(),
)

@Serializable
data class EventsCards(
    val total: Int = 0,
    val totalChange: Double? = null,
    val unique: Int = 0,
    val people: Int = 0,
)

@Serializable
data class EventsSeries(
    val labels: List<String> = emptyList(),
    val events: List<Int> = emptyList(),
)

@Serializable
data class RecentEvent(
    val name: String? = null,
    val category: String? = null,
    val type: String = "guest",
    val country_code: String? = null,
    val device_type: String? = null,
    val occurred_at: String? = null,
)

// ---------------------------------------------------------------------------
// Support Team (api/admin/support-team) — só admins
// ---------------------------------------------------------------------------

@Serializable
data class SupportTeamResponse(
    val data: List<SupportTeamAgent> = emptyList(),
    val categories: List<String> = emptyList(),
)

@Serializable
data class SupportTeamAgentResponse(val data: SupportTeamAgent)

@Serializable
data class SupportTeamAgent(
    val id: JsonElement? = null,
    val email: String? = null,
    val account_name: String? = null,
    val avatar: String? = null,
    val role: String? = null,
    val display_name: String? = null,
    val title: String? = null,
    val is_active: Boolean = true,
    /** all | assigned */
    val scope: String = "all",
    val categories: List<String>? = null,
    val can_assign: Boolean = false,
    val can_delete: Boolean = false,
    val can_request_deletion: Boolean = false,
    val can_view_user_email: Boolean = false,
    val open_assigned: Int = 0,
) {
    val idString: String get() = id.idOrEmpty()
}

/** Corpo de criação/edição (o formulário envia sempre o perfil completo). */
@Serializable
data class SupportTeamAgentRequest(
    val email: String? = null,
    val display_name: String? = null,
    val title: String? = null,
    val is_active: Boolean = true,
    val scope: String = "all",
    val categories: List<String> = emptyList(),
    val can_assign: Boolean = false,
    val can_delete: Boolean = false,
    val can_request_deletion: Boolean = false,
    val can_view_user_email: Boolean = false,
)

@Serializable
data class SupportTeamUserSearch(val data: List<SupportTeamCandidate> = emptyList())

@Serializable
data class SupportTeamCandidate(
    val email: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val is_member: Boolean = false,
    val is_suspended: Boolean = false,
)

// ---------------------------------------------------------------------------
// FAQ / centro de ajuda (api/admin/faq) — só admins
// ---------------------------------------------------------------------------

@Serializable
data class FaqListResponse(
    val data: List<FaqEntry> = emptyList(),
    val categories: List<String> = emptyList(),
    val locales: List<String> = emptyList(),
    val ai: Boolean = false,
)

@Serializable
data class FaqEntryResponse(val data: FaqEntry)

@Serializable
data class FaqEntry(
    val id: JsonElement? = null,
    val category: String = "",
    /** locale → {question, answer}; o PHP envia [] quando está vazio. */
    val content: JsonElement? = null,
    val is_published: Boolean = false,
    val sort_order: Int = 0,
    val source: String? = null,
) {
    val idString: String get() = id.idOrEmpty()

    val contentMap: Map<String, FaqContent>
        get() = (content as? kotlinx.serialization.json.JsonObject)?.mapNotNull { (locale, value) ->
            val o = value as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            locale to FaqContent(
                question = (o["question"] as? JsonPrimitive)?.contentOrNull,
                answer = (o["answer"] as? JsonPrimitive)?.contentOrNull,
            )
        }?.toMap().orEmpty()
}

@Serializable
data class FaqContent(val question: String? = null, val answer: String? = null)

@Serializable
data class FaqRequest(
    val category: String,
    val content: Map<String, FaqContent>,
    val is_published: Boolean,
)

@Serializable
data class FaqTranslateRequest(val source: String)

@Serializable
data class FaqTranslateResponse(val content: JsonElement? = null) {
    val contentMap: Map<String, FaqContent> get() = FaqEntry(content = content).contentMap
}

@Serializable
data class FaqGenerateRequest(val locale: String)

// ---------------------------------------------------------------------------
// Pages (api/admin/pages; a chave é o slug)
// ---------------------------------------------------------------------------

@Serializable
data class PagesResponse(val data: List<PageEnvelope> = emptyList())

@Serializable
data class PageEnvelope(val data: PageData)

@Serializable
data class PageData(val attributes: PageAttributes = PageAttributes())

@Serializable
data class PageAttributes(
    val visibility: JsonElement? = null,
    val title: String? = null,
    val slug: String = "",
    val lang: String? = null,
    val slug_group: String? = null,
    val content: String? = null,
    val translations: List<PageSibling> = emptyList(),
) {
    /** O PHP guarda 0/1 ou bool. */
    val isVisible: Boolean
        get() = (visibility as? JsonPrimitive)?.contentOrNull.let { it == "1" || it == "true" }
}

@Serializable
data class PageSibling(val slug: String = "", val lang: String? = null)

@Serializable
data class CreatePageRequest(
    val title: String,
    val slug: String,
    val lang: String,
    val slug_group: String? = null,
    val content: String = "",
    val visibility: Boolean = false,
)

/** PATCH de um campo (make_single_input / $updateText da web). */
@Serializable
data class SingleFieldRequest(val name: String, val value: String)

@Serializable
data class PageTranslateRequest(val source: String)

@Serializable
data class PageTranslation(val title: String = "", val content: String = "")

// ---------------------------------------------------------------------------
// Languages (api/admin/languages)
// ---------------------------------------------------------------------------

@Serializable
data class LanguagesResponse(
    val data: List<LanguageEnvelope> = emptyList(),
    val meta: LanguagesMeta = LanguagesMeta(),
)

@Serializable
data class LanguagesMeta(
    val current_language: LanguageEnvelope? = null,
    /** chave → texto em inglês (referência) */
    val reference_translations: JsonElement? = null,
    val progress: Map<String, Int> = emptyMap(),
    val ai_available: Boolean = false,
)

@Serializable
data class LanguageEnvelope(val data: LanguageData)

@Serializable
data class LanguageData(
    val id: JsonElement? = null,
    val attributes: LanguageAttributes = LanguageAttributes(),
) {
    val idString: String get() = id.idOrEmpty()
}

@Serializable
data class LanguageAttributes(
    val name: String = "",
    val locale: String = "",
    /** chave → valor; [] quando vazio. */
    val translations: JsonElement? = null,
)

/** Objeto JSON de strings → Map (ignora o [] que o PHP envia quando vazio). */
fun JsonElement?.stringMap(): Map<String, String> =
    (this as? kotlinx.serialization.json.JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()

@Serializable
data class CreateLanguageRequest(val name: String, val locale: String)

@Serializable
data class AutoTranslateRequest(val offset: Int)

@Serializable
data class AutoTranslateResponse(
    val translated: Int = 0,
    val remaining: Int = 0,
    val next_offset: Int = 0,
    val translations: JsonElement? = null,
)

// ---------------------------------------------------------------------------
// Faturação (api/subscriptions/admin) — planos, subscrições, descontos
// ---------------------------------------------------------------------------

@Serializable
data class PlansResponse(
    val data: List<PlanEnvelope> = emptyList(),
    val meta: AdminPageMeta? = null,
)

@Serializable
data class PlanEnvelope(val data: AdminPlan)

@Serializable
data class AdminPlan(
    val id: JsonElement? = null,
    val attributes: AdminPlanAttributes = AdminPlanAttributes(),
) {
    val idString: String get() = id.idOrEmpty()
}

@Serializable
data class AdminPlanAttributes(
    val name: String? = null,
    /** active | archived */
    val status: String? = null,
    /** fixed | metered */
    val type: String? = null,
    val visible: JsonElement? = null,
    val currency: String? = null,
    val description: String? = null,
    val subscribers: Int = 0,
    val price: String? = null,
    val amount: Double? = null,
    val discount: PlanDiscount? = null,
    /** fixed: chave → valor; metered: chave → tiers. */
    val features: JsonElement? = null,
    val interval: String? = null,
    val created_at: String? = null,
) {
    val isVisible: Boolean
        get() = (visible as? JsonPrimitive)?.contentOrNull.let { it == "1" || it == "true" }
}

@Serializable
data class PlanDiscount(val original: String? = null, val final: String? = null, val label: String? = null)

@Serializable
data class CreateFixedPlanRequest(
    val type: String = "fixed",
    val name: String,
    val description: String?,
    val amount: Double,
    val currency: String,
    val interval: String,
    val features: Map<String, Int>,
)

@Serializable
data class UpdatePlanRequest(val name: String, val description: String?, val visible: Boolean)

@Serializable
data class AmountRequest(val amount: Double)

@Serializable
data class SubscriptionsResponse(
    val data: List<SubscriptionEnvelope> = emptyList(),
    val meta: AdminPageMeta? = null,
)

@Serializable
data class SubscriptionEnvelope(val data: AdminSubscription)

@Serializable
data class AdminSubscription(
    val id: JsonElement? = null,
    val attributes: AdminSubscriptionAttributes = AdminSubscriptionAttributes(),
    val relationships: AdminSubscriptionRelationships? = null,
) {
    val idString: String get() = id.idOrEmpty()
}

@Serializable
data class AdminSubscriptionAttributes(
    val is_cancelled: Boolean = false,
    val driver: String? = null,
    val name: String? = null,
    val status: String? = null,
    val created_at: String? = null,
    val renews_at: String? = null,
    val ends_at: String? = null,
    val is_past_due: Boolean = false,
    val is_suspended: Boolean = false,
    val days_left_to_pay: Int = 0,
)

@Serializable
data class AdminSubscriptionRelationships(
    val plan: PlanEnvelope? = null,
    val user: TransactionUserEnvelope? = null,
)

@Serializable
data class DiscountsResponse(val data: List<AdminDiscount> = emptyList())

@Serializable
data class AdminDiscount(
    val id: JsonElement? = null,
    val name: String = "",
    /** direct | code */
    val kind: String = "code",
    /** percent | fixed */
    val type: String = "percent",
    val value: Double = 0.0,
    val plan_id: String? = null,
    val plan_name: String? = null,
    val code: String? = null,
    val starts_at: String? = null,
    val ends_at: String? = null,
    val max_redemptions: Int? = null,
    val redeemed: Int = 0,
    val active: Boolean = true,
    val running: Boolean = false,
    val label: String? = null,
) {
    val idString: String get() = id.idOrEmpty()
}

@Serializable
data class DiscountRequest(
    val name: String,
    val kind: String,
    val type: String,
    val value: Double,
    val plan_id: String?,
    val code: String?,
    val starts_at: String?,
    val ends_at: String?,
    val max_redemptions: Int?,
    val active: Boolean,
)

@Serializable
data class GeneratedCode(val code: String = "")

/** POST api/admin/settings/payment-service(/test). */
@Serializable
data class PaymentServiceRequest(
    val service: String,
    val key: String,
    val secret: String,
    val webhook: String? = null,
)

// --- Definições do sistema ---

/** PATCH api/admin/settings com valor null (ex.: remover uma imagem). */
@Serializable
data class NullableFieldRequest(val name: String, val value: String?)

@Serializable
data class SettingImageResponse(val path: String? = null)

@Serializable
data class SocialServiceRequest(val service: String, val client_id: String, val client_secret: String)

@Serializable
data class MessageResponse(val message: String? = null)

@Serializable
data class AiOverview(
    val has_key: Boolean = false,
    val has_sdk: Boolean = false,
    val models: List<String> = emptyList(),
    val features: List<AiFeature> = emptyList(),
    val usage: List<AiUsageRow> = emptyList(),
)

@Serializable
data class AiFeature(
    val feature: String = "",
    /** Setting que liga/desliga (null = sempre ligada). */
    val toggle: String? = null,
    val model_setting: String? = null,
    val model: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class AiUsageRow(
    val feature: String = "",
    val model: String = "",
    val requests: Int = 0,
    val input_tokens: Long = 0,
    val output_tokens: Long = 0,
    val cost_usd: Double = 0.0,
)

// --- Denúncias de links (só admins) ---
@Serializable
data class AbuseReportListResponse(
    val data: List<AbuseReport> = emptyList(),
    val meta: AbuseReportMeta = AbuseReportMeta(),
    val ai: Boolean = false,
)

@Serializable
data class AbuseReportMeta(
    val current_page: Int = 1,
    val last_page: Int = 1,
    val total: Int = 0,
    val open: Int = 0,
)

@Serializable
data class AbuseReport(
    val id: String,
    val share_token: String = "",
    val link: String = "",
    val link_active: Boolean = false,
    val item_type: String? = null,
    val reason: String = "other",
    val message: String? = null,
    val reporter_email: String? = null,
    val reports_count: Int = 1,
    val status: String = "open",
    val owner: AbuseReportOwner? = null,
    val ai_category: String? = null,
    val ai_priority: String? = null,
    val ai_summary: String? = null,
    val created_at: String? = null,
)

@Serializable
data class AbuseReportOwner(val id: String, val email: String? = null, val name: String? = null)

// --- Antivírus (só admins) ---
@Serializable
data class AntivirusOverview(
    val enabled: Boolean = false,
    /** CLAMAV_ENABLED no .env; o interruptor só desliga por cima. */
    val configured: Boolean = false,
    val socket: String? = null,
    val version: String? = null,
    /** status → total; o PHP envia [] quando não há nenhum. */
    val counts: JsonElement? = null,
    val data: List<QuarantinedFile> = emptyList(),
    val meta: AbuseReportMeta = AbuseReportMeta(),
) {
    fun count(status: String): Int =
        ((counts as? kotlinx.serialization.json.JsonObject)?.get(status) as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
}

@Serializable
data class QuarantinedFile(
    val id: String,
    val name: String? = null,
    val filesize: Long = 0,
    val signature: String? = null,
    val scanned_at: String? = null,
    val owner: AbuseReportOwner? = null,
)
