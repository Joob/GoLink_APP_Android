package co.golink.tester.data.receipts

import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Os portes Kotlin têm de dar exatamente o mesmo que os services/receipts da web.
 * Fixtures geradas na web: `node ops/receipts/fixtures.mjs` (tester.golink.co).
 */
class ReceiptsLogicTest {
    private val fx: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("receipts-fixtures.json")!!.readText(),
    ).jsonObject

    private val today = LocalDate.of(2026, 11, 11)
    private val now = LocalDateTime.of(2026, 11, 11, 15, 0)

    private val household = fx["household"]!!.jsonArray.map {
        val o = it.jsonObject
        Member(o.s("id"), o.s("name"), o.s("nif"), o.s("role"))
    }
    private val budgets: Map<String, Budget> = fx["budgets"]!!.jsonObject.mapValues { (_, v) ->
        val o = v.jsonObject
        Budget(o["amount"]!!.jsonPrimitive.long, o["warn"]!!.jsonPrimitive.int, o["rollover"]!!.jsonPrimitive.boolean)
    }
    private val receipts: List<Receipt> = fx["receipts"]!!.jsonArray.map {
        val o = it.jsonObject
        Receipt(
            id = o.s("id"), revision = 1, year = o.s("date").take(4).toIntOrNull(), hasPhoto = false,
            deletedAt = (o["deleted_at"] as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content,
            createdAt = o.s("created_at"), updatedAt = null, enc = "", payload = o, locked = false,
        )
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()
    private fun JsonElement.l(): Long = jsonPrimitive.long
    private fun JsonElement.d(): Double = jsonPrimitive.double
    private fun near(msg: String, a: Double, b: Double) = assertEquals(msg, a, b, 1e-6)
    private fun longs(el: JsonElement?) = el!!.jsonArray.map { it.l() }

    @Test
    fun nif() {
        for (c in fx["nif"]!!.jsonArray) {
            val (n, ok) = c.jsonArray
            assertEquals(n.jsonPrimitive.content, ok.jsonPrimitive.boolean, AtQr.validNif(n.jsonPrimitive.content))
        }
        for (c in fx["nifFormat"]!!.jsonArray) {
            val (n, mask, spaced) = c.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(mask, RcFormat.maskNif(n))
            assertEquals(spaced, RcFormat.spacedNif(n))
        }
    }

    @Test
    fun qr() {
        for (c in fx["qr"]!!.jsonArray) {
            val q = c.jsonArray[0].jsonPrimitive.content
            val exp = c.jsonArray[1] as? JsonObject
            val got = AtQr.parse(q)
            if (exp == null) {
                assertNull(q, got)
                continue
            }
            got!!
            assertEquals(q, exp.s("merchant_nif"), got.merchantNif)
            assertEquals(q, exp.s("buyer_nif"), got.buyerNif)
            assertEquals(q, exp.s("buyer_country"), got.buyerCountry)
            assertEquals(q, exp.s("doc_type"), got.docType)
            assertEquals(q, exp.s("date"), got.date)
            assertEquals(q, exp.s("doc_no"), got.docNo)
            assertEquals(q, exp.s("atcud"), got.atcud)
            assertEquals(q, exp["total"]!!.l(), got.total)
            assertEquals(q, exp["vat"]!!.l(), got.vat)
            assertEquals(q, exp["vat_exempt"]!!.jsonPrimitive.boolean, got.vatExempt)
            assertEquals(q, exp["vat_lines"]!!.jsonArray.map { l -> l.jsonObject.let { Triple(it.s("rate"), it["base"]!!.l(), it["vat"]!!.l()) } }, got.vatLines.map { Triple(it.rate, it.base, it.vat) })
            assertEquals(q, exp["filled"]!!.jsonArray.map { it.jsonPrimitive.content }, got.filled)
            val rate = exp["main_rate"]!!.jsonPrimitive.content
            assertEquals(q, if (rate == "null") null else rate.toInt(), AtQr.mainVatRate(got.vatLines, got.vat))
        }
    }

    @Test
    fun ocr() {
        for (c in fx["ocr"]!!.jsonArray) {
            val text = c.jsonArray[0].jsonPrimitive.content
            val exp = c.jsonArray[1].jsonObject
            val got = OcrParse.parse(text, today)
            val nl = { k: String -> (exp[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.long }
            assertEquals(text, exp.s("merchant"), got.merchant)
            assertEquals(text, exp.s("merchant_nif"), got.merchantNif)
            assertEquals(text, exp.s("buyer_nif"), got.buyerNif)
            assertEquals(text, exp.s("date"), got.date)
            assertEquals(text, exp.s("time"), got.time)
            assertEquals(text, nl("total"), got.total)
            assertEquals(text, nl("vat"), got.vat)
            assertEquals(text, exp.s("atcud"), got.atcud)
            assertEquals(text, exp.s("doc_no"), got.docNo)
            assertEquals(text, exp.s("doc_type"), got.docType)
            assertEquals(text, exp["filled"]!!.jsonArray.map { it.jsonPrimitive.content }, got.filled)
        }
    }

    @Test
    fun money() {
        for (c in fx["money"]!!.jsonArray) {
            val a = c.jsonArray
            val cents = a[0].l()
            assertEquals(a[1].jsonPrimitive.content, RcFormat.money(cents))
            assertEquals(a[2].jsonPrimitive.content, RcFormat.money(cents, whole = true))
            assertEquals(a[3].jsonPrimitive.content, RcFormat.money(cents, sign = true))
        }
        for (c in fx["parseMoney"]!!.jsonArray) {
            val (s, v) = c.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(s, if (v == "null") null else v.toLong(), RcFormat.parseMoney(s))
        }
    }

    @Test
    fun categories() {
        for (c in fx["suggest"]!!.jsonArray) {
            val name = c.jsonArray[0].jsonPrimitive.content
            val exp = c.jsonArray[1] as? JsonArray
            val got = RcCategories.suggest(name, "", emptyList())
            if (exp == null) assertNull(name, got)
            else assertEquals(name, exp.map { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }, listOf(got!!.spend, got.irs, got.multi.toString(), got.kind))
        }
        for (c in fx["initials"]!!.jsonArray) {
            val (n, i) = c.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(n, i, RcCategories.initialOf(n))
        }
        for (c in fx["suggestBudget"]!!.jsonArray) {
            val (a, s) = c.jsonArray.map { it.l() }
            assertEquals(s, Budgets.suggestBudget(a))
        }
    }

    @Test
    fun efStatusAndLost() {
        val exp = fx["efStatus"]!!.jsonArray.map { it.jsonPrimitive.content }
        receipts.forEachIndexed { i, r -> assertEquals(r.id, exp[i], IrsRules.efStatus(r, household, now)) }
        val empty = listOf(Member("me", "", "", "taxpayer"))
        fx["efStatusEmptyHousehold"]!!.jsonArray.forEachIndexed { i, e -> assertEquals(e.jsonPrimitive.content, IrsRules.efStatus(receipts[i], empty, now)) }
        fx["lost"]!!.jsonArray.forEachIndexed { i, e -> assertEquals(e.l(), IrsRules.lostBenefit(receipts[i], 2026)) }
    }

    private fun checkEstimate(tag: String, exp: JsonObject, got: IrsRules.Estimate) {
        assertEquals(tag, exp["total"]!!.l(), got.total)
        assertEquals(tag, exp["rawTotal"]!!.l(), got.rawTotal)
        assertEquals(tag, exp["expenses"]!!.l(), got.expenses)
        assertEquals(tag, exp["count"]!!.jsonPrimitive.int, got.count)
        exp["rows"]!!.jsonArray.forEachIndexed { i, el ->
            val e = el.jsonObject
            val g = got.rows[i]
            val t = "$tag/${g.key}"
            assertEquals(t, e.s("key"), g.key)
            assertEquals(t, e["spent"]!!.l(), g.spent)
            assertEquals(t, e["vat"]!!.l(), g.vat)
            assertEquals(t, e["count"]!!.jsonPrimitive.int, g.count)
            near(t, e["raw"]!!.d(), g.raw)
            assertEquals(t, e["cap"]!!.l(), g.cap)
            assertEquals(t, e["deduction"]!!.l(), g.deduction)
            assertEquals(t, e["reached"]!!.jsonPrimitive.boolean, g.reached)
            assertEquals(t, e["leftToLimit"]!!.l(), g.leftToLimit)
            near(t, e["fill"]!!.d(), g.fill)
        }
    }

    @Test
    fun estimate() {
        for (c in fx["estimate"]!!.jsonArray) {
            val m = c.jsonArray[0].jsonPrimitive.content
            checkEstimate(m, c.jsonArray[1].jsonObject, IrsRules.estimate(receipts, 2026, household, m, now))
        }
        checkEstimate("2025", fx["estimate2025"]!!.jsonObject, IrsRules.estimate(receipts, 2025, household, now = now))
    }

    private fun checkBudgets(tag: String, exp: JsonObject, got: Budgets.Summary) {
        assertEquals(tag, exp["totalBudget"]!!.l(), got.totalBudget)
        assertEquals(tag, exp["totalSpent"]!!.l(), got.totalSpent)
        assertEquals(tag, exp["projected"]!!.l(), got.projected)
        assertEquals(tag, exp["spare"]!!.l(), got.spare)
        assertEquals(tag, exp["safePerDay"]!!.l(), got.safePerDay)
        val p = exp["prog"]!!.jsonObject
        assertEquals(tag, listOf(p["day"]!!.jsonPrimitive.int, p["total"]!!.jsonPrimitive.int, p["left"]!!.jsonPrimitive.int), listOf(got.prog.day, got.prog.total, got.prog.left))
        exp["cards"]!!.jsonArray.forEachIndexed { i, el ->
            val e = el.jsonObject
            val g = got.cards[i]
            val t = "$tag/${g.cat}"
            assertEquals(t, e.s("cat"), g.cat)
            assertEquals(t, e["budget"]!!.l(), g.budget)
            assertEquals(t, e["spent"]!!.l(), g.spent)
            assertEquals(t, e["left"]!!.l(), g.left)
            assertEquals(t, e["perDay"]!!.l(), g.perDay)
            near(t, e["pace"]!!.d(), g.pace)
            assertEquals(t, e.s("status"), g.status)
            assertEquals(t, e["within"]!!.jsonPrimitive.int, g.within)
            assertEquals(t, e["avg"]!!.l(), g.avg)
            assertEquals(t, e["history"]!!.jsonArray.map { h -> h.jsonObject.let { listOf(it.s("ym"), it["spent"]!!.l().toString(), it["budget"]!!.l().toString()) } }, g.history.map { listOf(it.ym, it.spent.toString(), it.budget.toString()) })
        }
        assertEquals(tag, exp["cards"]!!.jsonArray.size, got.cards.size)
        assertEquals(tag, exp["without"]!!.jsonArray.map { w -> w.jsonObject.let { listOf(it.s("cat"), it["avg"]!!.l().toString(), it["suggest"]!!.l().toString()) } }, got.without.map { listOf(it.cat, it.avg.toString(), it.suggest.toString()) })
    }

    @Test
    fun budgets() {
        checkBudgets("2026-11", fx["budgets2611"]!!.jsonObject, Budgets.summary(receipts, budgets, "2026-11", today))
        checkBudgets("2026-10", fx["budgets2610"]!!.jsonObject, Budgets.summary(receipts, budgets, "2026-10", today))
        val t = fx["track"]!!.jsonObject
        val got = Budgets.trackRecord(receipts, budgets, 2026, today)
        assertEquals(t["months"]!!.jsonArray.map { it.jsonObject.s("state") }, got.months.map { it.state })
        assertEquals(t["within"]!!.jsonPrimitive.int, got.within)
        assertEquals(t["total"]!!.jsonPrimitive.int, got.total)
        val alerts = Budgets.pendingAlerts(receipts, budgets, "2026-11", mapOf("2026-11:groceries:close" to 1), ReceiptsSettings())
        assertEquals(fx["alerts"]!!.jsonArray.map { it.jsonObject.s("key") }, alerts.map { it.key })
        assertEquals(fx["years"]!!.jsonArray.map { it.jsonPrimitive.int }, Aggregate.yearsWithReceipts(receipts, today))
    }

    @Test
    fun spending() {
        for (c in fx["spending"]!!.jsonArray) {
            val o = c.jsonObject
            val kind = o.s("kind")
            val anchor = LocalDate.parse(o.s("anchor"))
            val ws = o["weekStart"]!!.jsonPrimitive.int
            val e = o["result"]!!.jsonObject
            val tag = "$kind ${o.s("anchor")} ws=$ws"
            val g = Aggregate.spendingSummary(receipts, kind, anchor, household, budgets, ws, today, now)
            assertEquals(tag, e.s("start"), RcFormat.ymd(g.range.start))
            assertEquals(tag, e.s("end"), RcFormat.ymd(g.range.end))
            assertEquals(tag, e["ids"]!!.jsonArray.map { it.jsonPrimitive.content }, g.list.map { it.id })
            assertEquals(tag, e["spent"]!!.l(), g.spent)
            assertEquals(tag, e["prevSpent"]!!.l(), g.prevSpent)
            val delta = (e["delta"] as? JsonPrimitive)?.doubleOrNull
            if (delta == null) assertNull(tag, g.delta) else near(tag, delta, g.delta!!)
            assertEquals(tag, e["days"]!!.jsonPrimitive.int, g.days)
            assertEquals(tag, e["dailyAvg"]!!.l(), g.dailyAvg)
            assertEquals(tag, e["scannedToday"]!!.jsonPrimitive.int, g.scannedToday)
            assertEquals(
                tag,
                e["cats"]!!.jsonArray.map { x -> x.jsonObject.let { listOf(it.s("key"), it["spent"]!!.l(), it["count"]!!.jsonPrimitive.int, it["irs"]!!.jsonArray.map { i -> i.jsonPrimitive.content }, (it["budget"] as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.long) } },
                g.cats.map { listOf(it.key, it.spent, it.count, it.irs, it.budget) },
            )
            e["cats"]!!.jsonArray.forEachIndexed { i, x -> near(tag, x.jsonObject["pct"]!!.d(), g.cats[i].pct) }
            assertEquals(tag, (e["biggest"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content, g.biggest?.key)
            val irs = e["irs"]!!.jsonObject
            assertEquals(tag, irs["spent"]!!.l(), g.irs.spent)
            assertEquals(tag, irs["raw"]!!.l(), g.irs.raw)
            assertEquals(tag, irs["yearEstimate"]!!.l(), g.irs.yearEstimate)
            assertEquals(tag, irs["chips"]!!.jsonObject.mapValues { it.value.l() }, g.irs.chips)
            assertEquals(tag, e["places"]!!.jsonArray.map { p -> p.jsonArray.let { listOf(it[0].jsonPrimitive.content, it[1].l(), it[2].l()) } }, g.places.map { listOf(it.key, it.total, it.visits.toLong()) })
            e["points"]!!.jsonArray.forEachIndexed { i, p ->
                assertEquals(tag, p.jsonArray[0].jsonPrimitive.content, g.points[i].id)
                near(tag, p.jsonArray[1].d(), g.points[i].hour)
                assertEquals(tag, p.jsonArray[2].l(), g.points[i].total)
            }
            assertEquals(tag, e["points"]!!.jsonArray.size, g.points.size)
            assertEquals(
                tag,
                e["bars"]!!.jsonArray.map { b -> b.jsonArray.let { listOf(it[0].l(), it[1].jsonArray.map { s -> s.jsonArray[0].jsonPrimitive.content to s.jsonArray[1].l() }, it[2].jsonPrimitive.booleanOrNull) } },
                g.bars.map { listOf(it.total, it.segments.map { s -> s.key to s.total }, it.future || it.forecast) },
            )
            assertEquals(tag, e["avg"]!!.l(), g.avg)
            assertEquals(tag, longs(e["cells"]), g.cells.map { it.total })
            assertEquals(tag, longs(e["levels"]), g.levels)
            assertEquals(tag, (e["decForecast"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.long, g.decForecast)
        }
    }
}
