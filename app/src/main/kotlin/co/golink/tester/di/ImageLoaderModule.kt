package co.golink.tester.di

import android.content.Context
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
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
        .crossfade(true)
        .build()
}
