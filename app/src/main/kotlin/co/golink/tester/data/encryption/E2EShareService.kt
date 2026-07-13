package co.golink.tester.data.encryption

import android.content.Context
import co.golink.tester.domain.encryption.ShareKeyBody
import co.golink.tester.network.FilesApi
import co.golink.tester.network.UserEncryptionApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
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
    private val filesApi: FilesApi,
    private val userEncryptionApi: UserEncryptionApi,
    private val e2eKeyManager: E2EKeyManager,
) {
    val isUnlocked: Boolean get() = e2eKeyManager.isUnlocked

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
