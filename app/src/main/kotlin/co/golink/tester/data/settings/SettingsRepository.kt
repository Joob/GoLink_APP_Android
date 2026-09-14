package co.golink.tester.data.settings

import co.golink.tester.domain.settings.AccessToken
import co.golink.tester.domain.settings.CreateTokenRequest
import co.golink.tester.domain.settings.SecurityEventData
import co.golink.tester.domain.settings.SessionItem
import co.golink.tester.domain.settings.StorageUsage
import co.golink.tester.domain.settings.TransactionItem
import co.golink.tester.domain.settings.UpdatePasswordRequest
import co.golink.tester.domain.settings.UpdateProfileFieldRequest
import co.golink.tester.network.SettingsApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Uma página do histórico de segurança, já achatada para a UI. */
data class SecurityEventsPage(
    val items: List<SecurityEventData>,
    val hasMore: Boolean,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val api: SettingsApi,
) {
    suspend fun storage(): Result<StorageUsage> = runCatching {
        val response = api.storage()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        StorageUsage.fromResponse(response.body() ?: error("Resposta vazia"))
    }

    suspend fun sessions(): Result<List<SessionItem>> = runCatching {
        val response = api.sessions()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.data.orEmpty()
    }

    /**
     * Histórico de segurança da conta, já achatado (o envelope da API é
     * aninhado) e com a indicação de haver mais páginas.
     */
    suspend fun securityEvents(page: Int = 1): Result<SecurityEventsPage> = runCatching {
        val response = api.securityEvents(page)
        check(response.isSuccessful) { "HTTP ${response.code()}" }

        val body = response.body()
        val items = body?.data.orEmpty().map { it.data }
        val current = body?.meta?.current_page ?: page
        val last = body?.meta?.last_page ?: current

        SecurityEventsPage(items = items, hasMore = current < last)
    }

    suspend fun revokeSession(id: String): Result<Unit> = runCatching {
        val response = api.revokeSession(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun revokeAllSessions(): Result<Unit> = runCatching {
        val response = api.revokeAllSessions()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun updatePassword(current: String, newPassword: String): Result<Unit> = runCatching {
        val response = api.updatePassword(
            UpdatePasswordRequest(
                current = current,
                password = newPassword,
                password_confirmation = newPassword,
            ),
        )
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun updateProfileField(name: String, value: String): Result<Unit> = runCatching {
        val response = api.updateProfileField(UpdateProfileFieldRequest(name = name, value = value))
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
    }

    // Envia o avatar recortado (JPEG) para POST api/user/avatar (campo "avatar").
    suspend fun updateAvatar(jpeg: ByteArray): Result<Unit> = runCatching {
        val body = jpeg.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = okhttp3.MultipartBody.Part.createFormData("avatar", "avatar.jpg", body)
        val response = api.updateAvatar(part)
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
    }

    suspend fun listTokens(): Result<List<AccessToken>> = runCatching {
        val response = api.listTokens()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body().orEmpty()
    }

    suspend fun createToken(name: String): Result<String> = runCatching {
        val response = api.createToken(CreateTokenRequest(name = name))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.plainTextToken ?: error("Token não retornado")
    }

    suspend fun deleteToken(id: Long): Result<Unit> = runCatching {
        val response = api.deleteToken(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun transactions(): Result<List<TransactionItem>> = runCatching {
        val response = api.transactions()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.data.orEmpty().map { envelope ->
            val a = envelope.data.attributes
            TransactionItem(
                id = envelope.data.id,
                type = a.type,
                status = a.status,
                note = a.note,
                price = a.price,
                driver = a.driver,
                createdAt = a.created_at,
            )
        }
    }
}
