package co.golink.tester.ui.screens.settings

// Tabelas das Definições (indicativos, fusos, países), fora do SettingsScreen.kt.

/** Indicativos (ISO → "+351"), pela ordem da lista de países. */
internal val DIAL_CODES = listOf(
    "PT" to "+351", "ES" to "+34", "FR" to "+33", "DE" to "+49", "IT" to "+39", "GB" to "+44",
    "IE" to "+353", "NL" to "+31", "BE" to "+32", "LU" to "+352", "CH" to "+41", "AT" to "+43",
    "DK" to "+45", "SE" to "+46", "NO" to "+47", "FI" to "+358", "PL" to "+48", "CZ" to "+420",
    "SK" to "+421", "HU" to "+36", "RO" to "+40", "BG" to "+359", "GR" to "+30", "HR" to "+385",
    "SI" to "+386", "EE" to "+372", "LV" to "+371", "LT" to "+370", "US" to "+1", "CA" to "+1",
    "MX" to "+52", "BR" to "+55", "AR" to "+54", "CL" to "+56", "CO" to "+57", "PE" to "+51",
    "AU" to "+61", "NZ" to "+64", "JP" to "+81", "KR" to "+82", "CN" to "+86", "HK" to "+852",
    "TW" to "+886", "SG" to "+65", "IN" to "+91", "ID" to "+62", "TH" to "+66", "VN" to "+84",
    "PH" to "+63", "MY" to "+60", "AE" to "+971", "SA" to "+966", "IL" to "+972", "TR" to "+90",
    "EG" to "+20", "ZA" to "+27", "NG" to "+234", "KE" to "+254", "MA" to "+212", "AO" to "+244",
    "MZ" to "+258", "CV" to "+238", "RU" to "+7", "UA" to "+380",
)

internal fun dialCode(iso: String): String = DIAL_CODES.firstOrNull { it.first == iso }?.second ?: "+351"

/** "agora 10:13" no fuso escolhido (deslocamento em horas, ex. "5.5"). */
internal fun timeAtOffset(value: String): String? {
    val h = value.toDoubleOrNull() ?: return null
    return runCatching {
        java.time.OffsetDateTime.now(java.time.ZoneOffset.ofTotalSeconds((h * 3600).toInt()))
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrNull()
}

// Fuso horário: o valor guardado é o deslocamento ("0.0"), como na web. Rótulos só
// "GMT±H[:MM]": com cidades ("Lisboa, Londres") a hora de verão ficava 1 h errada.
internal val TIMEZONES = listOf(
    "-12.0",
    "-11.0",
    "-10.0",
    "-9.5",
    "-9.0",
    "-8.0",
    "-7.0",
    "-6.0",
    "-5.0",
    "-4.0",
    "-3.5",
    "-3.0",
    "-2.0",
    "-1.0",
    "0.0",
    "1.0",
    "2.0",
    "3.0",
    "3.5",
    "4.0",
    "4.5",
    "5.0",
    "5.5",
    "5.75",
    "6.0",
    "7.0",
    "8.0",
    "9.0",
    "9.5",
    "10.0",
    "10.5",
    "11.0",
    "12.0",
    "12.75",
    "13.0",
    "14.0",
).map { it to gmtLabel(it.toDouble()) }

internal fun gmtLabel(hours: Double): String {
    val totalMin = Math.round(kotlin.math.abs(hours) * 60).toInt()
    val sign = if (hours < 0) "-" else "+"
    val h = totalMin / 60
    val m = totalMin % 60
    return "GMT$sign$h" + if (m != 0) ":%02d".format(m) else ""
}

/** Nome do país na língua da app (os nomes em inglês ficam como recurso). */
internal fun countryName(code: String, fallback: String): String =
    java.util.Locale("", code).getDisplayCountry(co.golink.tester.ui.common.appLocale()).takeIf { it.isNotBlank() && it != code } ?: fallback

internal val COUNTRIES = listOf(
    "PT" to "Portugal", "ES" to "Spain", "FR" to "France", "DE" to "Germany",
    "IT" to "Italy", "GB" to "United Kingdom", "IE" to "Ireland", "NL" to "Netherlands",
    "BE" to "Belgium", "LU" to "Luxembourg", "CH" to "Switzerland", "AT" to "Austria",
    "DK" to "Denmark", "SE" to "Sweden", "NO" to "Norway", "FI" to "Finland",
    "PL" to "Poland", "CZ" to "Czech Republic", "SK" to "Slovakia", "HU" to "Hungary",
    "RO" to "Romania", "BG" to "Bulgaria", "GR" to "Greece", "HR" to "Croatia",
    "SI" to "Slovenia", "EE" to "Estonia", "LV" to "Latvia", "LT" to "Lithuania",
    "US" to "United States", "CA" to "Canada", "MX" to "Mexico", "BR" to "Brazil",
    "AR" to "Argentina", "CL" to "Chile", "CO" to "Colombia", "PE" to "Peru",
    "AU" to "Australia", "NZ" to "New Zealand", "JP" to "Japan", "KR" to "South Korea",
    "CN" to "China", "HK" to "Hong Kong", "TW" to "Taiwan", "SG" to "Singapore",
    "IN" to "India", "ID" to "Indonesia", "TH" to "Thailand", "VN" to "Vietnam",
    "PH" to "Philippines", "MY" to "Malaysia", "AE" to "United Arab Emirates",
    "SA" to "Saudi Arabia", "IL" to "Israel", "TR" to "Turkey", "EG" to "Egypt",
    "ZA" to "South Africa", "NG" to "Nigeria", "KE" to "Kenya", "MA" to "Morocco",
    "AO" to "Angola", "MZ" to "Mozambique", "CV" to "Cape Verde",
    "RU" to "Russia", "UA" to "Ukraine",
)
