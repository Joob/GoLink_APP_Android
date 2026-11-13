package co.golink.tester.data.backup

import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Liga o backup automático à sessão. Sair (logout, sessão expirada) desliga-o:
 * antes o tick de 1 min continuava a acordar o telemóvel com "Sem sessão" e,
 * ao entrar outra conta, o backup retomava sozinho para ela. Outra conta
 * começa do zero (ver [AutoBackupPreferences.claimOwner]); a mesma conta
 * mantém os cursores e só tem de voltar a ligar.
 *
 * Criado no arranque da App. Provider: o AutoBackupManager arrasta o
 * UploadManager, que só é preciso se houver algo a desligar.
 */
@Singleton
class AutoBackupSessionGuard @Inject constructor(
    sessionManager: SessionManager,
    private val preferences: AutoBackupPreferences,
    private val manager: Provider<AutoBackupManager>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            sessionManager.state.collect { state ->
                when (state) {
                    AuthState.Unauthenticated -> if (preferences.enabled) runCatching { manager.get().disable() }
                    is AuthState.Authenticated -> preferences.claimOwner(state.user.id)
                    else -> Unit
                }
            }
        }
    }
}
