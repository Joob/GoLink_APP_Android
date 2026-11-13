package co.golink.tester.data.notes

import co.golink.tester.data.encryption.E2EKeyManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * K_note abertas (wrapped_key → chave), partilhadas pelas Notes e pelas caixas
 * datadas no Calendário: uma só cópia na heap, zerada no lock/logout.
 */
@Singleton
class NoteKeyCache @Inject constructor(private val keys: E2EKeyManager) {
    private val cache = ConcurrentHashMap<String, ByteArray>()

    init {
        keys.addOnLock {
            cache.values.forEach { it.fill(0) }
            cache.clear()
        }
    }

    /** null: sem chave em memória ou wrapped_key ilegível. */
    fun open(wrapped: String?): ByteArray? {
        if (wrapped.isNullOrEmpty() || !keys.isUnlocked) return null
        cache[wrapped]?.let { return it }
        return runCatching { keys.openFileDataKey(wrapped) }.getOrNull()?.also { cache[wrapped] = it }
    }

    fun put(wrapped: String, key: ByteArray) {
        cache[wrapped] = key
    }
}
