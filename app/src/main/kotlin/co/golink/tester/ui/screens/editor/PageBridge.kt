package co.golink.tester.ui.screens.editor

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import co.golink.tester.ui.screens.notes.sameSite
import org.json.JSONObject

/**
 * Lado da app da ponte com as páginas /editor-embed e /notes-embed
 * (resources/js/services/appBridge.js na web).
 */
internal interface PageBridgeHost {
    /** JSON (ou "null") com os dados de arranque da página. */
    fun info(): String

    /** Trabalho assíncrono: [onDone] recebe o id quando o resultado está pronto em [take]. */
    fun request(id: Int, method: String, args: String, onDone: (Int) -> Unit)

    fun take(id: Int): String

    fun toast(message: String)

    fun close() {}
}

/**
 * Ponte por mensagens (WebViewCompat.addWebMessageListener): o objeto window.[name] só
 * existe nos frames da origem do site da página (com e sem www), por isso um iframe de
 * outra origem não o vê; e cada mensagem tem de vir do frame principal desse site.
 *
 * Devolve false quando a WebView não suporta WEB_MESSAGE_LISTENER ([pageBridgeSupported]): a
 * interface antiga (addJavascriptInterface) foi retirada porque ficava visível a todos os frames.
 */
internal fun installMessageBridge(view: WebView, name: String, page: Uri, host: PageBridgeHost): Boolean {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
    val origins = siteOrigins(page)
    if (origins.isEmpty()) return false
    return runCatching {
        WebViewCompat.addWebMessageListener(view, name, origins) { webView, message, sourceOrigin, isMainFrame, reply ->
            if (!isMainFrame || !sameSite(sourceOrigin, page)) return@addWebMessageListener
            val msg = message.data?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@addWebMessageListener
            // Os resultados chegam de outras threads; o reply tem de sair pela principal.
            val send = { text: String -> webView.post { runCatching { reply.postMessage(text) } } }
            when (msg.optString("t")) {
                "info" -> send("""{"t":"info","data":${host.info()}}""")
                "req" -> {
                    val id = msg.optInt("id", Int.MIN_VALUE)
                    val method = msg.optString("method")
                    if (id == Int.MIN_VALUE || method.isEmpty()) return@addWebMessageListener
                    host.request(id, method, msg.optString("args", "{}")) { done ->
                        send("""{"t":"res","id":$done,"result":${host.take(done)}}""")
                    }
                }
                "toast" -> host.toast(msg.optString("message"))
                "close" -> host.close()
            }
        }
    }.isSuccess
}

/** Tira as duas formas da ponte (a que não estiver instalada é ignorada). */
internal fun uninstallPageBridge(view: WebView, portName: String, legacyName: String) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        runCatching { WebViewCompat.removeWebMessageListener(view, portName) }
    }
    view.removeJavascriptInterface(legacyName)
}

/** "https://host[:porta]" do site da página, com e sem www (como em [sameSite]). */
private fun siteOrigins(page: Uri): Set<String> {
    val scheme = page.scheme ?: return emptySet()
    val host = page.host?.removePrefix("www.") ?: return emptySet()
    val port = if (page.port != -1) ":${page.port}" else ""
    return setOf("$scheme://$host$port", "$scheme://www.$host$port")
}

/** A WebView suporta a ponte por mensagens; sem ela o editor e as Notes não abrem (pedir atualização). */
internal fun pageBridgeSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

internal const val WEBVIEW_UPDATE_MSG = "Atualiza o Android System WebView (Play Store) para abrir esta página"
