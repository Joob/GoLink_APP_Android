package co.golink.tester.data.pass

import java.net.URI
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Resultado da análise (igual a analyse() de resources/js/services/pass/health.js + score). */
data class PassHealthResult(
    val byItem: Map<String, Flags>,
    val lists: Map<String, List<String>>,
    val checked: Int,
    val issues: Int,
    val flagged: Int,
    val score: Int,
    val unchecked: Int,
) {
    data class Flags(val reused: Boolean, val weak: Boolean, val breach: Boolean, val twofa: Boolean, val print: String) {
        fun any() = reused || weak || breach || twofa
        fun has(kind: String) = when (kind) {
            "reused" -> reused
            "weak" -> weak
            "breach" -> breach
            "twofa" -> twofa
            else -> false
        }
    }

    /** Selo a mostrar na lista (prioridade: breach > reused > weak). */
    fun badgeOf(id: String): String? = byItem[id]?.let { f ->
        when {
            f.breach -> "breach"
            f.reused -> "reused"
            f.weak -> "weak"
            else -> null
        }
    }
}

/**
 * Password health no aparelho: reused / weak / breach (k-anonymity pelo proxy
 * /api/pass/breach, só 5 hex do SHA-1) / twofa. "Mark as fixed" grava
 * health_ignore[kind] = impressão da password atual.
 */
class PassHealth(
    private val fetchRange: suspend (prefix: String) -> String,
    private val loadTwofa: suspend () -> Set<String>,
) {
    companion object {
        val KINDS = listOf("reused", "weak", "breach", "twofa")
        private const val MIN_GAP_MS = 210L // proxy /pass/breach: throttle 300/min
        private const val FAIL_TTL_MS = 10 * 60 * 1000L

        fun sha1Hex(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }

        /** Impressão local curta (nunca sai do aparelho). */
        fun passwordPrint(pw: String): String = if (pw.isEmpty()) "" else sha1Hex("golink-pass:$pw").substring(0, 16)

        /** Pontuação 0–100 = logins sem problemas / verificados (igual a healthScore da web). */
        fun score(checked: Int, flagged: Int): Int = if (checked == 0) 100 else (100.0 * (checked - flagged) / checked).roundToInt()

        fun hostOf(url: String?): String {
            if (url.isNullOrBlank()) return ""
            val full = if (Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(url)) url else "https://$url"
            return runCatching { URI(full.trim()).host?.lowercase()?.removePrefix("www.") }.getOrNull().orEmpty()
        }

        fun supports2fa(host: String, domains: Set<String>): Boolean {
            if (host.isEmpty()) return false
            val parts = host.split('.')
            for (i in 0 until parts.size - 1) if (parts.subList(i, parts.size).joinToString(".") in domains) return true
            return false
        }

        /** Procura o sufixo no corpo "SUFIXO35:contagem" (igual a breachCount). */
        fun countIn(body: String, suffix: String): Int {
            val at = body.indexOf("$suffix:")
            if (at < 0 || (at > 0 && body[at - 1] != '\n')) return 0
            val rest = body.substring(at + suffix.length + 1).takeWhile { it.isDigit() }
            return rest.take(11).toIntOrNull() ?: 1
        }
    }

    private val breachCache = HashMap<String, Int>()
    private val itemMemo = HashMap<String, Pair<String, Int>>()
    private val failedAt = HashMap<String, Long>()
    private var breakerUntil = 0L
    private var nextSlot = 0L
    private val slotLock = Mutex()
    private val cacheLock = Mutex()
    private val inflight = HashMap<String, kotlinx.coroutines.Deferred<String>>()

    fun clear() {
        breachCache.clear(); itemMemo.clear(); failedAt.clear(); inflight.clear(); breakerUntil = 0
    }

    /** "Recheck": volta a perguntar ao HIBP (esquece contagens, prefixos falhados e o disjuntor). */
    suspend fun clearBreach() = cacheLock.withLock {
        breachCache.clear(); failedAt.clear(); breakerUntil = 0
    }

    private suspend fun takeSlot() {
        val wait = slotLock.withLock {
            val now = System.currentTimeMillis()
            val at = maxOf(now, nextSlot)
            nextSlot = at + MIN_GAP_MS
            at - now
        }
        if (wait > 0) delay(wait)
    }

    private suspend fun fetchWithRetry(prefix: String): String {
        var attempt = 0
        while (true) {
            takeSlot()
            try {
                return fetchRange(prefix)
            } catch (e: PassHttpException) {
                if (e.status != 429 || attempt >= 4) {
                    if (e.status == 429) breakerUntil = System.currentTimeMillis() + 60_000
                    throw e
                }
                val wait = e.retryAfter?.takeIf { it > 0 } ?: (2 shl attempt)
                delay(minOf(60, wait) * 1000L)
                attempt++
            }
        }
    }

    private suspend fun breachCount(pw: String): Int = coroutineScope {
        val hash = sha1Hex(pw)
        val prefix = hash.substring(0, 5)
        val suffix = hash.substring(5)
        val job = cacheLock.withLock {
            inflight[prefix] ?: run {
                val failed = failedAt[prefix]
                val now = System.currentTimeMillis()
                if ((failed != null && now - failed < FAIL_TTL_MS) || now < breakerUntil) throw IllegalStateException("breach check skipped")
                async {
                    try {
                        fetchWithRetry(prefix)
                    } catch (e: Exception) {
                        cacheLock.withLock { failedAt[prefix] = System.currentTimeMillis() }
                        throw e
                    } finally {
                        cacheLock.withLock { inflight.remove(prefix) }
                    }
                }.also { inflight[prefix] = it }
            }
        }
        countIn(job.await(), suffix)
    }

    suspend fun analyse(items: List<PassItem>, withBreach: Boolean = true): PassHealthResult {
        val logins = items.filter { it.type == "login" && !it.isTrashed && !it.locked && !it.login?.password.isNullOrEmpty() }
        val prints = HashMap<String, String>()
        val scores = HashMap<String, Int>()
        val seen = HashSet<String>()
        for (i in logins) {
            val key = "${i.id}:${i.revision}"
            seen += key
            val m = itemMemo.getOrPut(key) { passwordPrint(i.login!!.password) to PassStrength.of(i.login!!.password).score }
            prints[i.id] = m.first
            scores[i.id] = m.second
        }
        itemMemo.keys.retainAll(seen)

        val groups = logins.groupBy { prints.getValue(it.id) }
        val domains = runCatching { loadTwofa() }.getOrDefault(emptySet())
        val byItem = java.util.concurrent.ConcurrentHashMap<String, PassHealthResult.Flags>()
        var unchecked = 0
        val counterLock = Mutex()
        var next = 0

        coroutineScope {
            (1..4).map {
                async {
                    while (true) {
                        val i = counterLock.withLock { if (next < logins.size) logins[next++] else null } ?: break
                        val print = prints.getValue(i.id)
                        val ignore = i.healthIgnore
                        fun ignored(kind: String) = ignore[kind] == print
                        var breach = false
                        if (withBreach && !ignored("breach")) {
                            try {
                                val cached = cacheLock.withLock { breachCache[print] }
                                val count = cached ?: breachCount(i.login!!.password).also { c -> cacheLock.withLock { breachCache[print] = c } }
                                breach = count > 0
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                counterLock.withLock { unchecked++ }
                            }
                        }
                        byItem[i.id] = PassHealthResult.Flags(
                            reused = groups.getValue(print).size > 1 && !ignored("reused"),
                            weak = scores.getValue(i.id) < 3 && !ignored("weak"),
                            breach = breach,
                            twofa = i.totp == null && supports2fa(hostOf(i.login?.url), domains) && !ignored("twofa"),
                            print = print,
                        )
                    }
                }
            }.awaitAll()
        }

        val lists = KINDS.associateWith { k -> logins.filter { byItem[it.id]?.has(k) == true }.map { it.id } }
        val issues = lists.values.sumOf { it.size }
        val flagged = byItem.values.count { it.any() }
        return PassHealthResult(byItem.toMap(), lists, logins.size, issues, flagged, score(logins.size, flagged), unchecked)
    }
}
