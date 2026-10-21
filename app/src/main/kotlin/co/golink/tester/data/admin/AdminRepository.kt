package co.golink.tester.data.admin

import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import co.golink.tester.domain.admin.AdminDeleteConfirm
import co.golink.tester.domain.admin.AdminDeleteRequest
import co.golink.tester.domain.admin.AdminCodeRequest
import co.golink.tester.domain.admin.AdminEmailChangeRequest
import co.golink.tester.domain.admin.AdminPageMeta
import co.golink.tester.domain.admin.AdminUserCounts
import co.golink.tester.domain.admin.AdminUserItem
import co.golink.tester.domain.admin.AuditLogData
import co.golink.tester.domain.admin.CapacityAttributes
import co.golink.tester.domain.admin.ChangeCapacityRequest
import co.golink.tester.domain.admin.ChangeRoleRequest
import co.golink.tester.domain.admin.CreateUserRequest
import co.golink.tester.domain.admin.DeletedUserRow
import co.golink.tester.domain.admin.RoleAttributes
import co.golink.tester.domain.admin.UserStorageData
import co.golink.tester.domain.admin.AnalyticsResponse
import co.golink.tester.domain.admin.CreateInviteRequest
import co.golink.tester.domain.admin.DashboardResponse
import co.golink.tester.domain.admin.InviteItem
import co.golink.tester.domain.admin.SuspendUserRequest
import co.golink.tester.domain.user.User
import co.golink.tester.network.AdminApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

@Singleton
class AdminRepository @Inject constructor(
    private val api: AdminApi,
    private val json: Json,
) {
    suspend fun dashboard(days: Int = 45): Result<DashboardResponse> = runCatching {
        val response = api.dashboard(days)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body() ?: error("Resposta vazia")
    }

    suspend fun newbies(): Result<List<AdminUserItem>> = body { api.newbies() }.map { r -> r.data.map { it.toItem() } }

    suspend fun latestTransactions(): Result<List<co.golink.tester.domain.admin.TransactionData>> =
        body { api.latestTransactions() }.map { r -> r.data.map { it.data } }

    suspend fun encryptionMigration(page: Int = 1): Result<Pair<List<co.golink.tester.domain.admin.EncryptionMigrationRow>, Boolean>> =
        body { api.encryptionMigration(page) }.map { r -> r.data to r.meta.hasMore(page) }

    suspend fun realtime() = body { api.realtime() }

    suspend fun events(range: String) = body { api.events(range) }

    // --- Support Team ---
    suspend fun supportTeam() = body { api.supportTeam() }
    suspend fun searchTeamCandidates(q: String) = body { api.supportTeamSearch(q) }.map { it.data }
    suspend fun addAgent(r: co.golink.tester.domain.admin.SupportTeamAgentRequest) = body { api.addAgent(r) }.map { it.data }
    suspend fun updateAgent(id: String, r: co.golink.tester.domain.admin.SupportTeamAgentRequest) =
        body { api.updateAgent(id, r.copy(email = null)) }.map { it.data }
    suspend fun removeAgent(id: String): Result<Unit> = unit { api.removeAgent(id) }

    // --- FAQ ---
    suspend fun faqs() = body { api.faqs() }
    suspend fun saveFaq(id: String?, r: co.golink.tester.domain.admin.FaqRequest) =
        body { if (id == null) api.createFaq(r) else api.updateFaq(id, r) }.map { it.data }
    suspend fun deleteFaq(id: String): Result<Unit> = unit { api.deleteFaq(id) }
    suspend fun translateFaq(id: String, source: String) =
        body { api.translateFaq(id, co.golink.tester.domain.admin.FaqTranslateRequest(source)) }.map { it.contentMap }
    suspend fun generateFaqs(locale: String): Result<Unit> = runCatching {
        val response = api.generateFaqs(co.golink.tester.domain.admin.FaqGenerateRequest(locale))
        check(response.isSuccessful) { serverMessage(response) }
    }

    // --- Pages ---
    suspend fun pages() = body { api.pages() }.map { r -> r.data.map { it.data.attributes } }
    suspend fun page(slug: String) = body { api.page(slug) }.map { it.data.attributes }
    suspend fun createPage(r: co.golink.tester.domain.admin.CreatePageRequest) = body { api.createPage(r) }.map { it.data.attributes }
    suspend fun updatePage(slug: String, field: String, value: String): Result<Unit> =
        unit { api.updatePage(slug, co.golink.tester.domain.admin.SingleFieldRequest(field, value)) }
    suspend fun translatePage(slug: String, source: String) = body { api.translatePage(slug, co.golink.tester.domain.admin.PageTranslateRequest(source)) }

    // --- Languages ---
    suspend fun languages() = body { api.languages() }
    suspend fun language(id: String) = body { api.language(id) }.map { it.data }
    suspend fun createLanguage(name: String, locale: String) =
        body { api.createLanguage(co.golink.tester.domain.admin.CreateLanguageRequest(name.trim(), locale.trim().lowercase())) }.map { it.data }
    suspend fun renameLanguage(id: String, name: String): Result<Unit> =
        unit { api.updateLanguage(id, co.golink.tester.domain.admin.SingleFieldRequest("name", name.trim())) }
    suspend fun deleteLanguage(id: String): Result<Unit> = unit { api.deleteLanguage(id) }
    suspend fun updateLanguageString(id: String, key: String, value: String): Result<Unit> =
        unit { api.updateLanguageString(id, co.golink.tester.domain.admin.SingleFieldRequest(key, value)) }
    suspend fun autoTranslate(id: String, offset: Int) = body { api.autoTranslate(id, co.golink.tester.domain.admin.AutoTranslateRequest(offset)) }
    suspend fun updateSetting(name: String, value: String): Result<Unit> =
        unit { api.updateSetting(co.golink.tester.domain.admin.SingleFieldRequest(name, value)) }

    // --- Faturação ---
    suspend fun plans(page: Int = 1) = body { api.plans(page) }.map { r -> r.data.map { it.data } to r.meta.hasMore(page) }
    suspend fun plan(id: String) = body { api.plan(id) }.map { it.data }
    suspend fun createFixedPlan(r: co.golink.tester.domain.admin.CreateFixedPlanRequest) = body { api.createFixedPlan(r) }.map { it.data }
    suspend fun updatePlan(id: String, r: co.golink.tester.domain.admin.UpdatePlanRequest) = body { api.updatePlan(id, r) }.map { it.data }
    suspend fun updatePlanPrice(id: String, amount: Double) = body { api.updatePlanPrice(id, co.golink.tester.domain.admin.AmountRequest(amount)) }.map { it.data }
    suspend fun updatePlanFeatures(id: String, features: Map<String, Int>): Result<Unit> = unit { api.updatePlanFeatures(id, features) }
    suspend fun deletePlan(id: String): Result<Unit> = unit { api.deletePlan(id) }
    suspend fun synchronizePlans(): Result<Unit> = unit { api.synchronizePlans() }
    suspend fun planSubscribers(id: String, page: Int = 1) =
        body { api.planSubscribers(id, page) }.map { r -> r.data.map { it.data } to r.meta.hasMore(page) }
    suspend fun subscriptions(page: Int = 1) =
        body { api.subscriptions(page) }.map { r -> r.data.map { it.data } to r.meta.hasMore(page) }
    suspend fun deleteSubscription(id: String): Result<Unit> = unit { api.deleteSubscription(id) }
    suspend fun discounts() = body { api.discounts() }.map { it.data }
    suspend fun generateDiscountCode() = body { api.generateDiscountCode() }.map { it.code }
    suspend fun saveDiscount(id: String?, r: co.golink.tester.domain.admin.DiscountRequest): Result<Unit> =
        unit { if (id == null) api.createDiscount(r) else api.updateDiscount(id, r) }
    suspend fun deleteDiscount(id: String): Result<Unit> = unit { api.deleteDiscount(id) }

    /** null quando o utilizador não tem subscrição (o servidor responde 404). */
    suspend fun userSubscription(id: String): Result<co.golink.tester.domain.admin.AdminSubscription?> = runCatching {
        val response = api.userSubscription(id)
        if (response.code() == 404) return@runCatching null
        check(response.isSuccessful) { serverMessage(response) }
        response.body()?.data
    }
    suspend fun creditUser(id: String, amount: Double): Result<Unit> = unit { api.creditUser(id, co.golink.tester.domain.admin.AmountRequest(amount)) }
    suspend fun transactions(page: Int = 1) =
        body { api.transactions(page) }.map { r -> r.data.map { it.data } to r.meta.hasMore(page) }
    suspend fun userTransactions(id: String, page: Int = 1) =
        body { api.userTransactions(id, page) }.map { r -> r.data.map { it.data } to r.meta.hasMore(page) }
    suspend fun refundTransaction(id: String): Result<Unit> = unit { api.refundTransaction(id) }
    suspend fun deleteTransaction(id: String): Result<Unit> = unit { api.deleteTransaction(id) }

    /** Valores das settings como texto (o servidor pode mandar números/booleanos). */
    suspend fun settings(vararg columns: String): Result<Map<String, String?>> =
        body { api.settings(columns.joinToString("|")) }.map { m ->
            m.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.let { p -> if (p is kotlinx.serialization.json.JsonNull) null else p.content } }
        }
    suspend fun clearSetting(name: String): Result<Unit> = unit { api.updateSettingNullable(co.golink.tester.domain.admin.NullableFieldRequest(name, null)) }
    suspend fun uploadSettingImage(name: String, bytes: ByteArray, mime: String, fileName: String): Result<String?> = body {
        val text = "text/plain".toMediaTypeOrNull()
        api.uploadSettingImage(
            name.toRequestBody(text),
            okhttp3.MultipartBody.Part.createFormData(name, fileName, bytes.toRequestBody(mime.toMediaTypeOrNull())),
        )
    }.map { it.path }
    suspend fun flushCache(): Result<Unit> = unit { api.flushCache() }
    suspend fun storeSocialService(r: co.golink.tester.domain.admin.SocialServiceRequest): Result<Unit> = unit { api.storeSocialService(r) }
    suspend fun envCredentials() = body { api.envCredentials() }
    suspend fun aiOverview() = body { api.aiOverview() }
    suspend fun serverStatus() = body { api.serverStatus() }
    suspend fun forceLogoutAll() = body { api.forceLogoutAll() }.map { it.message }
    suspend fun storePaymentService(r: co.golink.tester.domain.admin.PaymentServiceRequest): Result<Unit> = unit { api.storePaymentService(r) }
    suspend fun testPaymentService(r: co.golink.tester.domain.admin.PaymentServiceRequest): Result<Unit> = unit { api.testPaymentService(r) }

    private suspend fun <T> body(call: suspend () -> Response<T>): Result<T> = runCatching {
        val response = call()
        check(response.isSuccessful) { serverMessage(response) }
        response.body() ?: error("Resposta vazia")
    }

    suspend fun analytics(range: String = "7d"): Result<AnalyticsResponse> = runCatching {
        val response = api.analytics(range)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body() ?: error("Resposta vazia")
    }

    /** Página de utilizadores + totais dos separadores. [status]: null | active | suspended. */
    suspend fun users(
        page: Int = 1,
        status: String? = null,
        search: String? = null,
    ): Result<UsersPage> = runCatching {
        val response = api.users(page, status, search?.trim()?.ifBlank { null })
        check(response.isSuccessful) { serverMessage(response) }
        val body = response.body() ?: error("Resposta vazia")
        val items = body.data.map { it.toItem() }
        UsersPage(items, body.meta.hasMore(page), body.counts)
    }

    suspend fun deletedUsers(page: Int = 1, search: String? = null): Result<Pair<List<DeletedUserRow>, Boolean>> = runCatching {
        val response = api.deletedUsers(page, search?.trim()?.ifBlank { null })
        check(response.isSuccessful) { serverMessage(response) }
        val body = response.body() ?: error("Resposta vazia")
        body.data to body.meta.hasMore(page)
    }

    suspend fun cancelScheduledDeletion(id: String): Result<Unit> = unit { api.cancelScheduledDeletion(id) }

    suspend fun createUser(request: CreateUserRequest): Result<User> = runCatching {
        val response = api.createUser(request)
        check(response.isSuccessful) { serverMessage(response) }
        User.fromResponse(response.body() ?: error("Resposta vazia"))
    }

    suspend fun user(id: String): Result<User> = runCatching {
        val response = api.user(id)
        check(response.isSuccessful) { serverMessage(response) }
        User.fromResponse(response.body() ?: error("Resposta vazia"))
    }

    suspend fun changeRole(id: String, role: String): Result<Unit> =
        unit { api.changeRole(id, ChangeRoleRequest(RoleAttributes(role))) }

    suspend fun userStorage(id: String): Result<UserStorageData> = runCatching {
        val response = api.userStorage(id)
        check(response.isSuccessful) { serverMessage(response) }
        response.body()?.data ?: error("Resposta vazia")
    }

    /** [gigabytes]: nova quota em GB (o backend aceita 1–9 dígitos). */
    suspend fun changeCapacity(id: String, gigabytes: Int): Result<UserStorageData> = runCatching {
        val response = api.changeCapacity(id, ChangeCapacityRequest(CapacityAttributes(gigabytes)))
        check(response.isSuccessful) { serverMessage(response) }
        response.body()?.data ?: error("Resposta vazia")
    }

    suspend fun resetPassword(id: String): Result<Unit> = unit { api.resetPassword(id) }

    /** Passo 1: o código vai para o email do admin. */
    suspend fun requestEmailChange(id: String, email: String, password: String): Result<Unit> =
        unit { api.emailChangeRequest(id, AdminEmailChangeRequest(email.trim(), password)) }

    suspend fun confirmEmailChange(id: String, code: String): Result<Unit> =
        unit { api.emailChangeConfirm(id, AdminCodeRequest(code)) }

    /** Passo 1: nome do utilizador + password do admin → código para o email do admin. */
    suspend fun requestUserDeletion(id: String, name: String, password: String): Result<Unit> =
        unit { api.deleteUserRequest(id, AdminDeleteRequest(name, password)) }

    suspend fun deleteUser(id: String, name: String, code: String): Result<Unit> =
        unit { api.deleteUser(id, AdminDeleteConfirm(name, code)) }

    suspend fun auditLogs(page: Int = 1, action: String? = null, onlyFailed: Boolean = false): Result<Pair<List<AuditLogData>, Boolean>> = runCatching {
        val response = api.auditLogs(page, action?.trim()?.ifBlank { null }, if (onlyFailed) 1 else null)
        check(response.isSuccessful) { serverMessage(response) }
        val body = response.body() ?: error("Resposta vazia")
        body.data.map { it.data } to body.meta.hasMore(page)
    }

    suspend fun resendInvite(id: String): Result<Unit> = unit { api.resendInvite(id) }

    // Pedidos sem corpo útil na resposta. Alguns controladores respondem 202
    // com {type: error} (ex.: subscrição ativa) — tratado como erro.
    private suspend fun unit(call: suspend () -> Response<*>): Result<Unit> = runCatching {
        val response = call()
        check(response.isSuccessful && response.code() != 202) { serverMessage(response) }
    }

    private fun co.golink.tester.domain.admin.AdminUserListEnvelope.toItem(): AdminUserItem {
        val a = data.attributes
        return AdminUserItem(
            id = data.idString,
            name = a.name ?: a.email.orEmpty(),
            email = a.email.orEmpty(),
            role = a.role ?: "user",
            avatar = a.avatar,
            isSuspended = a.is_suspended,
            suspendedUntil = a.suspended_until,
            suspendedReason = a.suspended_reason,
            storage = a.storage?.let { s -> listOfNotNull(s.used_formatted, s.capacity_formatted).joinToString(" / ") },
            plan = a.plan,
            lastLoginAt = a.last_login_at,
            deletionScheduledFor = a.deletion_scheduled_for,
        )
    }

    private fun AdminPageMeta?.hasMore(page: Int): Boolean =
        (this?.current_page ?: page) < (this?.last_page ?: page)

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

    /** [unit]/[value] a null = suspensão indefinida. */
    suspend fun suspendUser(
        id: String,
        unit: String?,
        value: Int?,
        reason: String?,
    ): Result<Unit> = runCatching {
        val response = api.suspendUser(
            id,
            SuspendUserRequest(unit = unit, value = value, reason = reason?.takeIf { it.isNotBlank() }),
        )
        // O servidor recusa suspender admins (403) e o próprio (406) com uma
        // mensagem explicativa — mostrá-la em vez de um código HTTP.
        check(response.isSuccessful) { serverMessage(response) }
    }

    suspend fun unsuspendUser(id: String): Result<Unit> = runCatching {
        val response = api.unsuspendUser(id)
        check(response.isSuccessful) { serverMessage(response) }
    }

    private fun serverMessage(response: Response<*>): String {
        val raw = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
        // Respostas 202 de "erro" vêm no corpo normal, não no errorBody.
        val text = raw.ifBlank { runCatching { (response.body() as? okhttp3.ResponseBody)?.string().orEmpty() }.getOrDefault("") }
        val parsed = runCatching {
            json.decodeFromString(JsonObject.serializer(), text)["message"]?.jsonPrimitive?.content
        }.getOrNull()
        return parsed ?: "HTTP ${response.code()}"
    }
}

data class UsersPage(
    val items: List<AdminUserItem>,
    val hasMore: Boolean,
    val counts: AdminUserCounts?,
)
