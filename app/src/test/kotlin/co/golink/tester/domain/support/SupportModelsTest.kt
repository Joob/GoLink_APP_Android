package co.golink.tester.domain.support

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** O PHP serializa relationships vazias como `[]`: a lista do Support Desk não pode rebentar. */
class SupportModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun ticketPageAcceptsEmptyArrayRelationships() {
        val body = """
            {"data":[{"data":{"id":"t1","type":"support-ticket",
              "attributes":{"subject":"Ajuda","status":"open","priority":"high","category":"billing","messages_count":2,"unread_count":1},
              "requester":{"member_since":null,"tickets_count":3,"is_suspended":false,"plan":null},
              "relationships":[]}}],
             "meta":{"current_page":1,"last_page":1}}
        """.trimIndent()
        val page = json.decodeFromString(TicketPage.serializer(), body)
        assertEquals("t1", page.data.single().data.id)
        assertTrue(page.data.single().data.relationships?.messages.orEmpty().isEmpty())
    }

    @Test
    fun ticketAcceptsObjectRelationshipsWithMessages() {
        val body = """
            {"data":{"id":"t1","attributes":{"subject":"Ajuda"},
              "relationships":{"messages":[{"data":{"id":"m1","attributes":{"body":"Olá","author_role":"user"},"relationships":[]}}]}}}
        """.trimIndent()
        val ticket = json.decodeFromString(TicketEnvelope.serializer(), body)
        assertEquals("m1", ticket.data.relationships!!.messages.single().data.id)
    }

    @Test
    fun dashboardParses() {
        val body = """
            {"range":30,"kpis":{"awaiting_support":4,"unassigned":2},"times":{"avg_resolution_hours":3.5,"responded_within_24h_pct":80},
             "series":{"labels":["01/10","02/10"],"created":[1,2],"closed":[0,1]},
             "by_category":{"billing":1,"technical":2,"other":0},"by_priority":{"low":0,"normal":2,"high":1},
             "stale_tickets":[],"agents":null}
        """.trimIndent()
        val d = json.decodeFromString(SupportDashboard.serializer(), body)
        assertEquals(4, d.kpis.awaiting_support)
        assertEquals(3.5, d.times.avg_resolution_hours!!, 0.0)
        assertEquals(2, d.by_category["technical"])
    }
}
