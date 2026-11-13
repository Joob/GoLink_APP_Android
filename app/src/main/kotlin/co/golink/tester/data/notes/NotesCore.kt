package co.golink.tester.data.notes

import android.content.Context
import android.util.Log
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Núcleo das Notes (assets/notes-core.js, gerado no repo web por ops/notes-core):
 * Yjs + y-prosemirror com o esquema do editor da web, num QuickJS só nosso.
 * Os Y.Doc das notas abertas vivem lá dentro; a app passa updates em claro (base64)
 * e documentos ProseMirror em JSON. Rede e cifra ficam no Kotlin.
 *
 * O QuickJS não é thread-safe: tudo corre numa thread dedicada.
 */
@Singleton
class NotesCore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "notes-core").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val random = SecureRandom()

    private var js: QuickJSContext? = null
    private var api: JSObject? = null
    private val functions = HashMap<String, JSFunction>()

    /** Carrega o motor em segundo plano (1.ª nota aberta sem esperar ~100 ms). */
    fun warmUp() {
        scope.launch { runCatching { ensure() }.onFailure { Log.w(TAG, "warmUp falhou", it) } }
    }

    private fun ensure(): JSObject {
        api?.let { return it }
        QuickJSLoader.init()
        val ctx = QuickJSContext.create()
        // y-prosemirror é recursivo por nível do documento (listas aninhadas).
        ctx.setMaxStackSize(4 * 1024 * 1024)
        val global = ctx.globalObject
        global.setProperty("__ncRandom", JSCallFunction { args ->
            val n = (args.firstOrNull() as? Number)?.toInt()?.coerceIn(0, 4096) ?: 0
            val bytes = ByteArray(n).also(random::nextBytes)
            bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        })
        global.setProperty("__ncLog", JSCallFunction { args ->
            Log.d(TAG, args.firstOrNull()?.toString().orEmpty())
            null
        })
        val source = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        ctx.evaluate(source, ASSET)
        val nc = global.getJSObject("NotesCore") ?: error("NotesCore em falta no $ASSET")
        js = ctx
        api = nc
        return nc
    }

    private suspend fun call(name: String, vararg args: Any): String = withContext(dispatcher) {
        val fn = functions.getOrPut(name) { ensure().getJSFunction(name) ?: error("NotesCore.$name em falta") }
        fn.call(*args)?.toString().orEmpty()
    }

    // ---- API (espelho de resources/js/notes-core/index.js) -------------------

    suspend fun open(id: String) { call("open", id) }

    suspend fun close(id: String) { call("close", id) }

    /** Update remoto; devolve a mensagem de erro, ou null se entrou. */
    suspend fun apply(id: String, updateB64: String): String? = call("apply", id, updateB64).ifEmpty { null }

    /** Vários updates; devolve quantos falharam. */
    suspend fun applyMany(id: String, updatesB64: List<String>): Int =
        call("applyMany", id, JsonArray(updatesB64.map(::JsonPrimitive)).toString()).toIntOrNull() ?: 0

    /** Documento ProseMirror (JSON). */
    suspend fun doc(id: String): String = call("doc", id)

    /** Documento editado (JSON ProseMirror inteiro) → update local (base64) ou "" sem alterações. */
    suspend fun edit(id: String, docJson: String): String = call("edit", id, docJson)

    suspend fun take(id: String): String = call("take", id)

    suspend fun state(id: String): String = call("state", id)

    suspend fun emptyState(): String = call("emptyState")

    suspend fun merge(updatesB64: List<String>): String =
        call("merge", JsonArray(updatesB64.map(::JsonPrimitive)).toString())

    /** {meta, tasks, files} — o que a web grava (meta cifrado, índice de tarefas, ficheiros). */
    suspend fun derive(id: String): String = call("derive", id)

    suspend fun lines(id: String): String = call("lines", id)

    suspend fun diffLines(id: String): String = call("diffLines", id)

    suspend fun lineDiff(aJson: String, bJson: String): String = call("lineDiff", aJson, bJson)

    suspend fun tags(id: String): List<String> =
        runCatching { Json.parseToJsonElement(call("tags", id)) as JsonArray }.getOrNull()
            ?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

    suspend fun setTags(id: String, tags: List<String>): String =
        call("setTags", id, JsonArray(tags.map(::JsonPrimitive)).toString())

    suspend fun setTaskChecked(id: String, uid: String, checked: Boolean): String =
        call("setTaskChecked", id, uid, checked.toString())

    suspend fun newUid(): String = call("newUid")

    private companion object {
        const val TAG = "NotesCore"
        const val ASSET = "notes-core.js"
    }
}
