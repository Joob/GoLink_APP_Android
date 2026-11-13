package co.golink.tester.data.user

import co.golink.tester.data.auth.TokenStore
import co.golink.tester.domain.user.DisplayPrefs
import co.golink.tester.domain.user.User
import co.golink.tester.domain.user.UserResponse
import co.golink.tester.network.UserApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class UserRepository @Inject constructor(
    private val api: UserApi,
    private val tokenStore: TokenStore,
    private val json: Json,
) {
    private val _me = MutableStateFlow<User?>(null)
    val me: StateFlow<User?> = _me.asStateFlow()

    /** Única fonte das preferências de datas: derivadas de [me], nunca guardadas à parte. */
    val displayPrefs: Flow<DisplayPrefs> = me.map(DisplayPrefs::from).distinctUntilChanged()

    suspend fun fetchMe(): Result<User> = runCatching {
        val token = tokenStore.token
        val response = api.me()
        if (!response.isSuccessful) {
            // Só o 403 do EnsureOtpVerified manda de volta ao OTP (não um 403 do WAF/Cloudflare).
            val otpRequired = response.code() == 403 &&
                runCatching { response.errorBody()?.string().orEmpty().contains("\"otp_required\":true") }.getOrDefault(false)
            throw UserFetchException(response.code(), "HTTP ${response.code()}", otpRequired)
        }
        val body = response.body() ?: throw UserFetchException(0, "Resposta vazia")
        User.fromResponse(body).also {
            _me.value = it
            if (token != null) {
                // JSON + AES-GCM das preferências cifradas: fora do thread de UI.
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { tokenStore.saveCachedUser(token, json.encodeToString(UserResponse.serializer(), body)) }
                }
            }
        }
    }

    /** Repõe o último utilizador guardado; null se não houver (ou já não for legível). */
    fun restore(): User? {
        val cached = tokenStore.cachedUser ?: return null
        return runCatching { User.fromResponse(json.decodeFromString(UserResponse.serializer(), cached)) }
            .getOrNull()
            ?.also {
                _me.value = it
            }
    }

    class UserFetchException(val statusCode: Int, message: String, val otpRequired: Boolean = false) : Exception(message)

    fun clear() {
        _me.value = null
    }
}
