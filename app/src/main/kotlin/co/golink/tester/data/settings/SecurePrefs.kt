package co.golink.tester.data.settings

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.CopyOnWriteArraySet
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Cifra dos valores das [SecurePrefs]. Blob = IV (12 B) ‖ cifra+tag (16 B). */
interface PrefsCipher {
    fun encrypt(plain: ByteArray, aad: ByteArray): ByteArray
    fun decrypt(blob: ByteArray, aad: ByteArray): ByteArray
}

/** AES-256-GCM com a chave de [key] (Keystore em produção, chave de software nos testes). */
class AesGcmPrefsCipher(private val key: () -> SecretKey) : PrefsCipher {
    override fun encrypt(plain: ByteArray, aad: ByteArray): ByteArray {
        // IV gerado pelo provider (a Keystore exige-o: randomizedEncryptionRequired).
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        c.updateAAD(aad)
        val ct = c.doFinal(plain)
        val iv = c.iv
        check(iv.size == IV_LEN) { "IV inesperado" }
        return iv + ct
    }

    override fun decrypt(blob: ByteArray, aad: ByteArray): ByteArray {
        if (blob.size < IV_LEN + TAG_LEN) throw BadPaddingException("blob curto")
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LEN * 8, blob, 0, IV_LEN))
        c.updateAAD(aad)
        return c.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val TAG_LEN = 16
    }
}

/**
 * Chave AES-256 do AndroidKeyStore (não exportável, sem autenticação do
 * utilizador) que cifra as [SecurePrefs]. Não migra para outro aparelho, por
 * isso os ficheiros ficam fora do backup e da transferência (data_extraction_rules).
 */
object KeystorePrefsKey {
    private const val ALIAS = "golink_secure_prefs_v2"
    @Volatile private var cached: SecretKey? = null

    fun get(): SecretKey = cached ?: synchronized(this) { cached ?: load().also { cached = it } }

    private fun load(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // getKey lança se a entrada existir mas não abrir: aí não se gera outra por cima.
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}

/**
 * SharedPreferences com os valores cifrados (AES-GCM, chave na Keystore) e o
 * nome do ficheiro + chave no AAD (um valor não pode ser trocado de chave). Os
 * nomes das chaves ficam em claro (não são sensíveis). Substitui as
 * EncryptedSharedPreferences da security-crypto (deprecada): ver [EncryptedPrefs].
 *
 * Os valores são decifrados uma vez ao abrir e ficam em memória; por isso deve
 * haver uma só instância por ficheiro no processo ([EncryptedPrefs] garante-o).
 */
class SecurePrefs(
    private val backing: SharedPreferences,
    private val cipher: PrefsCipher,
    private val scope: String,
) : SharedPreferences {
    private val lock = Any()
    private val values = HashMap<String, Any>()
    private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        values.putAll(readAll())
    }

    /**
     * Decifra tudo o que está guardado. Um valor adulterado ou de outra chave
     * (tag inválida) é ignorado; a Keystore indisponível lança, para quem abre
     * decidir (não se perde nada no disco).
     */
    fun readAll(): Map<String, Any> {
        val out = HashMap<String, Any>()
        for ((key, raw) in backing.all) {
            if (key == MARKER || raw !is String) continue
            val plain = try {
                cipher.decrypt(Base64.getDecoder().decode(raw), aad(key))
            } catch (_: BadPaddingException) {
                continue
            } catch (_: IllegalArgumentException) {
                continue
            }
            decode(plain)?.let { out[key] = it }
        }
        return out
    }

    /** Migração concluída (marca em claro, fora dos valores). */
    fun isMigrated(): Boolean = backing.getBoolean(MARKER, false)

    fun markMigrated(): Boolean = backing.edit().putBoolean(MARKER, true).commit()

    /** Substitui todo o conteúdo e grava já no disco. */
    fun replaceAll(entries: Map<String, *>): Boolean {
        val editor = edit().clear() as Editor
        entries.forEach { (k, v) -> if (v != null) editor.putAny(k, v) }
        return editor.commit()
    }

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { HashMap<String, Any?>(values) }

    override fun getString(key: String, defValue: String?): String? =
        synchronized(lock) { values[key] as? String } ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(lock) {
            @Suppress("UNCHECKED_CAST")
            (values[key] as? Set<String>)?.toMutableSet()
        } ?: defValues

    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { values[key] as? Int } ?: defValue
    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { values[key] as? Long } ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) { values[key] as? Float } ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { values[key] as? Boolean } ?: defValue
    override fun contains(key: String): Boolean = synchronized(lock) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private fun aad(key: String) = "$scope/$key".toByteArray(Charsets.UTF_8)

    private inner class Editor : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>() // null = remover
        private var clearAll = false

        fun putAny(key: String, value: Any?): Editor {
            require(key != MARKER) { "chave reservada" }
            changes[key] = when (value) {
                is Set<*> -> value.map { it as String }.toSet()
                else -> value
            }
            return this
        }

        override fun putString(key: String, value: String?) = putAny(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = putAny(key, values)
        override fun putInt(key: String, value: Int) = putAny(key, value)
        override fun putLong(key: String, value: Long) = putAny(key, value)
        override fun putFloat(key: String, value: Float) = putAny(key, value)
        override fun putBoolean(key: String, value: Boolean) = putAny(key, value)
        override fun remove(key: String) = putAny(key, null)
        override fun clear(): Editor {
            clearAll = true
            return this
        }
        override fun commit(): Boolean = write(sync = true)
        override fun apply() {
            write(sync = false)
        }

        private fun write(sync: Boolean): Boolean {
            // Cifra primeiro: se a Keystore falhar, nem o disco nem a memória mudam.
            val sealed = changes.mapValues { (k, v) ->
                v?.let { Base64.getEncoder().encodeToString(cipher.encrypt(encode(it), aad(k))) }
            }
            var changed: Set<String> = emptySet()
            val ok = synchronized(lock) {
                val e = backing.edit()
                changed = if (clearAll) values.keys + changes.keys else changes.keys.toSet()
                if (clearAll) {
                    // Também os blobs que já não decifram (não estão em memória).
                    backing.all.keys.filter { it != MARKER }.forEach { e.remove(it) }
                    values.clear()
                }
                for ((k, v) in changes) {
                    val blob = sealed[k]
                    if (v == null || blob == null) {
                        e.remove(k)
                        values.remove(k)
                    } else {
                        e.putString(k, blob)
                        values[k] = v
                    }
                }
                if (sync) e.commit() else {
                    e.apply()
                    true
                }
            }
            if (listeners.isNotEmpty()) changed.forEach { k -> listeners.forEach { it.onSharedPreferenceChanged(this@SecurePrefs, k) } }
            return ok
        }
    }

    companion object {
        /** Marca da migração a partir das EncryptedSharedPreferences. */
        const val MARKER = "__secure_prefs_migrated"

        private val setSerializer = ListSerializer(String.serializer())

        internal fun encode(v: Any): ByteArray = when (v) {
            is String -> "s$v"
            is Boolean -> "b$v"
            is Int -> "i$v"
            is Long -> "l$v"
            is Float -> "f$v"
            is Set<*> -> "S" + Json.encodeToString(setSerializer, v.map { it as String })
            else -> throw IllegalArgumentException("tipo não suportado: ${v::class.java.simpleName}")
        }.toByteArray(Charsets.UTF_8)

        internal fun decode(bytes: ByteArray): Any? {
            val s = String(bytes, Charsets.UTF_8)
            if (s.isEmpty()) return null
            val body = s.substring(1)
            return runCatching<Any?> {
                when (s[0]) {
                    's' -> body
                    'b' -> body.toBooleanStrict()
                    'i' -> body.toInt()
                    'l' -> body.toLong()
                    'f' -> body.toFloat()
                    'S' -> Json.decodeFromString(setSerializer, body).toSet()
                    else -> null
                }
            }.getOrNull()
        }
    }
}

/** SharedPreferences só em memória: recurso quando a Keystore não abre (nada se perde no disco). */
class MemoryPrefs : SharedPreferences {
    private val values = HashMap<String, Any>()

    @Synchronized override fun getAll(): MutableMap<String, *> = HashMap<String, Any?>(values)
    @Synchronized override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    @Synchronized override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    @Synchronized override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    @Synchronized override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    @Synchronized override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    @Synchronized override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    @Synchronized override fun contains(key: String) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clearAll = false
        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            changes[key] = if (value is Set<*>) value.toSet() else value
            return this
        }
        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, null)
        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }
        override fun commit(): Boolean {
            synchronized(this@MemoryPrefs) {
                if (clearAll) values.clear()
                changes.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            }
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
