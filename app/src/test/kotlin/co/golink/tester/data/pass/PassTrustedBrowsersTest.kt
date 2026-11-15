package co.golink.tester.data.pass

import co.golink.tester.data.pass.autofill.PassAutofillMatch
import co.golink.tester.data.pass.autofill.PassAutofillParser
import co.golink.tester.data.pass.autofill.PassTrustedBrowsers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassTrustedBrowsersTest {
    /**
     * Certificados de teste do AOSP (chaves privadas públicas no código-fonte do Android):
     * SHA-256 dos *.x509.pem de platform/build/target/product/security. Qualquer app pode
     * ser assinada com eles, por isso nunca podem dar confiança ao webDomain.
     */
    private val aospTestKeys = mapOf(
        "platform" to "C8A2E9BCCF597C2FB6DC66BEE293FC13F2FC47EC77BC6B2B0D52C11F51192AB8",
        "testkey" to "A40DA80A59D170CAA950CF15C18C454D47A39B26989D8B640ECD745BA71BF5DC",
        "shared" to "28BBFE4A7B97E74681DC55C2FBB6CCB8D6C74963733F6AF6AE74D8C3A6E879FD",
        "media" to "465983F7791F2ABEB43EA2CBDC7F21A8260B72BC08A55C839FC1A43BC741A81E",
        "networkstack" to "E1DBADCE60DC080D15B58A014B0DCF9400E24DE23FA00B287A5A982BFEBDA2EE",
        "bluetooth" to "A6CCC500FF0E7421200EB66A7FE174EF1B00E52CA91727070CBEDF061FF76C35",
        "nfc" to "FAE9122A8721D6E2A196D2224DFFCF773C9127E2BB956CBDDB40B009192FFDFD",
        "sdk_sandbox" to "ABF21F9E2AF1D881CC673FDDCEFA6ED9C269A437BD64B279CF45844CFD589126",
        "cts_uicc_2021" to "CE7B2B47AE2B7552C8F92CC29124279883041FB623A5F194A82C9BF15D492AA0",
    )

    @Test fun noAospTestKeys() {
        for ((pkg, certs) in PassTrustedBrowsers.CERTS) {
            for ((name, fp) in aospTestKeys) {
                assertFalse("$pkg aceita a chave de teste AOSP '$name'", certs.any { it.equals(fp, ignoreCase = true) })
            }
        }
    }

    @Test fun certsWellFormed() {
        for ((pkg, certs) in PassTrustedBrowsers.CERTS) {
            assertTrue("$pkg sem certificados", certs.isNotEmpty())
            for (c in certs) assertTrue("$pkg: '$c' não é um SHA-256 em hex maiúsculo", c.matches(Regex("[0-9A-F]{64}")))
        }
    }

    @Test fun knownIncludesAllCertified() {
        assertTrue(PassTrustedBrowsers.KNOWN.containsAll(PassTrustedBrowsers.CERTS.keys))
    }

    /** Browsers conhecidos sem certificado (lista antiga ou só com chaves de debug) nunca contam como app. */
    @Test fun knownUnverifiedBrowsersAreNotApps() {
        for (pkg in listOf("com.opera.gx", "org.torproject.torbrowser", "org.cromite.cromite", "com.google.android.apps.chrome", "io.island.IslandDev")) {
            assertFalse("$pkg não devia estar em CERTS", pkg in PassTrustedBrowsers.CERTS)
            assertTrue("$pkg devia ser browser conhecido", PassAutofillParser.isTrustedBrowser(pkg))
            // Mesmo com o pacote lembrado num login (ex.: guardado na 4.0.0), sem webDomain não há correspondência.
            val item = PassItem(
                "x", 1, null, null, null, "",
                Json.parseToJsonElement(
                    """{"type":"login","name":"x","login":{"url":"https://bank.example","username":"u","password":"p","totp":"","apps":["$pkg"]}}""",
                ) as JsonObject,
            )
            assertTrue(PassAutofillMatch.match(listOf(item), pkg, null).isEmpty())
        }
    }
}
