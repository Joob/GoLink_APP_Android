package co.golink.tester.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.golink.tester.BuildConfig
import java.io.File

/**
 * Preferências cifradas da app (token, PIN do bloqueio, alarmes do calendário).
 *
 * Guardadas em [SecurePrefs] (AES-GCM com chave do AndroidKeyStore) no ficheiro
 * `<name>_v2`. As EncryptedSharedPreferences antigas (`<name>`, security-crypto,
 * deprecada) migram uma vez: copiar → verificar a leitura → marcar → só então
 * apagar o antigo. Se algo falhar, continua a ler o antigo e tenta outra vez no
 * próximo arranque. Uma só instância por ficheiro no processo.
 */
object EncryptedPrefs {
    private const val SUFFIX = "_v2"
    private const val TAG = "EncryptedPrefs"
    private val opened = HashMap<String, SharedPreferences>()

    fun create(context: Context, name: String): SharedPreferences = synchronized(opened) {
        opened.getOrPut(name) { open(context.applicationContext, name) }
    }

    private fun open(context: Context, name: String): SharedPreferences {
        val newName = name + SUFFIX
        return SecurePrefsMigrator(
            openNew = {
                SecurePrefs(
                    context.getSharedPreferences(newName, Context.MODE_PRIVATE),
                    AesGcmPrefsCipher(KeystorePrefsKey::get),
                    newName,
                )
            },
            legacyExists = { File(context.dataDir, "shared_prefs/$name.xml").exists() },
            openLegacy = { legacy(context, name) },
            deleteLegacy = {
                // Limpar antes de apagar: o SharedPreferencesImpl em cache no Context
                // reescreveria o ficheiro numa escrita posterior.
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                context.deleteSharedPreferences(name)
            },
            log = { if (BuildConfig.DEBUG) Log.w(TAG, "$name: $it") },
        ).open()
    }

    private fun legacy(context: Context, name: String): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            name,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
}

/**
 * Decide que ficheiro servir e faz a migração. Separado do Android para os
 * testes na JVM. Pior caso aceitável: sessão/PIN/alarmes perdidos (voltar a
 * entrar), e só quando o ficheiro antigo já não abre.
 */
class SecurePrefsMigrator(
    private val openNew: () -> SecurePrefs,
    private val legacyExists: () -> Boolean,
    private val openLegacy: () -> SharedPreferences,
    private val deleteLegacy: () -> Unit,
    private val log: (String) -> Unit = {},
) {
    fun open(): SharedPreferences {
        val fresh = try {
            openNew()
        } catch (e: Exception) {
            // Keystore indisponível agora: nada se apaga; o antigo (se houver) serve
            // esta execução e a migração repete-se no próximo arranque.
            log("novo indisponível (${e.javaClass.simpleName})")
            if (!legacyExists()) return MemoryPrefs()
            return runCatching(openLegacy).getOrElse { MemoryPrefs() }
        }
        if (fresh.isMigrated()) {
            // Já migrado; uma limpeza que tenha falhado repete-se aqui.
            if (legacyExists()) runCatching(deleteLegacy)
            return fresh
        }
        if (!legacyExists()) {
            fresh.markMigrated()
            return fresh
        }
        val legacy = try {
            openLegacy()
        } catch (e: Exception) {
            // Keyset antigo ilegível (ex.: Keystore mudou): não há o que copiar.
            log("antigo ilegível (${e.javaClass.simpleName})")
            runCatching(deleteLegacy)
            fresh.markMigrated()
            return fresh
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            val entries = legacy.all
                .filter { (k, v) -> v != null && !k.startsWith("__androidx_security_crypto_") } as Map<String, Any>
            check(fresh.replaceAll(entries)) { "escrita falhou" }
            // Verificar a leitura a partir do disco (decifrar de novo), não da memória.
            check(fresh.readAll() == entries.mapValues { (_, v) -> if (v is Set<*>) v.toSet() else v }) { "verificação falhou" }
            check(fresh.markMigrated()) { "marca falhou" }
            runCatching(deleteLegacy)
            fresh
        } catch (e: Exception) {
            log("migração adiada (${e.javaClass.simpleName})")
            runCatching { fresh.replaceAll(emptyMap<String, Any>()) }
            legacy
        }
    }
}
