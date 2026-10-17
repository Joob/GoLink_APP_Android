package co.golink.tester.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

/** Erros do Laravel (em inglês) → frases PT do dicionário (língua por omissão: PT). */
class ServerMessagesTest {
    @Test
    fun knownMessagesAreLocalized() {
        assertEquals("Email ou password incorrectos", ServerMessages.localize("The provided credentials are incorrect."))
        assertEquals("Não existe nenhuma conta com este email", ServerMessages.localize("We can't find a user with that email address."))
        assertEquals("Este email já está registado", ServerMessages.localize("The email has already been taken."))
        assertEquals("Email inválido", ServerMessages.localize("The email field must be a valid email address."))
        assertEquals("A password deve ter pelo menos 8 caracteres", ServerMessages.localize("The password field must be at least 8 characters."))
        assertEquals("As passwords não coincidem", ServerMessages.localize("The password field confirmation does not match."))
        assertEquals("Email ou password incorrectos", ServerMessages.localize("The provided credentials are incorrect. (and 1 more error)"))
    }

    @Test
    fun unknownMessagesPassThrough() {
        assertEquals("Something else happened", ServerMessages.localize("Something else happened"))
    }
}
