package co.golink.tester.data.receipts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Preferências locais dos Recibos (só neste aparelho). */
@Singleton
class ReceiptsPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("receipts_prefs", Context.MODE_PRIVATE)

    /** Notificações com valores só depois de desbloquear (o ecrã bloqueado mostra a versão pública). */
    var hideAmountsOnLock: Boolean
        get() = prefs.getBoolean("hide_amounts_lock", false)
        set(v) = prefs.edit().putBoolean("hide_amounts_lock", v).apply()

    /** Semana (data do domingo, YYYY-MM-DD) do último resumo semanal local. */
    var weeklyShownFor: String?
        get() = prefs.getString("weekly_shown_for", null)
        set(v) = prefs.edit().putString("weekly_shown_for", v).apply()

    /** Espelho de settings.weekly para o worker decidir sem rede. */
    var weeklyEnabled: Boolean
        get() = prefs.getBoolean("weekly_enabled", false)
        set(v) = prefs.edit().putBoolean("weekly_enabled", v).apply()

    fun clear() = prefs.edit().clear().apply()
}

/**
 * Cache em disco SÓ com o que o servidor guarda (tudo cifrado com K_rcpt, que
 * por sua vez vai selada à pubkey E2E): arranque rápido e resumo semanal sem
 * rede. Nada em claro. Apagada no lock/logout.
 */
@Singleton
class ReceiptsCache @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(File(context.filesDir, "receipts"), "vault.json")
    private val json = Json { ignoreUnknownKeys = true }

    data class Snapshot(val vault: JsonObject, val items: List<JsonObject>, val cursor: String?)

    @Synchronized
    fun read(): Snapshot? = runCatching {
        if (!file.exists()) return null
        val o = json.parseToJsonElement(file.readText()) as JsonObject
        val vault = o["vault"] as? JsonObject ?: return null
        val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        Snapshot(vault, items, (o["cursor"] as? JsonPrimitive)?.content?.ifEmpty { null })
    }.getOrNull()

    /** Escrita atómica (tmp + rename): um crash a meio nunca deixa um ficheiro partido. */
    @Synchronized
    fun write(vault: JsonObject, items: List<JsonObject>, cursor: String?) {
        runCatching {
            file.parentFile?.mkdirs()
            val body = JsonObject(
                mapOf(
                    "vault" to vault,
                    "items" to JsonArray(items),
                    "cursor" to (cursor?.let(::JsonPrimitive) ?: JsonPrimitive("")),
                ),
            ).toString()
            val tmp = File(file.parentFile, "vault.json.tmp")
            tmp.writeText(body)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    @Synchronized
    fun clear() {
        runCatching { file.parentFile?.deleteRecursively() }
        clearShared()
    }

    /** PDFs/CSV temporários para partilhar (em claro): só os nossos (prefixo rc-). */
    val shareDir = File(context.cacheDir, "share")

    fun clearShared() {
        runCatching { shareDir.listFiles()?.filter { it.name.startsWith("rc-") }?.forEach { it.delete() } }
    }
}
