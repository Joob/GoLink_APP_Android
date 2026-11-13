package co.golink.tester.domain.activity

import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.toItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Formato devolvido por GET api/activity/recent (mesma config Json do NetworkModule). */
class ActivityDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val body = """
        {"data":{
          "my_uploads":{"items":[{
            "id":"a1","action":"uploaded","item_type":"file","is_child":false,"is_mine":true,
            "created_at":"2026-10-01T10:00:00+00:00","metadata":{"source":"web","via":"share","permission":"can-edit"},
            "actor":{"id":"u1","name":"Ana","avatar":null,"color":"#00D98F"},
            "item_owner_id":"u1","item_trashed":false,
            "item":{"data":{"id":"f1","type":"image","attributes":{"name":"•","name_encrypted":"CIPHER","encrypted":true},
              "relationships":{"parent":{"data":{"type":"folder","id":"p1","attributes":{"name":"Fotos","name_encrypted":null}}}}}}
          }],"has_more":true,"page":1},
          "share_activity":{"items":[{"id":"a2","action":"downloaded","actor":null,"metadata":null,"item":null}],"has_more":false,"page":1}
        }}
    """.trimIndent()

    @Test
    fun parsesRecentActivity() {
        val res = json.decodeFromString(RecentActivityResponse.serializer(), body)
        val section = res.data.getValue(ActivitySection.MY_UPLOADS.key)
        assertTrue(section.has_more)

        val row = section.items.single()
        val item = row.item!!.data
        assertEquals("CIPHER", item.attributes.name_encrypted)
        assertEquals("Fotos", item.relationships?.parent?.data?.attributes?.name)

        val entry = row.toEntry(item.toItem(), parentName = "Fotos")
        assertEquals("share", entry.via)
        assertEquals("can-edit", entry.permission)
        assertTrue(entry.isMine)
        assertTrue(entry.hasActor)
        assertTrue(entry.item is BrowseItem.File)
    }

    @Test
    fun visitorRowWithoutItemOrMetadata() {
        val res = json.decodeFromString(RecentActivityResponse.serializer(), body)
        val row = res.data.getValue(ActivitySection.SHARE_ACTIVITY.key).items.single()
        val entry = row.toEntry(null, null)
        assertFalse(entry.hasActor)
        assertNull(entry.via)
        assertNull(entry.item)
    }
}
