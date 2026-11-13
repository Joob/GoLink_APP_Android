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
import co.golink.tester.ui.i18n.tr
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
        expirationMinutes: Int?,
        downloadLimit: Int?,
        viewLimit: Int?,
        emails: List<String>?,
    ): Result<ShareInfo> = runCatching {
        val type = if (item is BrowseItem.Folder) "folder" else "file"
        // Limite de visualizações só existe para ficheiros e exclui o limite
        // de downloads — é a mesma regra que o servidor aplica ao gravar.
        val views = viewLimit?.takeIf { it > 0 && type == "file" }
        val body = CreateShareRequest(
            id = item.id,
            type = type,
            isPassword = !password.isNullOrBlank(),
            password = password?.takeIf { it.isNotBlank() },
            permission = permission,
            expiration_minutes = expirationMinutes?.takeIf { it > 0 },
            download_limit = if (views != null) null else downloadLimit?.takeIf { it > 0 },
            view_limit = views,
            emails = emails?.filter { it.isNotBlank() },
        )
        val response = api.create(body)
        checkShareResponse(response)
        val attrs = response.body()?.data?.attributes ?: error("Resposta vazia")
        attrs.toShareInfo()
    }

    suspend fun update(
        token: String,
        protected: Boolean,
        password: String?,
        permission: String?,
        expirationMinutes: Int?,
        downloadLimit: Int?,
        viewLimit: Int?,
        passwordTipAllowed: Boolean = false,
        passwordTip: String? = null,
    ): Result<ShareInfo> = runCatching {
        val views = viewLimit?.takeIf { it > 0 }
        val body = UpdateShareRequest(
            protected = protected,
            protectedPasswordShow = protected && passwordTipAllowed,
            passwordShow = passwordTip.takeIf { protected && passwordTipAllowed },
            password = password,
            permission = permission,
            expiration_minutes = expirationMinutes?.takeIf { it > 0 },
            download_limit = if (views != null) null else downloadLimit?.takeIf { it > 0 },
            view_limit = views,
        )
        val response = api.update(token, body)
        checkShareResponse(response)
        val attrs = response.body()?.data?.attributes ?: error("Resposta vazia")
        attrs.toShareInfo()
    }

    /**
     * Estado atual da partilha — as visualizações/downloads são gastos por quem
     * abre o link, fora da app. null = já não existe (o servidor apaga-a depois
     * de gasta a última visualização).
     */
    suspend fun get(token: String): Result<ShareInfo?> = runCatching {
        val response = api.show(token)
        if (response.code() == 404) return@runCatching null
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.data?.attributes?.toShareInfo()
    }

    suspend fun revoke(token: String): Result<Unit> = runCatching {
        val response = api.revoke(token, RevokeSharesRequest(tokens = listOf(token)))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun sendByEmail(token: String, emails: List<String>): Result<Unit> = runCatching {
        require(emails.size <= MAX_EMAILS) { "No máximo %d destinatários por envio".tr().format(MAX_EMAILS) }
        val response = api.sendByEmail(token, ShareByEmailRequest(emails = emails))
        checkShareResponse(response)
    }

    suspend fun qrCode(token: String): Result<String> = runCatching {
        val response = api.qrCode(token)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.data?.svg ?: error("Sem QR no payload")
    }

    /**
     * Erro legível em vez de "HTTP 4xx: {json}": 429 (máx. 20 envios por hora), a mensagem
     * do campo nas 422 (password > 72, dica > 40, mais de 10 emails) e a do servidor nos 403/404.
     */
    private fun checkShareResponse(response: retrofit2.Response<*>) {
        if (response.isSuccessful) return
        val code = response.code()
        if (code == 429) error("Demasiados envios, tenta daqui a pouco".tr())
        val raw = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
        val body = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject }.getOrNull()
        val errors = body?.get("errors") as? kotlinx.serialization.json.JsonObject
        val first = (errors?.values?.firstOrNull() as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
        val message = ((first ?: body?.get("message")) as? kotlinx.serialization.json.JsonPrimitive)?.content
        error(co.golink.tester.ui.i18n.ServerMessages.localize(message)?.takeIf { it.isNotBlank() } ?: "HTTP $code")
    }

    companion object {
        /** Destinatários por pedido aceites pelo servidor (link por email e criar com emails). */
        const val MAX_EMAILS = 10
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
        expireInMinutes = expire_in_minutes ?: expire_in?.takeIf { it > 0 }?.let { it * 60 },
        expiresAt = expires_at,
        passwordTipAllowed = protectedPasswordShow ?: false,
        passwordTip = showpassword_in,
        active = active ?: true,
    )
}
