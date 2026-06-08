package co.golink.tester.data.user

import co.golink.tester.domain.user.User
import co.golink.tester.network.UserApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class UserRepository @Inject constructor(
    private val api: UserApi,
) {
    private val _me = MutableStateFlow<User?>(null)
    val me: StateFlow<User?> = _me.asStateFlow()

    suspend fun fetchMe(): Result<User> = runCatching {
        val response = api.me()
        if (!response.isSuccessful) throw UserFetchException(response.code(), "HTTP ${response.code()}")
        val body = response.body() ?: throw UserFetchException(0, "Resposta vazia")
        User.fromResponse(body).also { _me.value = it }
    }

    class UserFetchException(val statusCode: Int, message: String) : Exception(message)

    fun clear() { _me.value = null }
}
