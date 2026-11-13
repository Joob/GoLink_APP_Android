package co.golink.tester

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@HiltAndroidApp
class App : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Lazy: criar estes grafos abre a Keystore (preferências cifradas, com a
    // migração única das EncryptedSharedPreferences) — fora da main thread no arranque.

    // Fim de sessão (tranca E2E/Pass, caches, cookies) em qualquer caminho.
    @Inject lateinit var sessionTerminator: dagger.Lazy<co.golink.tester.data.auth.SessionTerminator>

    // Desliga o backup automático ao sair da conta (criado já no arranque).
    @Inject lateinit var autoBackupSessionGuard: dagger.Lazy<co.golink.tester.data.backup.AutoBackupSessionGuard>

    // Lembretes do calendário (AlarmManager): agenda ao desbloquear, limpa no logout.
    @Inject lateinit var calendarAlarms: dagger.Lazy<co.golink.tester.data.calendar.CalendarAlarmScheduler>
    @Inject lateinit var receiptsNotifier: dagger.Lazy<co.golink.tester.data.receipts.ReceiptsNotifier>

    // Preferências de datas: derivadas do utilizador, aplicadas ao estado Compose no Main.
    @Inject lateinit var userRepository: dagger.Lazy<co.golink.tester.data.user.UserRepository>

    // Língua e tema: escolha local → servidor, e da conta → app (iguais à web).
    @Inject lateinit var accountPrefsSync: dagger.Lazy<co.golink.tester.data.settings.AccountPrefsSync>

    override fun onCreate() {
        super.onCreate()
        // Carrega o idioma guardado antes de qualquer UI compor.
        co.golink.tester.ui.i18n.I18n.init(this)
        // Sem sessão o pedido falha e fica pendente: vai quando houver utilizador.
        co.golink.tester.ui.i18n.I18n.onUserChange = { code -> accountPrefsSync.get().languageChanged(code) }
        // E o tema escolhido no botão Lua/Sol (por omissão segue o sistema).
        co.golink.tester.ui.theme.AppThemeMode.init(this)
        co.golink.tester.ui.theme.AppThemeMode.onUserChange = { mode -> accountPrefsSync.get().themeChanged(mode) }
        co.golink.tester.ui.components.drawer.DrawerPanel.init(this)
        Thread {
            sessionTerminator.get()
            autoBackupSessionGuard.get()
            calendarAlarms.get().start()
            receiptsNotifier.get().start()
        }.start()
        kotlinx.coroutines.MainScope().launch {
            val repo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { userRepository.get() }
            repo.displayPrefs.collect { co.golink.tester.ui.common.UserDisplayPrefs.set(it) }
        }
        // Idioma/tema da conta: só reage quando muda o utilizador ou um destes valores.
        kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.Default) {
            val repo = userRepository.get()
            val sync = accountPrefsSync.get()
            repo.me
                .filterNotNull()
                .distinctUntilChangedBy { Triple(it.id, it.language, it.themeMode) }
                .collect { sync.onUser(it) }
        }
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
