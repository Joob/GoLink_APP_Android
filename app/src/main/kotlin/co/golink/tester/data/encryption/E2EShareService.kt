package co.golink.tester.data.encryption

import android.content.Context
import co.golink.tester.domain.encryption.FileIdsBody
import co.golink.tester.domain.encryption.FolderShareKeyEntry
import co.golink.tester.domain.encryption.ShareItemNameEntry
import co.golink.tester.domain.encryption.ShareKeyBody
import co.golink.tester.domain.encryption.StoreShareItemNamesBody
import co.golink.tester.domain.encryption.StoreFolderKeysBody
import co.golink.tester.network.FilesApi
import co.golink.tester.network.UserEncryptionApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Partilha E2E a destinatários REGISTADos (membros de team folder): o dono abre a
 * data key de cada ficheiro (com a privada) e sela-a à pública do destinatário,
 * registando-a via share-key. Antes, o dono confirma o FINGERPRINT do destinatário
 * (mitiga key-substitution). Iniciado pelo dono — cobre ficheiros já existentes.
 */
@Singleton
class E2EShareService @Inject constructor(
    @ApplicationContext context: Context,
    private val filesApi: FilesApi,
    private val userEncryptionApi: UserEncryptionApi,
    private val e2eKeyManager: E2EKeyManager,
) {
    val isUnlocked: Boolean get() = e2eKeyManager.isUnlocked

    // Chave de partilha PERSISTENTE por token: gerar uma nova a cada abertura do
    // popup re-registaria as chaves e INVALIDARIA os links já copiados. A chave já
    // circula dentro do próprio link (#k=), por isso guardá-la localmente no dono
    // não muda a exposição. (Espelha o localStorage 'share_key_<token>' da web.)
    private val shareKeyPrefs = context.getSharedPreferences("e2e_share_keys", Context.MODE_PRIVATE)

    private fun shareKeyForToken(token: String): ByteArray {
        shareKeyPrefs.getString(token, null)?.let { return Envelope.unb64(it) }
        val key = Envelope.generateDataKey()
        shareKeyPrefs.edit().putString(token, Envelope.b64(key)).apply()
        return key
    }

    /**
     * Fragmento #k= para o link público de uma PASTA cifrada: re-embrulha a data
     * key de cada ficheiro com uma chave de partilha e regista-as no servidor
     * (folder-keys). O fragmento leva só a chave de partilha — o servidor nunca a
     * vê. null se a pasta não tiver ficheiros cifrados ou o E2E estiver bloqueado.
     */
    suspend fun buildFolderShareFragment(folderId: String, token: String): String? = withContext(Dispatchers.IO) {
        if (!e2eKeyManager.isUnlocked) return@withContext null
        val fileIds = runCatching { filesApi.folderEncryptedFileIds(folderId).body()?.file_ids }
            .getOrNull().orEmpty()

        val shareKey = shareKeyForToken(token)

        // NÃO abortar em pasta sem ficheiros cifrados: o link tem de levar SEMPRE
        // a shareKey, senão os ficheiros (e nomes) que entrem depois nunca abrem
        // por um link já distribuído.
        val entries = mutableListOf<FolderShareKeyEntry>()
        // Em lotes (500): pedir uma a uma rebentava o rate limiter em pastas grandes.
        fileIds.chunked(500).forEach { batch ->
            val rows = runCatching { filesApi.fileEncryptionKeysBatch(FileIdsBody(batch)).body()?.keys }
                .getOrNull().orEmpty()
            rows.forEach { row ->
                runCatching {
                    val dataKey = e2eKeyManager.openFileDataKey(row.wrapped_data_key)
                    entries += FolderShareKeyEntry(row.file_id, Envelope.wrapKeyForShare(dataKey, shareKey))
                }
            }
        }

        entries.chunked(500).forEach { chunk ->
            runCatching { filesApi.storeFolderShareKeys(token, StoreFolderKeysBody(chunk)) }
        }

        registerFolderNames(folderId, token, shareKey)

        "#k=" + java.net.URLEncoder.encode(Envelope.b64(shareKey), "UTF-8")
    }

    /**
     * Fragmento #k= de um FICHEIRO cifrado: a própria data key. Regista também o
     * nome cifrado com essa chave — o `name_encrypted` do ficheiro está selado à
     * pubkey do dono e é ilegível para o visitante.
     */
    suspend fun buildFileShareFragment(
        fileId: String,
        token: String,
        wrappedDataKey: String,
        plainName: String?,
    ): String? = withContext(Dispatchers.IO) {
        if (!e2eKeyManager.isUnlocked) return@withContext null
        val dataKey = runCatching { e2eKeyManager.openFileDataKey(wrappedDataKey) }.getOrNull()
            ?: return@withContext null

        // O nome em claro vem da listagem (já decifrado com a privada do dono).
        if (!plainName.isNullOrBlank()) {
            runCatching {
                val enc = Envelope.sealNameForShare(plainName, dataKey)
                filesApi.storeShareItemNames(token, StoreShareItemNamesBody(listOf(ShareItemNameEntry(fileId, enc))))
            }
        }

        "#k=" + java.net.URLEncoder.encode(Envelope.b64(dataKey), "UTF-8")
    }

    /** Nomes da pasta e descendentes, re-cifrados com a shareKey. Best-effort. */
    private suspend fun registerFolderNames(folderId: String, token: String, shareKey: ByteArray) {
        runCatching {
            val items = filesApi.folderDescendantNames(folderId).body()?.items.orEmpty()
            val names = items.mapNotNull { row ->
                val plain = row.name_encrypted?.let { e2eKeyManager.openNameOrNull(it) } ?: row.name
                plain?.let { ShareItemNameEntry(row.id, Envelope.sealNameForShare(it, shareKey)) }
            }
            names.chunked(500).forEach { chunk ->
                filesApi.storeShareItemNames(token, StoreShareItemNamesBody(chunk))
            }
        }
    }

    data class RecipientKey(val publicKey: String, val fingerprint: String)

    /** Chave pública + fingerprint do destinatário. null se ainda não tem E2E. */
    suspend fun recipientKey(userId: String): RecipientKey? = withContext(Dispatchers.IO) {
        val pub = runCatching { userEncryptionApi.publicKey(userId).body()?.public_key }.getOrNull()
        if (pub.isNullOrBlank()) return@withContext null
        RecipientKey(pub, e2eKeyManager.fingerprintOf(pub))
    }

    data class GrantResult(val ok: Int, val total: Int)

    /** Sela a data key de todos os ficheiros cifrados da pasta ao destinatário. */
    suspend fun grantFolderAccess(
        folderId: String,
        recipientId: String,
        recipientPublicKey: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): GrantResult = withContext(Dispatchers.IO) {
        val ids = runCatching { filesApi.folderEncryptedFileIds(folderId).body()?.file_ids }
            .getOrNull().orEmpty()

        var ok = 0
        ids.forEachIndexed { i, fileId ->
            try {
                val wrapped = userEncryptionApi.fileKey(fileId).body()?.wrapped_data_key
                if (wrapped != null) {
                    val dataKey = e2eKeyManager.openFileDataKey(wrapped)
                    val sealed = e2eKeyManager.sealForPublicKey(dataKey, recipientPublicKey)
                    filesApi.shareFileKey(fileId, ShareKeyBody(recipientId, sealed))
                    ok++
                }
            } catch (e: Throwable) {
                // sem acesso p/ este ficheiro; segue
            }
            onProgress(i + 1, ids.size)
        }
        GrantResult(ok, ids.size)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Accessor {
        fun e2eShareService(): E2EShareService
    }

    companion object {
        fun get(context: Context): E2EShareService =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                Accessor::class.java,
            ).e2eShareService()
    }
}
