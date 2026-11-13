package co.golink.tester.data.pass.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/** Campos de login encontrados no ecrã de outra app (ou página do browser). */
data class PassAutofillTarget(
    val packageName: String,
    val webDomain: String?,
    val usernameIds: List<AutofillId>,
    val passwordIds: List<AutofillId>,
    /** Valores escritos pelo utilizador (só no pedido de gravação). */
    val usernameValue: String? = null,
    val passwordValue: String? = null,
) {
    val allIds: List<AutofillId> get() = usernameIds + passwordIds
    val isEmpty: Boolean get() = passwordIds.isEmpty() && usernameIds.isEmpty()
    /** Como mostrar o destino: domínio da página ou nome do pacote. */
    val label: String get() = webDomain ?: packageName
}

object PassAutofillParser {
    private val USER_WORDS = listOf("user", "email", "e-mail", "login", "account", "utilizador", "usuario", "usuário", "identifiant", "correo", "phone", "username")
    private val PASS_WORDS = listOf("pass", "pwd", "senha", "contraseña", "contrasena", "mot de passe", "motdepasse", "password")
    private val SKIP_WORDS = listOf("search", "pesquis", "otp", "code", "código", "captcha", "card", "cvv", "confirm", "new-password", "newpassword")

    /** Classifica um campo a partir das pistas (puro, testável). null = ignorar. */
    fun classify(hints: List<String>, inputType: Int, htmlType: String?, htmlAutocomplete: String?, idEntry: String?, hintText: String?): String? {
        val hint = hints.joinToString(" ").lowercase()
        if (hint.contains("password")) return "password"
        if (hint.contains("username") || hint.contains("email") || hint.contains("phone")) return "username"
        val ac = htmlAutocomplete?.lowercase().orEmpty()
        if (ac.contains("one-time-code") || ac.startsWith("cc-")) return null
        if (ac.contains("current-password") || ac.contains("new-password")) return "password"
        if (ac.contains("username") || ac.contains("email")) return "username"
        val html = htmlType?.lowercase()
        if (html == "password") return "password"
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            )
        ) return "password"
        if (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return "password"
        val words = listOfNotNull(idEntry, hintText).joinToString(" ").lowercase()
        if (SKIP_WORDS.any { words.contains(it) }) return null
        if (PASS_WORDS.any { words.contains(it) }) return "password"
        if (cls == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)) return "username"
        if (html == "email") return "username"
        if (USER_WORDS.any { words.contains(it) }) return "username"
        return null
    }

    /**
     * Browsers em que o `webDomain` é de confiança (setWebDomain é público: qualquer app o pode
     * declarar). Mesma lista que os compatibility-package de pass_autofill_service.xml + browsers
     * com autofill nativo. Fora desta lista o pedido é tratado como app.
     */
    val TRUSTED_BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.chromium.chrome",
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        "com.microsoft.emmx", "com.microsoft.emmx.beta", "com.microsoft.emmx.dev", "com.microsoft.emmx.canary",
        "com.opera.browser", "com.opera.browser.beta", "com.opera.mini.native", "com.opera.gx",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "com.vivaldi.browser", "com.vivaldi.browser.snapshot",
        "com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.ecosia.android",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus", "org.mozilla.klar",
        "org.mozilla.fennec_fdroid", "org.torproject.torbrowser", "us.spotco.fennec_dos", "io.github.forkmaintainers.iceraven",
        "org.cromite.cromite", "org.bromite.bromite",
    )

    fun isTrustedBrowser(pkg: String?): Boolean = pkg != null && pkg in TRUSTED_BROWSERS

    fun parse(structure: AssistStructure, withValues: Boolean = false): PassAutofillTarget {
        val pkg = structure.activityComponent?.packageName.orEmpty()
        val trustDomain = isTrustedBrowser(pkg)
        var domain: String? = null
        val users = mutableListOf<AutofillId>()
        val passwords = mutableListOf<AutofillId>()
        var userValue: String? = null
        var passValue: String? = null
        // Campo de texto visível imediatamente antes da password (username sem pistas).
        var lastText: AssistStructure.ViewNode? = null

        fun walk(node: AssistStructure.ViewNode) {
            if (trustDomain) node.webDomain?.takeIf { it.isNotBlank() }?.let { if (domain == null) domain = it }
            val id = node.autofillId
            if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.visibility == View.VISIBLE) {
                val html = node.htmlInfo
                val attrs = html?.attributes.orEmpty().associate { it.first.lowercase() to it.second }
                val hintText = node.hint ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) attrs["placeholder"] ?: attrs["name"] else null
                val kind = if (html != null && html.tag.lowercase() != "input") null else classify(
                    node.autofillHints?.toList().orEmpty(), node.inputType, attrs["type"], attrs["autocomplete"],
                    listOfNotNull(node.idEntry, attrs["id"], attrs["name"]).joinToString(" "), hintText,
                )
                when (kind) {
                    "password" -> {
                        passwords += id
                        if (withValues && passValue == null) passValue = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                        // Página só com password: o campo de texto anterior é o utilizador.
                        if (users.isEmpty()) lastText?.autofillId?.let {
                            users += it
                            if (withValues) userValue = lastText?.autofillValue?.takeIf { v -> v.isText }?.textValue?.toString()
                        }
                    }
                    "username" -> {
                        users += id
                        if (withValues && userValue == null) userValue = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                    }
                }
                if (kind == null) lastText = node
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)
        // Sem password à vista e sem pistas claras não é um formulário de login.
        return PassAutofillTarget(pkg, domain, users.distinct(), passwords.distinct(), userValue?.takeIf { it.isNotBlank() }, passValue?.takeIf { it.isNotEmpty() })
    }
}
