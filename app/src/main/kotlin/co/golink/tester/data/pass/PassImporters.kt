package co.golink.tester.data.pass

import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Importação no aparelho (espelho de resources/js/services/pass/importers.js):
 * CSV por cabeçalhos (Chrome, Firefox, Bitwarden, 1Password, LastPass, genérico),
 * Bitwarden JSON não cifrado. O export cifrado do Pass é tratado em PassExport.
 */
object PassImporters {
    val SOURCES = listOf("chrome", "firefox", "bitwarden", "1password", "lastpass", "csv")

    class Unrecognised : Exception("unrecognised")
    class EncryptedBitwarden : Exception("encrypted_bitwarden")

    /** CSV RFC 4180 (aspas, vírgulas e quebras de linha dentro de campos). */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        val s = text.removePrefix("﻿")
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"' && i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ }
                else if (c == '"') quoted = false
                else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\n', '\r' -> {
                    if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                    row.add(field.toString()); rows.add(row); row = mutableListOf(); field.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private val ALIASES = linkedMapOf(
        "name" to listOf("name", "title", "login_name", "account", "item name"),
        "url" to listOf("url", "login_uri", "website", "uri", "urls", "web site", "hostname", "origin"),
        "username" to listOf("username", "login_username", "login", "user", "email", "user name", "login name"),
        "password" to listOf("password", "login_password", "pass"),
        "totp" to listOf("totp", "login_totp", "otpauth", "otp", "one-time password", "2fa"),
        "notes" to listOf("notes", "note", "extra", "comments", "comment"),
        "folder" to listOf("folder", "grouping", "group", "vault", "category"),
        "favourite" to listOf("favorite", "favourite", "fav", "starred"),
        "type" to listOf("type"),
    )

    private fun truthy(v: String) = Regex("^(1|true|yes|y|sim)$", RegexOption.IGNORE_CASE).matches(v.trim())

    private fun nameFromUrl(url: String): String {
        if (url.isEmpty()) return ""
        return runCatching { URI(if (url.contains("://")) url else "https://$url").host?.removePrefix("www.") }.getOrNull() ?: url
    }

    private fun s(v: String) = JsonPrimitive(v)

    fun draft(type: String, name: String, url: String, username: String, password: String, totp: String, notes: String, folder: String, favourite: Boolean): PassDraft {
        val base = mutableMapOf<String, JsonElement>(
            "type" to s(type),
            "name" to s((name.ifEmpty { nameFromUrl(url).ifEmpty { username.ifEmpty { "Item" } } }).take(200)),
            "notes" to s(notes),
            "favourite" to JsonPrimitive(favourite),
        )
        if (type != "note") base["login"] = PassLogin(url, username, password, totp).toJson()
        return PassDraft(JsonObject(base), folder)
    }

    fun importCsv(text: String, source: String = "csv"): List<PassDraft> {
        val rows = parseCsv(text)
        if (rows.size < 2) return emptyList()
        val header = rows[0].map { it.trim().lowercase() }
        val map = ALIASES.mapNotNull { (k, names) -> header.indexOfFirst { it in names }.takeIf { it >= 0 }?.let { k to it } }.toMap()
        if ("password" !in map && "notes" !in map) throw Unrecognised()
        fun get(r: List<String>, k: String) = map[k]?.let { r.getOrNull(it) }?.trim().orEmpty()
        return rows.drop(1).map { r ->
            val url = get(r, "url")
            val note = (source == "lastpass" && url == "http://sn") || Regex("^(note|securenote|2)$", RegexOption.IGNORE_CASE).matches(get(r, "type"))
            val type = if (note) "note" else "login"
            draft(type, get(r, "name"), if (note) "" else url, get(r, "username"), get(r, "password"), get(r, "totp"), get(r, "notes"), get(r, "folder"), truthy(get(r, "favourite")))
        }
    }

    private fun JsonElement?.str(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** Bitwarden JSON não cifrado (type 1 login, 2 nota, 3 cartão). */
    fun importBitwardenJson(json: JsonObject): List<PassDraft> {
        if ((json["encrypted"] as? JsonPrimitive)?.contentOrNull == "true") throw EncryptedBitwarden()
        val folders = (json["folders"] as? JsonArray).orEmpty().mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            o["id"].str() to o["name"].str()
        }.toMap()
        return (json["items"] as? JsonArray).orEmpty().mapNotNull { e ->
            val it = e as? JsonObject ?: return@mapNotNull null
            val name = it["name"].str()
            val notes = it["notes"].str()
            val folder = folders[it["folderId"].str()].orEmpty()
            val fav = (it["favorite"] as? JsonPrimitive)?.contentOrNull == "true"
            val common = mapOf("name" to s(name), "notes" to s(notes), "favourite" to JsonPrimitive(fav))
            when ((it["type"] as? JsonPrimitive)?.intOrNull) {
                1 -> {
                    val l = it["login"] as? JsonObject
                    val uri = ((l?.get("uris") as? JsonArray)?.firstOrNull() as? JsonObject)?.get("uri").str()
                    PassDraft(JsonObject(common + mapOf("type" to s("login"), "login" to PassLogin(uri, l?.get("username").str(), l?.get("password").str(), l?.get("totp").str()).toJson())), folder)
                }
                3 -> {
                    val c = it["card"] as? JsonObject
                    val m = c?.get("expMonth").str()
                    val y = c?.get("expYear").str()
                    val exp = if (m.isNotEmpty() && y.isNotEmpty()) "${m.padStart(2, '0')}/${y.takeLast(2)}" else ""
                    PassDraft(JsonObject(common + mapOf("type" to s("card"), "card" to PassCard(c?.get("cardholderName").str(), c?.get("number").str(), exp, c?.get("code").str(), "").toJson())), folder)
                }
                2 -> PassDraft(JsonObject(common + mapOf("type" to s("note"))), folder)
                else -> null
            }
        }
    }

    /** Export portátil (pass/exporter.js toPortable) → rascunho. */
    fun fromPortable(o: JsonObject): PassDraft {
        val folder = o.str("folderName").orEmpty()
        return PassDraft(o.with("folderName" to null), folder)
    }
}
