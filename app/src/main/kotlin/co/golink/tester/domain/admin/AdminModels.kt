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
    val download: DashboardTraffic = DashboardTraffic(),
    val upload: DashboardTraffic = DashboardTraffic(),
)

@Serializable
data class DashboardTraffic(
    val total: String? = null,
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
    val cards: AnalyticsCards = AnalyticsCards(),
    val countries: List<RankedItem> = emptyList(),
    val devices: List<RankedItem> = emptyList(),
    val browsers: List<RankedItem> = emptyList(),
    val os: List<RankedItem> = emptyList(),
    val referrers: List<RankedItem> = emptyList(),
    val behavior: AnalyticsBehavior = AnalyticsBehavior(),
)

@Serializable
data class AnalyticsCards(
    val visitors: Int = 0,
    val visits: Int = 0,
    val visitorsChange: Double = 0.0,
    val visitsChange: Double = 0.0,
)

@Serializable
data class RankedItem(
    val label: String? = null,
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

// ---------------------------------------------------------------------------
// UI models
// ---------------------------------------------------------------------------

data class AdminUserItem(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val avatar: String?,
)

data class InviteItem(
    val id: String,
    val email: String,
    val status: String,
    val inviterName: String?,
    val createdAt: String?,
)
