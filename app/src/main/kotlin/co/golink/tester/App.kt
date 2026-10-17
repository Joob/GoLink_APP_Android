package co.golink.tester

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class App : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Carrega o idioma guardado antes de qualquer UI compor.
        co.golink.tester.ui.i18n.I18n.init(this)
        // E o tema escolhido no botão Lua/Sol (por omissão segue o sistema).
        co.golink.tester.ui.theme.AppThemeMode.init(this)
        // Ficheiros desencriptados deixados por uma sessão que terminou mal.
        val startedAt = System.currentTimeMillis()
        Thread { co.golink.tester.data.encryption.PlaintextCache.sweep(this, olderThan = startedAt) }.start()
    }

    // AsyncImage usa o ImageLoader por omissão do Coil; sem isto o loader do
    // Hilt (cliente autenticado + decoders SVG/vídeo) só era usado onde fosse
    // injectado à mão.
    @Inject lateinit var imageLoader: Provider<ImageLoader>

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
