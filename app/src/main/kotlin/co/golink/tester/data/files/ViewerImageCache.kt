package co.golink.tester.data.files

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.imageLoader

/**
 * Cache em disco dos originais das imagens NÃO cifradas abertas no viewer: voltar a abrir
 * a mesma foto não a descarrega de novo. Separada da cache das miniaturas (150 MB), para
 * os originais não as expulsarem. Sem cache em memória: o teto em memória é o LruCache
 * de bitmaps do FileViewerViewModel. Os E2E nunca passam por aqui (plaintext em disco).
 * Limpa no fim da sessão (SessionTerminator).
 */
object ViewerImageCache {
    private const val DIR = "viewer_images"
    private const val MAX_BYTES = 200L * 1024 * 1024

    @Volatile private var loader: ImageLoader? = null

    /** O ImageLoader da app (OkHttp autenticado, decoders) com esta cache em disco. */
    fun loader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: context.applicationContext.imageLoader.newBuilder()
            .diskCache {
                DiskCache.Builder()
                    .directory(context.applicationContext.cacheDir.resolve(DIR))
                    .maxSizeBytes(MAX_BYTES)
                    .build()
            }
            .memoryCache(null)
            .build()
            .also { loader = it }
    }

    /** Apaga tudo (fora da main thread). */
    fun clear(context: Context) {
        val current = loader
        if (current != null) runCatching { current.diskCache?.clear() }
        else runCatching { context.cacheDir.resolve(DIR).deleteRecursively() }
    }
}
