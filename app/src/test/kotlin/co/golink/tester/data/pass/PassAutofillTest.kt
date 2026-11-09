package co.golink.tester.data.pass

import android.text.InputType
import co.golink.tester.data.pass.autofill.PassAutofillMatch
import co.golink.tester.data.pass.autofill.PassAutofillParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassAutofillTest {
    private fun login(id: String, url: String, user: String = "u", apps: List<String> = emptyList(), name: String = id) = PassItem(
        id, 1, null, null, null, "",
        Json.parseToJsonElement(
            """{"type":"login","name":"$name","login":{"url":"$url","username":"$user","password":"p","totp":"","apps":[${apps.joinToString(",") { "\"$it\"" }}]}}""",
        ) as JsonObject,
    )

    @Test fun domainsMatchOnLabelBoundary() {
        assertTrue(PassAutofillMatch.domainsMatch("accounts.google.com", "google.com"))
        assertTrue(PassAutofillMatch.domainsMatch("www.github.com", "github.com"))
        assertFalse(PassAutofillMatch.domainsMatch("evilgoogle.com", "google.com"))
        assertFalse(PassAutofillMatch.domainsMatch("example.co.uk", "co.uk"))
        assertFalse(PassAutofillMatch.domainsMatch("a.com", "com"))
        // Alojamento partilhado (PSL): cada subdomínio é um site diferente.
        assertFalse(PassAutofillMatch.domainsMatch("evil.github.io", "github.io"))
        assertFalse(PassAutofillMatch.domainsMatch("a.github.io", "b.github.io"))
        assertTrue(PassAutofillMatch.domainsMatch("me.github.io", "login.me.github.io"))
        assertTrue(PassAutofillMatch.domainsMatch("shop.example.co.uk", "example.co.uk"))
    }

    @Test fun browserDomainOnlyFromKnownBrowsers() {
        assertTrue(PassAutofillParser.isTrustedBrowser("com.android.chrome"))
        assertFalse(PassAutofillParser.isTrustedBrowser("com.evil.paypal"))
        // Browser sem webDomain não usa login.apps nem heurística.
        val items = listOf(login("c", "https://chrome.com", apps = listOf("com.android.chrome")))
        assertTrue(PassAutofillMatch.match(items, "com.android.chrome", null).isEmpty())
    }

    @Test fun mainLabelAndTokens() {
        assertEquals("example", PassAutofillMatch.mainLabel("shop.example.co.uk"))
        assertEquals("netflix", PassAutofillMatch.mainLabel("www.netflix.com"))
        assertEquals(setOf("netflix", "mediaclient"), PassAutofillMatch.packageTokens("com.netflix.mediaclient"))
    }

    @Test fun matchWebAndApps() {
        val items = listOf(
            login("g", "https://accounts.google.com/x"),
            login("n", "https://netflix.com"),
            login("b", "", apps = listOf("pt.bank.app")),
            login("e", "https://evilgoogle.com"),
        )
        val web = PassAutofillMatch.match(items, "com.android.chrome", "google.com")
        assertEquals(listOf("g"), web.map { it.item.id })
        val app = PassAutofillMatch.match(items, "com.netflix.mediaclient", null)
        assertEquals(listOf("n" to PassAutofillMatch.Strength.Heuristic), app.map { it.item.id to it.strength })
        val remembered = PassAutofillMatch.match(items, "pt.bank.app", null)
        assertEquals(listOf("b" to PassAutofillMatch.Strength.Exact), remembered.map { it.item.id to it.strength })
        assertTrue(PassAutofillMatch.match(items, "com.other.thing", null).isEmpty())
    }

    @Test fun classifyFields() {
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals("password", PassAutofillParser.classify(emptyList(), text or InputType.TYPE_TEXT_VARIATION_PASSWORD, null, null, null, null))
        assertEquals("password", PassAutofillParser.classify(listOf("password"), text, null, null, null, null))
        assertEquals("username", PassAutofillParser.classify(emptyList(), text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, null, null, null, null))
        assertEquals("username", PassAutofillParser.classify(emptyList(), text, null, null, "login_email", null))
        assertEquals("password", PassAutofillParser.classify(emptyList(), 0, "password", null, null, null))
        assertNull(PassAutofillParser.classify(emptyList(), text, null, "one-time-code", null, null))
        assertNull(PassAutofillParser.classify(emptyList(), text, null, null, "search_box", "Pesquisar"))
        assertNull(PassAutofillParser.classify(emptyList(), text, null, null, "first_name", null))
    }
}
