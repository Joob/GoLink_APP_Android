package co.golink.tester.data.share

import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.ShareInfo
import co.golink.tester.domain.share.CreateShareRequest
import co.golink.tester.domain.share.RevokeSharesRequest
import co.golink.tester.domain.share.ShareByEmailRequest
import co.golink.tester.domain.share.ShareResponseAttributes
import co.golink.tester.domain.share.UpdateShareRequest
import co.golink.tester.network.ShareApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareRepository @Inject constructor(
    private val api: ShareApi,
) {
    suspend fun create(
        item: BrowseItem,
        password: String?,
        permission: String?,
        expirationDays: Int?,
        emails: List<String>?,
    ): Result<ShareInfo> = runCatching {
        val type = if (item is BrowseItem.Folder) "folder" else "file"
        val body = CreateShareRequest(
            id = item.id,
            type = type,
            isPassword = !password.isNullOrBlank(),
            password = password?.takeIf { it.isNotBlank() },
            permission = permission,
            expiration = expirationDays,
            emails = emails?.filter { it.isNotBlank() },
        )
        val response = api.create(body)
        if (!response.isSuccessful) {
            val errBody = response.errorBody()?.string()?.take(300) ?: ""
            error("HTTP ${response.code()}: $errBody")
        }
        val attrs = response.body()?.data?.attributes ?: error("Resposta vazia")
        attrs.toShareInfo()
    }

    suspend fun update(
        token: String,
        protected: Boolean,
        password: String?,
        permission: String?,
        expirationDays: Int?,
    ): Result<ShareInfo> = runCatching {
        val body = UpdateShareRequest(
            protected = protected,
            protectedPasswordShow = false,
            password = password,
            permission = permission,
            expiration = expirationDays,
        )
        val response = api.update(token, body)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val attrs = response.body()?.data?.attributes ?: error("Resposta vazia")
        attrs.toShareInfo()
    }

    suspend fun revoke(token: String): Result<Unit> = runCatching {
        val response = api.revoke(token, RevokeSharesRequest(tokens = listOf(token)))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun sendByEmail(token: String, emails: List<String>): Result<Unit> = runCatching {
        val response = api.sendByEmail(token, ShareByEmailRequest(emails = emails))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun qrCode(token: String): Result<String> = runCatching {
        val response = api.qrCode(token)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.data?.svg ?: error("Sem QR no payload")
    }

    private fun ShareResponseAttributes.toShareInfo() = ShareInfo(
        token = token,
        link = link,
        protected = protected ?: false,
        permission = permission,
        expireIn = expire_in,
    )
}
