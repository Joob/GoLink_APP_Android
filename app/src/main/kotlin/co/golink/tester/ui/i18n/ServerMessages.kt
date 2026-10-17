package co.golink.tester.ui.i18n

/**
 * O servidor (Laravel) devolve os erros de autenticação/validação em inglês.
 * Converte as mensagens conhecidas numa frase PT do dicionário e traduz para
 * a língua da app; mensagens desconhecidas passam como vêm.
 */
object ServerMessages {
    private data class Rule(val pattern: Regex, val pt: (MatchResult) -> String)

    private fun rule(pattern: String, pt: (MatchResult) -> String) =
        Rule(Regex(pattern, RegexOption.IGNORE_CASE), pt)

    private val rules = listOf(
        rule("""^The provided credentials are incorrect\.?$""") { "Email ou password incorrectos".tr() },
        rule("""^These credentials do not match our records\.?$""") { "Email ou password incorrectos".tr() },
        rule("""^We can'?t find a user with that e-?mail address\.?$""") { "Não existe nenhuma conta com este email".tr() },
        rule("""^We have e-?mailed your password reset link!?\.?$""") { "Enviámos o link para repor a password".tr() },
        rule("""^Please wait before retrying\.?$""") { "Aguarda um pouco antes de tentar de novo".tr() },
        rule("""^This password reset token is invalid\.?$""") { "O link de recuperação é inválido ou expirou".tr() },
        rule("""^Too Many (Attempts|Requests)\.?$""") { "Demasiadas tentativas. Tenta mais tarde.".tr() },
        rule("""^The e-?mail has already been taken\.?$""") { "Este email já está registado".tr() },
        rule("""^The e-?mail (field )?must be a valid e-?mail address\.?$""") { "Email inválido".tr() },
        rule("""^The e-?mail field is required\.?$""") { "Email obrigatório".tr() },
        rule("""^The password field is required\.?$""") { "Password obrigatória".tr() },
        rule("""^The name field is required\.?$""") { "Nome obrigatório".tr() },
        rule("""^The (\w+)( \w+)? field is required\.?$""") { "Campo obrigatório".tr() },
        rule("""^The password (field )?(confirmation does not match|must match)\.?$""") { "As passwords não coincidem".tr() },
        rule("""^The password (field )?(must be|must contain) at least (\d+) characters\.?$""") { m ->
            "A password deve ter pelo menos %s caracteres".tr().format(m.groupValues[3])
        },
        rule("""^The password (field )?must contain at least one uppercase and one lowercase letter\.?$""") {
            "A password deve ter letras maiúsculas e minúsculas".tr()
        },
        rule("""^The password (field )?must contain at least one (number|digit)\.?$""") { "A password deve ter pelo menos um número".tr() },
        rule("""^The password (field )?must contain at least one symbol\.?$""") { "A password deve ter pelo menos um símbolo".tr() },
        rule("""^The given password has appeared in a data leak.*$""") {
            "Esta password apareceu numa fuga de dados. Escolhe outra.".tr()
        },
        rule("""^The given data was invalid\.?$""") { "Dados inválidos".tr() },
    )

    fun localize(raw: String?): String? {
        val message = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return raw
        // Às vezes chegam várias frases: "X (and 1 more error)" → só a primeira.
        val first = message.replace(Regex("""\s*\(and \d+ more errors?\)$"""), "")
        rules.forEach { r -> r.pattern.matchEntire(first)?.let { return r.pt(it) } }
        // Mensagens de fallback do ApiErrorParser já são frases PT do dicionário.
        return first.tr()
    }
}
