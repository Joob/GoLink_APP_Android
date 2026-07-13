package co.golink.tester.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.data.encryption.EncryptedFileCodec
import co.golink.tester.network.FilesApi
import co.golink.tester.network.UserEncryptionApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carrega e decifra o thumbnail cifrado (E2E) de um ficheiro: vai buscar a data
 * key selada, abre-a com a privada em memória, descarrega o ciphertext e decifra
 * para um Bitmap. Cache em memória + set de falhas para não repetir 404. Se a
 * encriptação estiver trancada, devolve null (o UI mostra o ícone).
 */
@Singleton
class EncryptedThumbnailLoader @Inject constructor(
    private val filesApi: FilesApi,
    private val userEncryptionApi: UserEncryptionApi,
    private val e2eKeyManager: E2EKeyManager,
) {
    private val cache = LruCache<String, ImageBitmap>(80)
    private val failed = Collections.synchronizedSet(HashSet<String>())

    suspend fun load(fileId: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(fileId)?.let { return@withContext it }
        if (!e2eKeyManager.isUnlocked) return@withContext null
        if (failed.contains(fileId)) return@withContext null

        try {
            val wrapped = userEncryptionApi.fileKey(fileId).body()?.wrapped_data_key
                ?: return@withContext null.also { failed.add(fileId) }
            val dataKey = e2eKeyManager.openFileDataKey(wrapped)

            val resp = filesApi.downloadEncryptedThumbnail(fileId)
            if (!resp.isSuccessful) {
                failed.add(fileId)
                return@withContext null
            }
            val cipher = resp.body()?.bytes() ?: return@withContext null
            val plain = EncryptedFileCodec.decryptFull(cipher, dataKey)
            val bmp = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                ?: return@withContext null.also { failed.add(fileId) }
            bmp.asImageBitmap().also { cache.put(fileId, it) }
        } catch (e: Throwable) {
            failed.add(fileId)
            null
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Accessor {
        fun encryptedThumbnailLoader(): EncryptedThumbnailLoader
    }

    companion object {
        fun get(context: Context): EncryptedThumbnailLoader =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                Accessor::class.java,
            ).encryptedThumbnailLoader()
    }
}

/**
 * Preview de um thumbnail cifrado: decifra em memória e mostra; enquanto não há
 * (a carregar / trancado / 404) mostra o fallback (ícone).
 */
@Composable
fun EncryptedThumbnail(
    fileId: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val loader = remember { EncryptedThumbnailLoader.get(context) }
    var bitmap by remember(fileId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(fileId) {
        bitmap = loader.load(fileId)
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(modifier = modifier) { fallback() }
    }
}
