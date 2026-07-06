package co.golink.tester.data.admin

import co.golink.tester.domain.admin.AdminUserItem
import co.golink.tester.domain.admin.AnalyticsResponse
import co.golink.tester.domain.admin.CreateInviteRequest
import co.golink.tester.domain.admin.DashboardResponse
import co.golink.tester.domain.admin.InviteItem
import co.golink.tester.domain.user.User
import co.golink.tester.network.AdminApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val api: AdminApi,
) {
    suspend fun dashboard(): Result<DashboardResponse> = runCatching {
        val response = api.dashboard()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body() ?: error("Resposta vazia")
    }

    suspend fun analytics(): Result<AnalyticsResponse> = runCatching {
        val response = api.analytics()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body() ?: error("Resposta vazia")
    }

    suspend fun users(page: Int = 1): Result<Pair<List<AdminUserItem>, Boolean>> = runCatching {
        val response = api.users(page)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        val items = body.data.map { envelope ->
            val user = User.fromResponse(envelope)
            AdminUserItem(
                id = user.id,
                name = user.name,
                email = user.email,
                role = user.role,
                avatar = user.avatar,
            )
        }
        val hasMore = (body.meta?.current_page ?: page) < (body.meta?.last_page ?: page)
        items to hasMore
    }

    suspend fun inviteRegisters(page: Int = 1): Result<Pair<List<InviteItem>, Boolean>> = runCatching {
        val response = api.inviteRegisters(page)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        val items = body.data.map { envelope ->
            val a = envelope.data.attributes
            InviteItem(
                id = envelope.data.idString,
                email = a.email.orEmpty(),
                status = a.status.orEmpty(),
                inviterName = envelope.data.relationships?.inviter?.data?.attributes?.name,
                createdAt = a.created_at,
            )
        }
        val hasMore = (body.meta?.current_page ?: page) < (body.meta?.last_page ?: page)
        items to hasMore
    }

    suspend fun createInvite(email: String): Result<Unit> = runCatching {
        val response = api.createInvite(CreateInviteRequest(emails = listOf(email)))
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
    }

    suspend fun deleteInvite(id: String): Result<Unit> = runCatching {
        val response = api.deleteInvite(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }
}
