package co.golink.tester.data.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.network.AuthApi
import coil.ImageLoader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Ponto único de fim de sessão. Todos os caminhos (logout, 401 de sessão
 * revogada, 403 de conta suspensa, eliminação de conta, "Terminar sessão" no
 * ecrã de erro) acabam em [TokenStore.clear], que chama [onSessionEnded]:
 *
 * - tranca o E2E (zera a privada, apaga o "manter desbloqueado" e o pin, e os
 *   addOnLock limpam Pass — incluindo a K biométrica —, Notes, Calendário e Browse);
 * - limpa as caches de imagens (memória e disco) com thumbnails da conta;
 * - apaga cookies e storage das WebViews (sessão Laravel e do fornecedor OAuth).
 *
 * Providers: o E2EKeyManager e o ImageLoader dependem do OkHttp, que depende
 * do TokenStore — só são resolvidos quando a sessão acaba.
 */
@Singleton
class SessionTerminator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
    private val e2eKeyManager: Provider<E2EKeyManager>,
    private val imageLoader: Provider<ImageLoader>,
) {
    init {
        tokenStore.setOnCleared { onSessionEnded() }
        // Sem sessão ao arrancar (ex.: terminada por uma versão anterior que só
        // apagava o token): o "manter desbloqueado" e o pin não podem ficar.
        if (tokenStore.token == null) Thread { runCatching { e2eKeyManager.get().lock() } }.start()
        // Tokens que ficaram por revogar numa sessão anterior (sem rede).
        if (tokenStore.pendingRevokes().isNotEmpty()) scheduleRevoke()
    }

    /** Revoga o token no servidor quando houver rede (logout sem rede, ecrã de erro). */
    fun revokeLater(token: String) {
        tokenStore.addPendingRevoke(token)
        scheduleRevoke()
    }

    private fun onSessionEnded() {
        // Síncrono: tem de acontecer antes de outra conta poder entrar.
        runCatching { e2eKeyManager.get().lock() }
        runCatching { imageLoader.get().memoryCache?.clear() }
        Thread {
            runCatching { imageLoader.get().diskCache?.clear() }
            co.golink.tester.data.files.ViewerImageCache.clear(context)
        }.start()
        // A WebView só pode ser usada na main thread.
        Handler(Looper.getMainLooper()).post {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            runCatching { WebStorage.getInstance().deleteAllData() }
        }
    }

    private fun scheduleRevoke() {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                LogoutRevokeWorker.WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<LogoutRevokeWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}

/**
 * POST /api/logout com cada token guardado em [TokenStore.addPendingRevoke].
 * O Authorization explícito prevalece sobre o do AuthInterceptor, e o
 * SessionGuardInterceptor ignora 401 de tokens que não são o atual.
 */
@HiltWorker
class LogoutRevokeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val tokenStore: TokenStore,
    private val api: AuthApi,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        var retry = false
        tokenStore.pendingRevokes().forEach { token ->
            // O mesmo token voltou a ser a sessão atual: não o revogar.
            if (token == tokenStore.token) {
                tokenStore.removePendingRevoke(token)
                return@forEach
            }
            val code = runCatching { api.logoutWith("Bearer $token").code() }.getOrNull()
            when {
                // 401/403: já não vale (revogado, expirado, conta suspensa ou apagada).
                code != null && (code in 200..299 || code == 401 || code == 403 || code == 404) ->
                    tokenStore.removePendingRevoke(token)
                else -> retry = true
            }
        }
        return if (retry && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        const val WORK = "logout_revoke"
        private const val MAX_ATTEMPTS = 20
    }
}
