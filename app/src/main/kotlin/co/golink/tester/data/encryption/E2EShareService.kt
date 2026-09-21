package co.golink.tester.data.encryption

import android.content.Context
import co.golink.tester.domain.encryption.FileIdsBody
import co.golink.tester.domain.encryption.FolderShareKeyEntry
import co.golink.tester.domain.encryption.ShareItemNameEntry
import co.golink.tester.domain.encryption.ShareKeyBody
import co.golink.tester.domain.encryption.ShareOwnerKeyResponse
import co.golink.tester.domain.encryption.StoreShareOwnerKeyBody
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
import kotlinx.serialization.json.Json

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
    private val json: Json,
) {
    val isUnlocked: Boolean get() = e2eKeyManager.isUnlocked

    // Cache local da chave de partilha por token. A chave canónica vive no cofre do
    // servidor (selada à pública do dono): só com a cache, abrir a partilha noutro
    // dispositivo (ou na web) cunhava uma chave nova e matava em silêncio os links
    // já distribuídos. (Espelha o localStorage 'share_key_<token>' da web.)
    private val shareKeyPrefs = context.getSharedPreferences("e2e_share_keys", Context.MODE_PRIVATE)

    private fun cachedShareKey(token: String): ByteArray? =
        shareKeyPrefs.getString(token, null)?.let { runCatching { Envelope.unb64(it) }.getOrNull() }

    private fun cacheShareKey(token: String, key: ByteArray) {
        shareKeyPrefs.edit().putString(token, Envelope.b64(key)).apply()
    }

    /** Chave resolvida; `lost` = não existe em lado nenhum mas já há links lá fora. */
    private data class ResolvedShareKey(val key: ByteArray?, val lost: Boolean = false)

    /** Envia a chave selada ao cofre; em 409 adota a que já lá estava (é essa que está nos links). */
    private suspend fun putOwnerKey(token: String, key: ByteArray): ByteArray {
        val resp = filesApi.storeShareOwnerKey(token, StoreShareOwnerKeyBody(e2eKeyManager.sealForSelf(key)))
        if (resp.isSuccessful) return key

        val winner = if (resp.code() == 409) {
            resp.errorBody()?.string()
                ?.let { runCatching { json.decodeFromString(ShareOwnerKeyResponse.serializer(), it) }.getOrNull() }
                ?.wrapped_share_key
        } else null
        checkNotNull(winner) { "owner-key HTTP ${resp.code()}" }

        return e2eKeyManager.openFileDataKey(winner).also { cacheShareKey(token, it) }
    }

    /**
     * Cache local → cofre do servidor → cunhar. Mesma ordem e regras da web
     * (`resolveShareKey` em e2eShareLink.js): o servidor é a fonte canónica, e
     * NUNCA se cunha uma chave nova quando já há chaves registadas para o token.
     */
    private suspend fun resolveShareKey(token: String, allowMint: Boolean = true): ResolvedShareKey {
        val local = cachedShareKey(token)

        val remote = runCatching { filesApi.shareOwnerKey(token) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body()
            // Sem resposta do servidor: a cache é o melhor que há. Cunhar às cegas
            // podia invalidar links — não se faz.
            ?: return ResolvedShareKey(local)

        remote.wrapped_share_key?.let { blob ->
            val key = e2eKeyManager.openFileDataKey(blob)
            if (local == null || !local.contentEquals(key)) cacheShareKey(token, key)
            return ResolvedShareKey(key)
        }

        // Partilha anterior ao cofre: promove a chave local a portátil.
        if (local != null) return ResolvedShareKey(runCatching { putOwnerKey(token, local) }.getOrDefault(local))

        if (!allowMint) return ResolvedShareKey(null)
        if (remote.has_file_keys) return ResolvedShareKey(null, lost = true)

        val key = Envelope.generateDataKey()
        cacheShareKey(token, key)
        return ResolvedShareKey(runCatching { putOwnerKey(token, key) }.getOrDefault(key))
    }

    /** Resultado do fragmento de pasta. `keyLost`: chave irrecuperável — avisar, não cunhar. */
    data class FolderFragment(val fragment: String?, val keyLost: Boolean = false)

    /**
     * Fragmento #k= para o link público de uma PASTA cifrada: re-embrulha a data
     * key de cada ficheiro com uma chave de partilha e regista-as no servidor
     * (folder-keys). O fragmento leva só a chave de partilha — o servidor nunca a
     * vê. `fragment` null se o E2E estiver bloqueado ou a chave não for resolvida.
     */
    suspend fun buildFolderShareFragment(folderId: String, token: String): FolderFragment = withContext(Dispatchers.IO) {
        if (!e2eKeyManager.isUnlocked) return@withContext FolderFragment(null)

        val resolved = runCatching { resolveShareKey(token) }.getOrDefault(ResolvedShareKey(null))
        val shareKey = resolved.key ?: return@withContext FolderFragment(null, resolved.lost)

        val fileIds = runCatching { filesApi.folderEncryptedFileIds(folderId).body()?.file_ids }
            .getOrNull().orEmpty()

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

        FolderFragment("#k=" + java.net.URLEncoder.encode(Envelope.b64(shareKey), "UTF-8"))
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

    /**
     * Regista o nome de um item nas partilhas ANCESTRAIS do próprio dono — itens
     * criados/renomeados DEPOIS da partilha, que o registo em lote do popup não
     * cobre. Sem isto o visitante do link vê '•'. Nunca cunha chave nova (isso
     * mataria o link). Espelha `registerItemNameForOwnerShares` da web. Best-effort.
     */
    suspend fun registerItemNameForOwnerShares(itemId: String, name: String) = withContext(Dispatchers.IO) {
        if (!e2eKeyManager.isUnlocked || name.isBlank()) return@withContext
        val tokens = runCatching { filesApi.ancestorShareTokens(itemId).body()?.tokens }
            .getOrNull().orEmpty()

        tokens.forEach { token ->
            runCatching {
                val key = resolveShareKey(token, allowMint = false).key ?: return@runCatching
                val enc = Envelope.sealNameForShare(name, key)
                filesApi.storeShareItemNames(token, StoreShareItemNamesBody(listOf(ShareItemNameEntry(itemId, enc))))
            }
        }
    }

    /**
     * Regista a data key de um ficheiro acabado de enviar pelo DONO em todas as
     * partilhas ancestrais, embrulhada com a chave de cada uma. Sem isto o
     * ficheiro não abria pelo link até o dono reabrir a partilha. Nunca cunha
     * chave nova. Espelha `registerFileKeyForOwnerShares` da web. Best-effort.
     */
    suspend fun registerFileKeyForOwnerShares(fileId: String, dataKey: ByteArray) = withContext(Dispatchers.IO) {
        if (!e2eKeyManager.isUnlocked) return@withContext
        val tokens = runCatching { filesApi.ancestorShareTokens(fileId).body()?.tokens }
            .getOrNull().orEmpty()

        tokens.forEach { token ->
            runCatching {
                val key = resolveShareKey(token, allowMint = false).key ?: return@runCatching
                val entry = FolderShareKeyEntry(fileId, Envelope.wrapKeyForShare(dataKey, key))
                filesApi.storeFolderShareKeys(token, StoreFolderKeysBody(listOf(entry)))
            }
        }
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
