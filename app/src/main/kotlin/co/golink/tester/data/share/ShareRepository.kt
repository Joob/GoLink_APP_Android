package co.golink.tester.data.share

import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.ShareInfo
import co.golink.tester.domain.browse.expireHoursToDays
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
        downloadLimit: Int?,
        singleView: Boolean,
        emails: List<String>?,
    ): Result<ShareInfo> = runCatching {
        val type = if (item is BrowseItem.Folder) "folder" else "file"
        // Visualização única só existe para ficheiros e exclui o limite de
        // downloads — é a mesma regra que o servidor aplica ao gravar.
        val viewLimit = if (singleView && type == "file") 1 else null
        val body = CreateShareRequest(
            id = item.id,
            type = type,
            isPassword = !password.isNullOrBlank(),
            password = password?.takeIf { it.isNotBlank() },
            permission = permission,
            // Backend conta em HORAS; a UI é em dias.
            expiration = expirationDays?.takeIf { it > 0 }?.let { it * 24 },
            download_limit = if (viewLimit != null) null else downloadLimit?.takeIf { it > 0 },
            view_limit = viewLimit,
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
        downloadLimit: Int?,
        singleView: Boolean,
    ): Result<ShareInfo> = runCatching {
        val viewLimit = if (singleView) 1 else null
        val body = UpdateShareRequest(
            protected = protected,
            protectedPasswordShow = false,
            password = password,
            permission = permission,
            expiration = expirationDays?.takeIf { it > 0 }?.let { it * 24 },
            download_limit = if (viewLimit != null) null else downloadLimit?.takeIf { it > 0 },
            view_limit = viewLimit,
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
        expireIn = expireHoursToDays(expire_in),
        downloadLimit = download_limit,
        downloadCount = download_count,
        viewLimit = view_limit,
        viewCount = view_count,
    )
}
