package co.golink.tester.data.settings

import android.content.SharedPreferences
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurePrefsTest {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmPrefsCipher { key }

    private fun prefs(backing: SharedPreferences = MemoryPrefs(), c: PrefsCipher = cipher) =
        SecurePrefs(backing, c, "auth_prefs_v2")

    @Test
    fun roundTripAllTypesFromDisk() {
        val backing = MemoryPrefs()
        prefs(backing).edit()
            .putString("token", "1|abc")
            .putBoolean("otp_validated", true)
            .putInt("n", 7)
            .putLong("l", 1L shl 40)
            .putFloat("f", 1.5f)
            .putStringSet("revoke_pending", mutableSetOf("a", "b"))
            .commit()
        // Nova instância: decifra do "disco", não da memória.
        val reopened = prefs(backing)
        assertEquals("1|abc", reopened.getString("token", null))
        assertTrue(reopened.getBoolean("otp_validated", false))
        assertEquals(7, reopened.getInt("n", 0))
        assertEquals(1L shl 40, reopened.getLong("l", 0))
        assertEquals(1.5f, reopened.getFloat("f", 0f))
        assertEquals(setOf("a", "b"), reopened.getStringSet("revoke_pending", null))
    }

    @Test
    fun valuesAreNotStoredInClear() {
        val backing = MemoryPrefs()
        prefs(backing).edit().putString("token", "segredo-123").commit()
        val raw = backing.getString("token", null)!!
        assertFalse(raw.contains("segredo"))
    }

    @Test
    fun valueMovedToAnotherKeyIsRejected() {
        val backing = MemoryPrefs()
        prefs(backing).edit().putString("token", "x").commit()
        backing.edit().putString("cached_user", backing.getString("token", null)).commit()
        assertNull(prefs(backing).getString("cached_user", null))
        assertEquals("x", prefs(backing).getString("token", null))
    }

    @Test
    fun otherKeyCannotReadAndClearRemovesStaleBlobs() {
        val backing = MemoryPrefs()
        prefs(backing).edit().putString("token", "x").commit()
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val other = prefs(backing, AesGcmPrefsCipher { otherKey })
        assertNull(other.getString("token", null))
        other.edit().clear().commit()
        assertFalse(backing.contains("token"))
    }

    @Test
    fun removeAndContains() {
        val p = prefs()
        p.edit().putString("a", "1").putString("b", "2").commit()
        p.edit().remove("a").apply()
        assertFalse(p.contains("a"))
        assertEquals(mapOf("b" to "2"), p.all)
    }

    // ---- migração --------------------------------------------------------

    private class World {
        val legacy = MemoryPrefs()
        var legacyPresent = true
        var legacyBroken = false
        var newBroken = false
        var deletes = 0
        val backing = MemoryPrefs()
        var cipher: PrefsCipher? = null

        fun migrator() = SecurePrefsMigrator(
            openNew = {
                if (newBroken) throw java.security.KeyStoreException("keystore")
                SecurePrefs(backing, cipher!!, "auth_prefs_v2")
            },
            legacyExists = { legacyPresent },
            openLegacy = { if (legacyBroken) throw java.security.GeneralSecurityException("keyset") else legacy },
            deleteLegacy = {
                deletes++
                legacy.edit().clear().commit()
                legacyPresent = false
            },
        )
    }

    private fun world() = World().also { it.cipher = cipher }

    @Test
    fun migratesCopiesVerifiesThenDeletes() {
        val w = world()
        w.legacy.edit().putString("token", "t").putBoolean("otp_validated", true)
            .putStringSet("revoke_pending", mutableSetOf("old")).commit()
        val p = w.migrator().open()
        assertTrue(p is SecurePrefs)
        assertEquals("t", p.getString("token", null))
        assertTrue(p.getBoolean("otp_validated", false))
        assertEquals(setOf("old"), p.getStringSet("revoke_pending", null))
        assertEquals(1, w.deletes)
        assertFalse(w.legacyPresent)
        assertTrue(w.backing.getBoolean(SecurePrefs.MARKER, false))
        // Próximo arranque: já migrado, lê do novo.
        assertEquals("t", w.migrator().open().getString("token", null))
    }

    @Test
    fun keystoreFailureKeepsReadingLegacyAndRetriesLater() {
        val w = world()
        w.legacy.edit().putString("token", "t").commit()
        w.newBroken = true
        val p = w.migrator().open()
        assertSame(w.legacy, p)
        assertEquals(0, w.deletes)
        // Arranque seguinte com a Keystore de volta: migra.
        w.newBroken = false
        assertEquals("t", w.migrator().open().getString("token", null))
        assertEquals(1, w.deletes)
    }

    @Test
    fun verificationFailureKeepsLegacyAndLeavesNoPartialCopy() {
        val w = world()
        w.legacy.edit().putString("token", "t").commit()
        // Cifra que grava mas não lê de volta → a verificação falha.
        w.cipher = object : PrefsCipher {
            override fun encrypt(plain: ByteArray, aad: ByteArray) = cipher.encrypt(plain, aad)
            override fun decrypt(blob: ByteArray, aad: ByteArray): ByteArray = throw javax.crypto.AEADBadTagException("x")
        }
        val p = w.migrator().open()
        assertSame(w.legacy, p)
        assertEquals(0, w.deletes)
        assertFalse(w.backing.getBoolean(SecurePrefs.MARKER, false))
        assertFalse(w.backing.contains("token"))
        // Corrigido no arranque seguinte.
        w.cipher = cipher
        assertEquals("t", w.migrator().open().getString("token", null))
    }

    @Test
    fun unreadableLegacyStartsEmpty() {
        val w = world()
        w.legacyBroken = true
        val p = w.migrator().open()
        assertTrue(p is SecurePrefs)
        assertNull(p.getString("token", null))
        assertEquals(1, w.deletes)
    }

    @Test
    fun freshInstallHasNothingToMigrate() {
        val w = world()
        w.legacyPresent = false
        val p = w.migrator().open()
        p.edit().putString("token", "n").commit()
        assertEquals(0, w.deletes)
        assertEquals("n", w.migrator().open().getString("token", null))
    }

    @Test
    fun keystoreDownWithoutLegacyUsesMemoryAndKeepsDisk() {
        val w = world()
        w.legacyPresent = false
        w.migrator().open().edit().putString("token", "kept").commit()
        val before = w.backing.getString("token", null)
        w.newBroken = true
        val p = w.migrator().open()
        assertTrue(p is MemoryPrefs)
        p.edit().putString("token", "other").commit()
        assertEquals(before, w.backing.getString("token", null))
        assertNotEquals(null, before)
    }

    @Test
    fun pendingCleanupIsRetried() {
        val w = world()
        w.legacy.edit().putString("token", "t").commit()
        w.migrator().open()
        // Simula um ficheiro antigo que não foi apagado.
        w.legacyPresent = true
        w.migrator().open()
        assertEquals(2, w.deletes)
    }
}
