package co.golink.tester.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

/** Erros do Laravel (em inglês) → frases do dicionário na língua atual (por omissão: EN). */
class ServerMessagesTest {
    @Test
    fun knownMessagesAreLocalized() {
        assertEquals(I18n.t("Email ou password incorrectos"), ServerMessages.localize("The provided credentials are incorrect."))
        assertEquals(I18n.t("Não existe nenhuma conta com este email"), ServerMessages.localize("We can't find a user with that email address."))
        assertEquals(I18n.t("Este email já está registado"), ServerMessages.localize("The email has already been taken."))
        assertEquals(I18n.t("Email inválido"), ServerMessages.localize("The email field must be a valid email address."))
        assertEquals(I18n.t("A password deve ter pelo menos %s caracteres").format("8"), ServerMessages.localize("The password field must be at least 8 characters."))
        assertEquals(I18n.t("As passwords não coincidem"), ServerMessages.localize("The password field confirmation does not match."))
        assertEquals(I18n.t("Email ou password incorrectos"), ServerMessages.localize("The provided credentials are incorrect. (and 1 more error)"))
    }

    @Test
    fun unknownMessagesPassThrough() {
        assertEquals("Something else happened", ServerMessages.localize("Something else happened"))
    }
}
