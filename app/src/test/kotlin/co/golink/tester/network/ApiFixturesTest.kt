package co.golink.tester.network

import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.BrowseListResponse
import co.golink.tester.domain.browse.toItem
import co.golink.tester.domain.share.ShareResponseEnvelope
import co.golink.tester.domain.user.UserResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Respostas do servidor (fixtures em test/resources/api, com a forma dos
 * Resources do Laravel: FolderResource, FileResource, ShareResource,
 * UserResource ?compact=1) lidas pelos DTOs com a configuração do NetworkModule.
 * Sem MockWebServer (não está na cache offline do gradle): o que se testa é o
 * contrato JSON, não o transporte.
 */
class ApiFixturesTest {
    // A mesma configuração do NetworkModule.provideJson().
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("api/$name")!!.use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun browseFolderParsesFoldersFilesAndMeta() {
        val res = json.decodeFromString(BrowseListResponse.serializer(), fixture("browse-folder.json"))
        assertEquals(3, res.data.size)
        assertEquals(1, res.meta?.paginate?.current_page)
        assertEquals(1, res.meta?.paginate?.last_page)
        assertEquals(3, res.meta?.paginate?.total)
        assertEquals(2, res.meta?.stats?.files)
        assertEquals(2098176L, res.meta?.stats?.bytes)

        val folder = res.data[0].data.toItem() as BrowseItem.Folder
        assertEquals("🚀", folder.emoji)
        assertTrue(folder.isTeamFolder)
        assertEquals(true, folder.isTeamRoot)
        assertEquals("u-1", folder.ownerId)
        assertEquals(12582912L, folder.filesizeBytes)
        // O dono vinha 2× nos membros: fica só uma vez.
        assertEquals(listOf("u-1", "u-2"), folder.members.map { it.id })
        assertTrue(folder.members.first().online)

        val image = res.data[1].data.toItem() as BrowseItem.File
        assertTrue(image.encrypted)
        assertEquals("v1.abc", image.nameEncrypted)
        assertNotNull(image.thumbnailUrl)
        assertEquals(4032, image.imageWidth)
        assertEquals(3024, image.imageHeight) // EXIF em texto também conta
        val share = image.share!!
        assertEquals("tok123", share.token)
        assertTrue(share.protected)
        assertEquals(2, share.expireIn) // 25 h → 2 dias
        assertEquals(5, share.viewLimit)
        assertTrue(share.active)
    }

    @Test
    fun emptyRelationshipsArrayIsAccepted() {
        // FileResource sem nenhuma relação: o PHP manda "relationships": [].
        val res = json.decodeFromString(BrowseListResponse.serializer(), fixture("browse-folder.json"))
        val plain = res.data[2].data.toItem() as BrowseItem.File
        assertNull(plain.share)
        assertEquals("pending", plain.scanStatus)
        assertEquals(2, plain.contentRevision)
        assertNull(plain.thumbnailUrl)
    }

    @Test
    fun compactUserParses() {
        val user = json.decodeFromString(UserResponse.serializer(), fixture("user-compact.json")).data
        assertEquals("ana@x.pt", user.attributes.email)
        assertTrue(user.attributes.socialite_account)
        assertTrue(user.attributes.has_passkeys)
        assertFalse(user.attributes.is_suspended)
        assertEquals(true, user.attributes.support_staff?.can_assign)
        assertEquals(false, user.attributes.support_staff?.can_use_ai)
        assertEquals("PT", user.relationships?.settings?.data?.attributes?.holiday_country)
        assertEquals(1, user.relationships?.settings?.data?.attributes?.week_start)
        assertFalse(user.meta?.restrictions?.canCreateFolder ?: true)
        assertEquals(5368709120L, user.meta?.upload?.limit)
    }

    @Test
    fun createdShareWithNullPermissionParses() {
        // permission null = só leitura (servidor), expire_in 0 = sem validade.
        val share = json.decodeFromString(ShareResponseEnvelope.serializer(), fixture("share-created.json")).data
        assertEquals("abc", share.attributes.token)
        assertNull(share.attributes.permission)
        assertEquals(0, share.attributes.expire_in)
        assertEquals("f-1", share.attributes.item_id)
    }
}
