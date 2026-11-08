package co.golink.tester.data.pass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassLogicTest {
    // ---- TOTP: vetores do RFC 6238 (apêndice B), 8 dígitos ----
    private val seed20 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" // "12345678901234567890"
    private val seed32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA"
    private val seed64 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA"

    @Test
    fun totpRfc6238() {
        val sha1 = TotpConfig(seed20, 8, 30, "SHA1", "", "")
        val sha256 = TotpConfig(seed32, 8, 30, "SHA256", "", "")
        val sha512 = TotpConfig(seed64, 8, 30, "SHA512", "", "")
        assertEquals("94287082", Totp.hotp(sha1, 59L / 30))
        assertEquals("46119246", Totp.hotp(sha256, 59L / 30))
        assertEquals("90693936", Totp.hotp(sha512, 59L / 30))
        assertEquals("07081804", Totp.hotp(sha1, 1111111109L / 30))
        assertEquals("65353130", Totp.hotp(sha1, 20000000000L / 30))
        val now = Totp.now(sha1, 59_000L)
        assertEquals("94287082", now.code)
        assertEquals(1, now.remaining)
    }

    @Test
    fun totpParse() {
        assertNotNull(Totp.parse("JBSW Y3DP EHPK 3PXP"))
        assertNull(Totp.parse("JBSWY3DP")) // < 10 bytes
        assertNull(Totp.parse("not base32!"))
        val uri = Totp.parse("otpauth://totp/GitHub:golink-dev?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30")!!
        assertEquals("GitHub", uri.issuer)
        assertEquals("golink-dev", uri.account)
        assertEquals("JBSWY3DPEHPK3PXP", uri.secret)
        assertNull(Totp.parse("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP"))
        assertEquals("482 913", Totp.format("482913"))
    }

    // ---- Força: valores gerados pelo strength.js da web ----
    @Test
    fun strengthMatchesWeb() {
        val golden = listOf(
            Triple("password", 8.0, 0), Triple("Password1!", 8.0, 0), Triple("tulipa2024", 51.699250, 2),
            Triple("abcabcabc", 18.801759, 0), Triple("aaaaaaaa", 9.400879, 0), Triple("1234", 8.637013, 0),
            Triple("yP6@nCT!brGX&fvL3=", 118.257401, 4), Triple("correct-horse-battery-staple", 164.714005, 4),
            Triple("qwerty123", 8.0, 0), Triple("Zx9#kL2m", 52.558845, 2), Triple("olá mundo €", 75.842995, 3),
            Triple("19830412", 21.260340, 0),
        )
        for ((pw, bits, score) in golden) {
            val r = PassStrength.of(pw)
            assertEquals(pw, bits, r.bits, 1e-4)
            assertEquals(pw, score, r.score)
        }
        assertEquals("millions_years", PassStrength.crackTimeKey(118.0))
        assertEquals("days", PassStrength.crackTimeKey(51.7))
    }

    @Test
    fun generator() {
        val pw = PassGenerator.random(PassGenerator.RandomOpts(length = 18))
        assertEquals(18, pw.length)
        assertTrue(pw.any { it.isUpperCase() } && pw.any { it.isDigit() } && pw.any { it in PassGenerator.SYMBOLS })
        assertFalse(pw.any { it in "Il1O0o" })
        val phrase = PassGenerator.passphrase(PassGenerator.PassphraseOpts(words = 5))
        assertEquals(5, phrase.split('-').size)
        assertTrue(phrase.any { it.isDigit() })
        assertEquals(6, PassGenerator.pin().length)
        assertEquals(1295, PassWordlist.words.size)
    }

    @Test
    fun healthHelpers() {
        assertEquals(46, PassHealth.score(13, 7))
        assertEquals(100, PassHealth.score(0, 0))
        assertEquals("bank.example", PassHealth.hostOf("https://www.bank.example/login"))
        assertEquals("shop.example", PassHealth.hostOf("shop.example"))
        assertTrue(PassHealth.supports2fa("accounts.github.com", setOf("github.com")))
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\n00D4F6E8FA6EECAD2A3AA415EEC418D38EC:2\n"
        assertEquals(2, PassHealth.countIn(body, "00D4F6E8FA6EECAD2A3AA415EEC418D38EC"))
        assertEquals(0, PassHealth.countIn(body, "FFFFF6E8FA6EECAD2A3AA415EEC418D38EC"))
        assertEquals(16, PassHealth.passwordPrint("x").length)
    }

    @Test
    fun importers() {
        val csv = "name,url,username,password,note\r\nGitHub,https://github.com,dev,\"p,w\"\"1\",\"multi\nline\"\r\n,https://www.shop.example,me,pw2,\r\n"
        val drafts = PassImporters.importCsv(csv, "chrome")
        assertEquals(2, drafts.size)
        val login = drafts[0].payload["login"] as JsonObject
        assertEquals("p,w\"1", login.str("password"))
        assertEquals("multi\nline", drafts[0].payload.str("notes"))
        assertEquals("shop.example", drafts[1].payload.str("name"))

        val lp = PassImporters.importCsv("url,username,password,extra,name,grouping,fav\nhttp://sn,,,secret text,My note,Work,1\n", "lastpass")
        assertEquals("note", lp[0].payload.str("type"))
        assertEquals("Work", lp[0].folderName)
        assertTrue(lp[0].payload.bool("favourite"))

        val bw = Json.parseToJsonElement(
            """{"encrypted":false,"folders":[{"id":"f1","name":"Finance"}],"items":[
              {"type":1,"name":"Bank","folderId":"f1","favorite":true,"login":{"uris":[{"uri":"https://bank.example"}],"username":"u","password":"p","totp":null}},
              {"type":3,"name":"Visa","card":{"cardholderName":"A","number":"4111","expMonth":"3","expYear":"2029","code":"123"}},
              {"type":2,"name":"Note","notes":"n"}]}""",
        ) as JsonObject
        val items = PassImporters.importBitwardenJson(bw)
        assertEquals(3, items.size)
        assertEquals("Finance", items[0].folderName)
        assertEquals("03/29", (items[1].payload["card"] as JsonObject).str("expiry"))
    }
}
