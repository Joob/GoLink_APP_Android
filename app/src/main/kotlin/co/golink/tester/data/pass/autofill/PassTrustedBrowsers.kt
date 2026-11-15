package co.golink.tester.data.pass.autofill

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Browsers cujo `webDomain` é de confiança, verificados pelo certificado de assinatura
 * (SHA-256) e não só pelo nome do pacote: uma app instalada fora do Play pode usar o
 * nome de um browser que a pessoa não tem (ex.: com.chrome.canary) e declarar
 * setWebDomain("paypal.com"). Fonte: lista de browsers privilegiados do Credential
 * Manager (https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json, 2026-11-15),
 * sem as entradas que não são browsers. Só se aceitam certificados de builds "release":
 * os "userdebug" ficam de fora porque alguns são chaves públicas (ex.: a chave de teste
 * da plataforma AOSP, C8A2E9BC…, cuja chave privada está no código-fonte do Android) e
 * qualquer app assinada com elas passaria por browser. Pacotes sem certificado release
 * não entram. O teste PassTrustedBrowsersTest falha se aparecer uma chave de teste AOSP.
 * Manter os <queries> do AndroidManifest em sincronia com as chaves de [CERTS].
 *
 * Browsers conhecidos ([KNOWN]) mas sem assinatura verificada: o webDomain é ignorado e
 * também não contam como app (não se liga o pacote do browser a um login).
 * Fora das duas listas o pedido conta como app (sem webDomain): continua a ter "Outros logins…".
 */
object PassTrustedBrowsers {
    val CERTS: Map<String, Set<String>> = mapOf(
        "com.android.chrome" to setOf("F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB83"),
        "com.chrome.beta" to setOf("3D7A1223019AA39D9EA0E3436AB7C0896BFB4FB679F4DE5FE7C23F326C8F994A", "DA633D34B69E63AE2103B49D53CE052FC5F7F3C53AAB94FDC2A208BDFD14249C"),
        "com.chrome.dev" to setOf("3D7A1223019AA39D9EA0E3436AB7C0896BFB4FB679F4DE5FE7C23F326C8F994A", "9044EE5FEE4BBC5E21DD44665431C4EB1F1F71A32716A0BC927BCBB39233CABF"),
        "com.chrome.canary" to setOf("2019DFA1FB23EFBF70C5BCD1443C5BEAB04F3F2FF4366E9AC1E3457639A24CFC"),
        "org.chromium.chrome" to setOf("C6ADB8B83C6D4C17D292AFDE56FD488A51D316FF8F2C11C5410223BFF8A7DBB3"),
        "org.mozilla.firefox" to setOf("A78B62A5165B4494B2FEAD9E76A280D22D937FEE6251AECE599446B2EA319B04"),
        "org.mozilla.firefox_beta" to setOf("A78B62A5165B4494B2FEAD9E76A280D22D937FEE6251AECE599446B2EA319B04"),
        "org.mozilla.focus" to setOf("6203A473BE36D64EE37F87FA500EDBC79EAB930610AB9B9FA4CA7D5C1F1B4FFC"),
        "org.mozilla.fennec_aurora" to setOf("BC0488838D06F4CA6BF32386DAAB0DD8EBCF3E7730787459F62FB3CD14A1BAAA"),
        "org.mozilla.rocket" to setOf("863A46F0973932B7D0199B549112741C2D2731AC72EA11B7523AA90A11BF5691"),
        "org.mozilla.fenix" to setOf("5004779088E7F988D5BC5CC5F8798FEBF4F8CD084A1B2A46EFD4C8EE4AEAF211"),
        "org.mozilla.focus.beta" to setOf("6203A473BE36D64EE37F87FA500EDBC79EAB930610AB9B9FA4CA7D5C1F1B4FFC"),
        "org.mozilla.focus.nightly" to setOf("6203A473BE36D64EE37F87FA500EDBC79EAB930610AB9B9FA4CA7D5C1F1B4FFC"),
        "org.mozilla.klar" to setOf("6203A473BE36D64EE37F87FA500EDBC79EAB930610AB9B9FA4CA7D5C1F1B4FFC"),
        "com.microsoft.emmx.canary" to setOf("01E1999710A82C2749B4D50C445DC85D670B6136089D0A766A73827C82A1EAC9"),
        "com.microsoft.emmx.dev" to setOf("01E1999710A82C2749B4D50C445DC85D670B6136089D0A766A73827C82A1EAC9"),
        "com.microsoft.emmx.beta" to setOf("01E1999710A82C2749B4D50C445DC85D670B6136089D0A766A73827C82A1EAC9"),
        "com.microsoft.emmx" to setOf("01E1999710A82C2749B4D50C445DC85D670B6136089D0A766A73827C82A1EAC9"),
        "com.brave.browser" to setOf("9C2DB70513515FDBFBBC585B3EDF3D7123D4DC67C94FFD306361C1D79BBF18AC"),
        "com.brave.browser_beta" to setOf("9C2DB70513515FDBFBBC585B3EDF3D7123D4DC67C94FFD306361C1D79BBF18AC"),
        "com.brave.browser_nightly" to setOf("9C2DB70513515FDBFBBC585B3EDF3D7123D4DC67C94FFD306361C1D79BBF18AC"),
        "app.vanadium.browser" to setOf("C6ADB8B83C6D4C17D292AFDE56FD488A51D316FF8F2C11C5410223BFF8A7DBB3"),
        "com.vivaldi.browser" to setOf("E8A78544655BA8C09817F732768F5689B1662EC4B2BC5A0BC0EC138D33CA3D1E"),
        "com.vivaldi.browser.snapshot" to setOf("E8A78544655BA8C09817F732768F5689B1662EC4B2BC5A0BC0EC138D33CA3D1E"),
        "com.vivaldi.browser.sopranos" to setOf("E8A78544655BA8C09817F732768F5689B1662EC4B2BC5A0BC0EC138D33CA3D1E"),
        "com.android.browser" to setOf("C9009D01EBF9F5D0302BC71B2FE9AA9A47A432BBA17308A3111B75D7B2149025"),
        "com.sec.android.app.sbrowser" to setOf("34DF0E7A9F1CF1892E45C056B4973CD81CCF148A4050D11AEA4AC5A65F900A42"),
        "com.sec.android.app.sbrowser.beta" to setOf("34DF0E7A9F1CF1892E45C056B4973CD81CCF148A4050D11AEA4AC5A65F900A42"),
        "com.yandex.browser" to setOf("ACA405DED8B25CB2E8C6DA69425D2B4307D087C1276FC06AD5942731CCC51DBA"),
        "com.yandex.browser.beta" to setOf("ACA405DED8B25CB2E8C6DA69425D2B4307D087C1276FC06AD5942731CCC51DBA"),
        "com.yandex.browser.alpha" to setOf("ACA405DED8B25CB2E8C6DA69425D2B4307D087C1276FC06AD5942731CCC51DBA"),
        "com.yandex.browser.corp" to setOf("ACA405DED8B25CB2E8C6DA69425D2B4307D087C1276FC06AD5942731CCC51DBA"),
        "com.yandex.browser.canary" to setOf("1DA9CBAE2DCCC6A58D6C947BE94CDBB733D65DA4D1770FA14A5364CB4A28EB49"),
        "com.yandex.browser.broteam" to setOf("1DA9CBAE2DCCC6A58D6C947BE94CDBB733D65DA4D1770FA14A5364CB4A28EB49"),
        "com.talonsec.talon" to setOf("A3660344A6F6AFCA818CBF4396A23CCFD5ED7A781BB4A3D1850301E2F46D2383", "E2A56474EA237B0667B6F52CDCE9045E24883BAED082599AA2DF0B603ACF6A3B"),
        "com.talonsec.talon_beta" to setOf("9AA125D5E55E3FB0DE9672D9A95D04653F494A1EC3EE761E94C44E5D2F658E2F", "F586627A32C89FE67E006DB18C34319E017FB3B2BED69D0101B7F943E77C48AE"),
        "com.talonsec.intune.talon" to setOf("090C4CE620659B0400358FC83FDC9F02D2AB77C37F4C8014308EFF96BA544955"),
        "com.duckduckgo.mobile.android" to setOf("BB7BB31C573C46A1DA7FC5C528A6ACF432108456FEEC50810C7F33694EB3D2D4"),
        "com.naver.whale" to setOf("0B8B8523BB4AEFFA346E4BDD4FBF7D193450569AA14AAAD4ADFD94A3F7B227BB"),
        "com.heytap.browser" to setOf("A8FEA4CAFB9332DA26B8E6810817C1DA90A5030E35A60A79E06C9097AAC6A442", "AFF8A749CF0E7D754465D0FBFA7B8D0C645E225C10C6E232ADA0D9748836B8E5", "B29AA0BBDC9FD9DEF55DC56EA7D74576D5846CBCF5E5ABD305E2D9319E4F42AE"),
        "io.island.Island" to setOf("D9C339AC9C3AEEE1751D858C35D9BAC5CC87B3CE763093F0F51064F5A2F69B04"),
        "io.island.IslandCanary" to setOf("90171323456E6F39CBFDCFB256BE1DCFF3BC1C598A159330E49773D04CB9C905"),
        "io.island.IslandBeta" to setOf("3531831A9E2B211DE6AAC3694B45836E5609B9D7D004C31B218740FB771738D1"),
        "io.island.island.intune" to setOf("C23824154120A08FC39542ACD82AE9249478801E47FD6C662B181C28CA7E594E"),
        "io.island.island.canary.intune" to setOf("1E1674BB79EA09FB37CF9F1B071B1D518D46030ED3EEF2C14EAD939EC6EE3A4C"),
        "io.island.island.beta.intune" to setOf("D25EADF61CE6366CA423A47FC4DB9B8C9C8A35B4B019E8D982FBD08AD9DB495A"),
        "net.quetta.browser" to setOf("BEFEE731126AA56E7EFDAEAF5EF3FAEA441C19CCE0CAEC426B65BBF82C594680"),
        "cz.seznam.sbrowser" to setOf("DB9540661078836E4EB166F69EF407309E8DAE3334685EC8F6FA2F1381B9ACF6"),
        "com.opera.mini.native" to setOf("57ACBC525F1B2EBD19196CD6F014397CC910FD18841E0AE850FEBC3E1E593FF2"),
        "com.opera.mini.native.beta" to setOf("57ACBC525F1B2EBD19196CD6F014397CC910FD18841E0AE850FEBC3E1E593FF2"),
        "com.zoho.primeum.stable" to setOf("A9D6D0A2AFDB15849B8CD31D51FE73B8E1B170BAA570C2F8F2A3F8652829CBBD"),
        "ai.perplexity.comet" to setOf("8958A405401F69F5B0FB544424746C40DEC30C091F401F951F613C4835C3E5EC"),
        "com.amazon.cloud9" to setOf("2F19ADEB284EB36F7F07786152B9A1D14B21653203AD0B04EBBF9C73AB6D7625", "70D568EC6AE6F338BC1A6399A6537EE06908CA1D72FB8FF04874AB95433B250E"),
        "net.waterfox.android.release" to setOf("2939997A2D8F07303CEB37AD6810AFEF0BDA710BE2116476E3525A7379EC2E1A"),
        "com.opera.browser" to setOf("5D6AFBF87F652AF04647ADA0DF634CF22370900B164B09D50BD23AA2CB5285B8"),
        "com.opera.browser.beta" to setOf("5D6AFBF87F652AF04647ADA0DF634CF22370900B164B09D50BD23AA2CB5285B8"),
        "com.wsapp.browser" to setOf("F5219530FC8F87993B3CF50E838A9675AB3B2DC579A2A8520D99872A26B15C5F"),
        "io.github.jqssun.helium" to setOf("095540494D53F48CA051BC86C392C46EDB29725DA5592BD8AF3C3D027B3166BB"),
        "com.norton.neo" to setOf("C52A34DEF81EDED53D48ADD3756C7324A85B4204D565A7220F0838E3076D034B"),
        "com.netskope.browser" to setOf("B8BBF59CBD84EB217BF43D1C50E85C5343E79369039390069DC00BA8A88FBADE"),
    )

    /**
     * Nomes de browsers conhecidos, com ou sem certificado em [CERTS]: a lista antiga
     * (até à 3.x) + os retirados de [CERTS] por só terem certificados de debug. Num destes
     * sem assinatura verificada o webDomain é ignorado, mas o pedido também não é tratado
     * como app: "lembrar para esta app" ligaria o login ao browser inteiro e qualquer
     * página (phishing incluído) teria correspondência exata.
     */
    val KNOWN: Set<String> = CERTS.keys + setOf(
        "com.opera.gx", "com.kiwibrowser.browser", "com.ecosia.android",
        "org.mozilla.fennec_fdroid", "org.torproject.torbrowser", "us.spotco.fennec_dos",
        "io.github.forkmaintainers.iceraven", "org.cromite.cromite", "org.bromite.bromite",
        "com.google.android.apps.chrome", "io.island.IslandDev", "io.island.island.dev.intune",
    )

    /** Nome conhecido de browser (sem verificar a assinatura): só para decisões que restringem. */
    fun isKnown(pkg: String?): Boolean = pkg != null && pkg in KNOWN

    /** Browser conhecido e assinado com um dos certificados esperados. */
    fun isVerified(context: Context, pkg: String?): Boolean {
        val expected = pkg?.let { CERTS[it] } ?: return false
        return runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                expected.any { pm.hasSigningCertificate(pkg, hex(it), PackageManager.CERT_INPUT_SHA256) }
            } else {
                @Suppress("DEPRECATION")
                val sigs = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures.orEmpty()
                sigs.isNotEmpty() && sigs.all { s -> sha256Hex(s.toByteArray()) in expected }
            }
        }.getOrDefault(false)
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02X".format(it) }
}
