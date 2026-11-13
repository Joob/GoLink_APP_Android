package co.golink.tester.data.notes

import co.golink.tester.data.encryption.CalendarCrypto
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.network.NotesApi
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Caixa de uma nota com data (sobreposta, só de leitura, no Calendário). */
data class NoteTaskItem(
    val noteId: String,
    val uid: String,
    val text: String,
    val noteTitle: String,
    val done: Boolean,
    val dueDate: LocalDate,
)

/**
 * Caixas datadas das Notes para o Calendário (como loadNoteTasks na web). A
 * K_note abre-se com a chave privada; fica em cache enquanto a wrapped_key não
 * mudar (o Calendário pede isto a cada recarga), e sai no lock.
 */
@Singleton
class NotesTasksRepository @Inject constructor(
    private val api: NotesApi,
    keys: E2EKeyManager,
    private val keyCache: NoteKeyCache,
) {
    // A chave vem da NoteKeyCache (a mesma das Notes, zerada lá no lock); aqui só o título.
    private data class NoteKey(val wrapped: String, val metaEnc: String?, val key: ByteArray, val title: String)

    private val cache = ConcurrentHashMap<String, NoteKey>()

    init {
        keys.addOnLock { cache.clear() }
    }

    suspend fun tasks(from: LocalDate, to: LocalDate): Result<List<NoteTaskItem>> = runCatching {
        withContext(Dispatchers.IO) {
            val res = api.tasks(from.toString(), to.toString())
            check(res.isSuccessful) { "HTTP ${res.code()}" }
            val body = res.body() ?: return@withContext emptyList()
            val noteKeys = HashMap<String, NoteKey?>()
            for (n in body["notes"]?.jsonArray.orEmpty()) {
                val o = n.jsonObject
                val id = o.str("id") ?: continue
                val wrapped = o.str("wrapped_key") ?: continue
                val metaEnc = o.str("meta_enc")
                val hit = cache[id]
                noteKeys[id] = if (hit != null && hit.wrapped == wrapped && hit.metaEnc == metaEnc) hit else {
                    keyCache.open(wrapped)?.let { key ->
                        val title = metaEnc?.let {
                            runCatching { NoteMeta.from(Json.parseToJsonElement(CalendarCrypto.decryptString(it, key)) as? JsonObject).title }.getOrNull()
                        }.orEmpty()
                        NoteKey(wrapped, metaEnc, key, title).also { cache[id] = it }
                    }
                }
            }
            body["data"]?.jsonArray.orEmpty().mapNotNull { t ->
                val o = t.jsonObject
                val noteId = o.str("note_id") ?: return@mapNotNull null
                val due = o.str("due_date")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                val k = noteKeys[noteId] ?: return@mapNotNull null
                val text = o.str("text_enc")?.let { runCatching { CalendarCrypto.decryptString(it, k.key) }.getOrNull() } ?: return@mapNotNull null
                NoteTaskItem(
                    noteId = noteId,
                    uid = o.str("uid").orEmpty(),
                    text = text,
                    noteTitle = k.title,
                    done = o["done"]?.jsonPrimitive?.booleanOrNull ?: false,
                    dueDate = due,
                )
            }
        }
    }

    private fun JsonObject.str(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
}
