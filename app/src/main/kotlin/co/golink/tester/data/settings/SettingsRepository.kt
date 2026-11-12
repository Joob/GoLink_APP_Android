package co.golink.tester.data.settings

import co.golink.tester.domain.browse.toItem
import co.golink.tester.domain.settings.AccessToken
import co.golink.tester.domain.settings.CreateTokenRequest
import co.golink.tester.domain.settings.SecurityEventData
import co.golink.tester.domain.settings.SessionItem
import co.golink.tester.domain.settings.StorageUsage
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
    private val json: kotlinx.serialization.json.Json,
    private val keys: co.golink.tester.data.encryption.E2EKeyManager,
    private val errorParser: co.golink.tester.data.auth.ApiErrorParser,
) {
    /** Totais, Lixo, backups e maiores ficheiros (nomes decifrados quando E2E). */
    suspend fun storageInsights(): Result<co.golink.tester.domain.settings.StorageInsights> = netCatching {
        val response = api.storageInsights()
        check(response.isSuccessful) { validationMessage(response) }
        val d = response.body()?.data ?: error("Resposta vazia")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val largest = d.largest.mapNotNull { env ->
                val e = env.data
                val plain = keys.openNameOrNull(e.attributes.name_encrypted)
                val dec = if (plain != null) e.copy(attributes = e.attributes.copy(name = plain)) else e
                dec.toItem() as? co.golink.tester.domain.browse.BrowseItem.File
            }
            co.golink.tester.domain.settings.StorageInsights(
                filesCount = d.files_count,
                foldersCount = d.folders_count,
                encryptedFilesCount = d.encrypted_files_count,
                sharesCount = d.shares_count,
                trashCount = d.trash_count,
                trashBytes = d.trash_bytes,
                backupCount = d.backup_count,
                backupBytes = d.backup_bytes,
                largest = largest,
            )
        }
    }

    suspend fun storage(): Result<StorageUsage> = netCatching {
        val response = api.storage()
        check(response.isSuccessful) { validationMessage(response) }
        StorageUsage.fromResponse(response.body() ?: error("Resposta vazia"))
    }

    suspend fun sessions(): Result<co.golink.tester.domain.settings.SessionsResponse> = netCatching {
        val response = api.sessions()
        check(response.isSuccessful) { validationMessage(response) }
        response.body() ?: error("Resposta vazia")
    }

    suspend fun confirmSession(id: String): Result<Unit> = netCatching {
        val response = api.confirmSession(id)
        check(response.isSuccessful) { validationMessage(response) }
    }

    suspend fun revokeInactiveSessions(): Result<Unit> = netCatching {
        val response = api.revokeInactiveSessions()
        check(response.isSuccessful) { validationMessage(response) }
    }

    suspend fun setSessionAutoSignout(days: Int?): Result<Unit> = netCatching {
        val response = api.updateSessionSettings(co.golink.tester.domain.settings.SessionAutoSignoutRequest(days))
        check(response.isSuccessful) { validationMessage(response) }
    }

    suspend fun revokeConnection(id: Long): Result<Unit> = netCatching {
        val response = api.revokeConnection(id)
        check(response.isSuccessful) { validationMessage(response) }
    }

    suspend fun sessionsCount(): Result<Int> = netCatching {
        val response = api.sessionsCount()
        check(response.isSuccessful) { validationMessage(response) }
        response.body()?.count ?: error("Resposta vazia")
    }

    /**
     * Histórico de segurança da conta, já achatado (o envelope da API é
     * aninhado) e com a indicação de haver mais páginas.
     */
    suspend fun securityEvents(page: Int = 1): Result<SecurityEventsPage> = netCatching {
        val response = api.securityEvents(page)
        check(response.isSuccessful) { validationMessage(response) }

        val body = response.body()
        val items = body?.data.orEmpty().map { it.data }
        val current = body?.meta?.current_page ?: page
        val last = body?.meta?.last_page ?: current

        SecurityEventsPage(items = items, hasMore = current < last)
    }

    suspend fun revokeSession(id: String): Result<Unit> = netCatching {
        val response = api.revokeSession(id)
        check(response.isSuccessful) { validationMessage(response) }
    }

    suspend fun revokeAllSessions(): Result<Unit> = netCatching {
        val response = api.revokeAllSessions()
        check(response.isSuccessful) { validationMessage(response) }
    }

    suspend fun updatePassword(current: String, newPassword: String, passkeyToken: String? = null): Result<Unit> = netCatching {
        val response = api.updatePassword(
            UpdatePasswordRequest(
                current = current,
                password = newPassword,
                password_confirmation = newPassword,
                passkey_token = passkeyToken,
            ),
        )
        check(response.isSuccessful) { validationMessage(response) }
    }

    // Passo 1: password actual + novo email → servidor envia código ao NOVO email.
    // Devolve a validade do código em segundos.
    suspend fun requestEmailChange(email: String, password: String, passkeyToken: String? = null): Result<Int> = netCatching {
        val response = api.requestEmailChange(
            co.golink.tester.domain.settings.EmailChangeRequest(email = email.trim(), password = password, passkey_token = passkeyToken),
        )
        check(response.isSuccessful) { serverMessage(response) }
        response.body()?.expires_in ?: 120
    }

    // Passo 2: código → email alterado. Devolve o novo email.
    suspend fun confirmEmailChange(code: String): Result<String> = netCatching {
        val response = api.confirmEmailChange(co.golink.tester.domain.settings.EmailChangeConfirmRequest(code))
        check(response.isSuccessful) { serverMessage(response) }
        response.body()?.email.orEmpty()
    }

    private fun serverMessage(response: retrofit2.Response<*>): String = validationMessage(response)

    suspend fun updateProfileField(name: String, value: String): Result<Unit> = netCatching {
        val response = api.updateProfileField(UpdateProfileFieldRequest(name = name, value = value))
        check(response.isSuccessful) { validationMessage(response) }
    }

    // 422 → primeira mensagem do campo; resto → mensagem do servidor ou genérica
    // (traduzida quando é uma mensagem conhecida), nunca "HTTP 500".
    private fun validationMessage(response: retrofit2.Response<*>): String {
        val error = errorParser.parse(response)
        val msg = (error as? co.golink.tester.domain.auth.AuthError.Validation)
            ?.fields?.values?.flatten()?.firstOrNull()
            ?: error.message.orEmpty()
        return co.golink.tester.ui.i18n.ServerMessages.localize(msg)?.takeIf { it.isNotBlank() } ?: msg
    }

    /** runCatching sem engolir o cancelamento; falhas de rede passam a uma frase traduzida. */
    private inline fun <T> netCatching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        Result.failure(java.io.IOException(co.golink.tester.ui.i18n.I18n.t("Sem ligação ao servidor. Tenta novamente."), e))
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Envia o avatar recortado (JPEG) para POST api/user/avatar (campo "avatar").
    suspend fun updateAvatar(jpeg: ByteArray): Result<Unit> = netCatching {
        val body = jpeg.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = okhttp3.MultipartBody.Part.createFormData("avatar", "avatar.jpg", body)
        val response = api.updateAvatar(part)
        check(response.isSuccessful) { validationMessage(response) }
    }

    suspend fun listTokens(): Result<List<AccessToken>> = netCatching {
        val response = api.listTokens()
        check(response.isSuccessful) { validationMessage(response) }
        response.body().orEmpty()
    }

    /** O servidor exige a password atual ou um passkey_token (purpose create_token). */
    suspend fun createToken(name: String, password: String?, passkeyToken: String?): Result<String> = netCatching {
        val response = api.createToken(
            CreateTokenRequest(
                name = name,
                password = password?.takeIf { passkeyToken == null && it.isNotEmpty() },
                passkey_token = passkeyToken,
            ),
        )
        check(response.isSuccessful) { validationMessage(response) }
        response.body()?.plainTextToken ?: error("Token não retornado")
    }

    suspend fun deleteToken(id: Long): Result<Unit> = netCatching {
        val response = api.deleteToken(id)
        check(response.isSuccessful) { validationMessage(response) }
    }

    // ── 2FA (TOTP) ─────────────────────────────────────────────────────
    private fun stepUp(password: String?, passkeyToken: String?) =
        co.golink.tester.domain.settings.TwoFactorStepUp(
            password = password?.takeIf { passkeyToken == null && it.isNotEmpty() },
            passkey_token = passkeyToken,
        )

    suspend fun enableTwoFactor(password: String?, passkeyToken: String?): Result<co.golink.tester.domain.settings.TwoFactorSetupResponse> = netCatching {
        val response = api.enableTwoFactor(stepUp(password, passkeyToken))
        check(response.isSuccessful) { validationMessage(response) }
        response.body() ?: error("Resposta vazia")
    }

    suspend fun confirmTwoFactor(code: String): Result<List<String>> = netCatching {
        val response = api.confirmTwoFactor(co.golink.tester.domain.settings.TwoFactorCode(code))
        check(response.isSuccessful) { validationMessage(response) }
        response.body()?.recovery_codes.orEmpty()
    }

    suspend fun disableTwoFactor(password: String?, passkeyToken: String?): Result<Unit> = netCatching {
        val response = api.disableTwoFactor(stepUp(password, passkeyToken))
        check(response.isSuccessful) { validationMessage(response) }
    }

    suspend fun recoveryCodes(password: String?, passkeyToken: String?, regenerate: Boolean): Result<List<String>> = netCatching {
        val body = stepUp(password, passkeyToken)
        val response = if (regenerate) api.regenerateRecoveryCodes(body) else api.recoveryCodes(body)
        check(response.isSuccessful) { validationMessage(response) }
        response.body()?.recovery_codes.orEmpty()
    }
}
