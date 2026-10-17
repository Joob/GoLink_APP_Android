package co.golink.tester.data.upload

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Retoma de uploads depois de a app ser fechada (ou morta pelo sistema).
 *
 * Guarda em filesDir a fila de uploads do utilizador e, para os ficheiros
 * grandes (por chunks), o nome do .part no servidor e os índices já aceites.
 * O servidor guarda cada chunk à parte durante 24 h e reescrever o mesmo índice
 * é inofensivo, por isso a retoma continua do último chunk aceite.
 *
 * E2E: o ciphertext fica num ficheiro em filesDir/upload_resume (não no cache)
 * e a data key é guardada cifrada com uma chave AES do Android Keystore — nunca
 * sai do dispositivo nem fica em claro no disco. O backup automático fica de
 * fora (o worker volta a correr sozinho).
 */
class UploadResumeStore(private val context: Context) {

    @Serializable
    data class Recipient(val user_id: String, val public_key: String)

    @Serializable
    data class ChunkState(
        val chunkName: String,
        val chunkCount: Int,
        val totalBytes: Long,
        val uploaded: List<Int> = emptyList(),
    )

    @Serializable
    data class E2EState(
        val tempPath: String,
        val wrapped: String,
        val nameEncrypted: String? = null,
        val mediaType: String,
        val keyIv: String,
        val keyCt: String,
        val thumb: String? = null,
        val recipients: List<Recipient> = emptyList(),
        val ownerSelf: Boolean,
    )

    @Serializable
    data class Record(
        val taskId: String,
        val userId: String,
        val uri: String,
        val name: String,
        val size: Long,
        val parentId: String? = null,
        /** Pasta (SAF) de onde veio: a permissão persistente é da árvore. */
        val treeUri: String? = null,
        val overwrite: Boolean = false,
        val replacesId: String? = null,
        val chunks: ChunkState? = null,
        val e2e: E2EState? = null,
        val updatedAt: Long = System.currentTimeMillis(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "upload_resume.json")
    private val dir = File(context.filesDir, "upload_resume").apply { mkdirs() }
    private val records = linkedMapOf<String, Record>()
    private val lastSave = mutableMapOf<String, Long>()

    init {
        runCatching {
            if (file.exists()) {
                json.decodeFromString(ListSerializer(Record.serializer()), file.readText())
                    .forEach { records[it.taskId] = it }
            }
        }
        purgeStale()
    }

    @Synchronized fun get(taskId: String): Record? = records[taskId]

    @Synchronized fun forUser(userId: String): List<Record> = records.values.filter { it.userId == userId }

    @Synchronized fun put(record: Record) {
        records[record.taskId] = record.copy(updatedAt = System.currentTimeMillis())
        save()
    }

    @Synchronized fun update(taskId: String, transform: (Record) -> Record) {
        val r = records[taskId] ?: return
        records[taskId] = transform(r).copy(updatedAt = System.currentTimeMillis())
        save()
    }

    /** Chunk aceite pelo servidor. Grava no máximo a cada ~800 ms por ficheiro. */
    @Synchronized fun markChunk(taskId: String, index: Int) {
        val r = records[taskId] ?: return
        val c = r.chunks ?: return
        if (index in c.uploaded) return
        records[taskId] = r.copy(chunks = c.copy(uploaded = c.uploaded + index), updatedAt = System.currentTimeMillis())
        val now = System.currentTimeMillis()
        if (now - (lastSave[taskId] ?: 0L) > 800L) {
            lastSave[taskId] = now
            save()
        }
    }

    /** O ficheiro saiu da fila: apaga o registo e o ciphertext temporário. */
    @Synchronized fun forget(taskId: String): Record? {
        val r = records.remove(taskId) ?: return null
        lastSave.remove(taskId)
        r.e2e?.tempPath?.let { File(it).delete() }
        save()
        return r
    }

    @Synchronized fun isTracked(taskId: String): Boolean = records.containsKey(taskId)

    /** Outro registo ainda usa esta árvore SAF? (para libertar a permissão). */
    @Synchronized fun treeInUse(treeUri: String): Boolean = records.values.any { it.treeUri == treeUri }

    @Synchronized fun uriInUse(uri: String): Boolean = records.values.any { it.uri == uri }

    @Synchronized fun clear() {
        records.clear()
        lastSave.clear()
        dir.listFiles()?.forEach { it.delete() }
        file.delete()
    }

    fun tempFileFor(taskId: String): File = File(dir, "$taskId.enc")

    private fun save() {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(Record.serializer()), records.values.toList()))
            tmp.renameTo(file)
        }
    }

    @Synchronized private fun purgeStale() {
        val cutoff = System.currentTimeMillis() - RECORD_MAX_AGE
        val stale = records.values.filter { it.updatedAt < cutoff }.map { it.taskId }
        stale.forEach { forget(it) }
        // Ciphertexts órfãos (registo perdido): não deixar ficheiros grandes para trás.
        val live = records.values.mapNotNull { it.e2e?.tempPath }.toSet()
        dir.listFiles()?.filter { it.absolutePath !in live }?.forEach { it.delete() }
    }

    // ---- chave do dispositivo (Keystore) para a data key E2E ----------------

    private fun deviceKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** (iv, ct) em base64. */
    fun wrapKey(dataKey: ByteArray): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        val ct = cipher.doFinal(dataKey)
        return b64(cipher.iv) to b64(ct)
    }

    fun unwrapKey(iv: String, ct: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, unb64(iv)))
        return cipher.doFinal(unb64(ct))
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun unb64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        private const val KEY_ALIAS = "gc_upload_resume"
        private const val RECORD_MAX_AGE = 7L * 24 * 3600 * 1000

        /** O servidor apaga as partes ao fim de 24 h: depois disto recomeça os chunks. */
        const val CHUNKS_MAX_AGE = 20L * 3600 * 1000
    }
}
