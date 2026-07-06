package co.golink.tester.di

import android.content.Context
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("authed") client: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(client)
        .components {
            add(SvgDecoder.Factory())
            // Extrai um frame de ficheiros de vídeo — usado como preview no
            // ícone quando o servidor não fornece thumbnail.
            add(VideoFrameDecoder.Factory())
        }
        // Cache em memória (~25% da heap) e em disco (150 MB). Sem isto cada
        // scroll de volta voltava a buscar os thumbnails à rede — o principal
        // motivo do scroll lento e do "loading" repetido.
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        // Os thumbnails chegam por URLs assinados (S3/CDN) sem cabeçalhos de
        // cache; sem isto o Coil nunca os guardava em disco e recarregava tudo
        // a cada visita.
        .respectCacheHeaders(false)
        // RGB_565 nos previews opacos: metade da memória por bitmap, scroll
        // mais leve em listas grandes de imagens.
        .allowRgb565(true)
        .crossfade(true)
        .build()
}
