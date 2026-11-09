package co.golink.tester.network

import co.golink.tester.domain.admin.ChangeRoleRequest
import co.golink.tester.domain.admin.CreateUserRequest
import co.golink.tester.domain.admin.RoleAttributes
import co.golink.tester.domain.files.CreateFolderRequest
import co.golink.tester.domain.files.RenameItemRequest
import co.golink.tester.domain.settings.CreateTokenRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Corpos enviados contra as regras dos FormRequests do servidor. Os achados
 * A10#1–#3 (422 em produção) eram todos apanháveis aqui.
 */
class ApiContractTest {
    // A mesma configuração do NetworkModule.provideJson().
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private inline fun <reified T> body(value: T): JsonObject =
        json.parseToJsonElement(json.encodeToString(value)).jsonObject

    @Test
    fun createFolderSendsEmojiAsObject() {
        // CreateFolderRequest.php: emoji => nullable|array, emoji.char => string.
        val emoji = buildJsonObject { put("codes", "1F600"); put("char", "😀"); put("name", "😀") }
        val b = body(CreateFolderRequest(name = "Fotos", parent_id = null, emoji = emoji))
        assertEquals("😀", b["emoji"]!!.jsonObject["char"]!!.jsonPrimitive.content)
        assertEquals("Fotos", b["name"]!!.jsonPrimitive.content)
        // Sem emoji, sem cor e sem pai: os campos não vão (explicitNulls = false).
        val plain = body(CreateFolderRequest(name = "Fotos"))
        assertFalse(plain.containsKey("emoji"))
        assertFalse(plain.containsKey("color"))
        assertFalse(plain.containsKey("parent_id"))
    }

    @Test
    fun renameClearsAppearanceWithExplicitNull() {
        // Rename: JsonNull limpa (volta ao tema); ausente = não mexe.
        val cleared = body(RenameItemRequest(name = "x", type = "folder", color = JsonNull, emoji = JsonNull))
        assertEquals(JsonNull, cleared["emoji"])
        assertEquals(JsonNull, cleared["color"])
        val untouched = body(RenameItemRequest(name = "x", type = "file"))
        assertFalse(untouched.containsKey("emoji"))
        assertFalse(untouched.containsKey("color"))
    }

    @Test
    fun createTokenCarriesStepUp() {
        // CreateAccessTokenRequest.php: password ou passkey_token (create_token).
        val withPassword = body(CreateTokenRequest(name = "CLI", password = "secret"))
        assertEquals("secret", withPassword["password"]!!.jsonPrimitive.content)
        assertFalse(withPassword.containsKey("passkey_token"))
        val withPasskey = body(CreateTokenRequest(name = "CLI", passkey_token = "pk"))
        assertEquals("pk", withPasskey["passkey_token"]!!.jsonPrimitive.content)
        assertFalse(withPasskey.containsKey("password"))
    }

    @Test
    fun changeRoleNestsRoleAndCarriesStepUp() {
        // ChangeRoleRequest.php: attributes.role in [admin,user]; promoção pede password/passkey.
        val promote = body(ChangeRoleRequest(RoleAttributes("admin"), password = "secret"))
        assertEquals("admin", promote["attributes"]!!.jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("secret", promote["password"]!!.jsonPrimitive.content)
        val demote = body(ChangeRoleRequest(RoleAttributes("user")))
        assertEquals(setOf("attributes"), demote.keys)
    }

    @Test
    fun createAdminUserUsesAdminPassword() {
        // CreateUserByAdmin: `password` é a da conta nova; o step-up vai em admin_password.
        val b = body(CreateUserRequest("Ana", "ana@x.pt", "newpass12", "newpass12", "admin", admin_password = "mine"))
        assertEquals("newpass12", b["password"]!!.jsonPrimitive.content)
        assertEquals("mine", b["admin_password"]!!.jsonPrimitive.content)
        assertTrue(b["role"] is JsonPrimitive)
    }
}
