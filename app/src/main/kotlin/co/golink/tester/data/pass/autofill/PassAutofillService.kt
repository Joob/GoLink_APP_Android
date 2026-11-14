package co.golink.tester.data.pass.autofill

import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import co.golink.tester.data.pass.PassRepository
import co.golink.tester.data.settings.AppLockManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Preenchimento do Pass noutras apps e no browser.
 * Nada sai do aparelho: os logins vêm do cofre já decifrado em memória (o mesmo processo da app);
 * com o cofre aberto, 1 sync por dia ([PassRepository.touchFromAutofill]) conta como atividade no servidor;
 * trancado → só um "Desbloquear o Pass" que abre o ecrã de desbloqueio.
 */
@AndroidEntryPoint
class PassAutofillService : AutofillService() {
    @Inject lateinit var repo: PassRepository
    @Inject lateinit var appLock: AppLockManager

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        try {
            val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
            val pkg = structure.activityComponent?.packageName
            val target = PassAutofillParser.parse(structure, PassTrustedBrowsers.isVerified(this, pkg))
            if (target.isEmpty || target.packageName == packageName) return callback.onSuccess(null)
            val inline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) request.inlineSuggestionsRequest else null
            // O ticker do auto-lock não corre com o processo congelado: aplicar o lock_after já.
            repo.checkIdle()
            // Bloqueio da app (PIN/biometria) ativo: nada de sugestões sem desbloquear.
            val response = if (repo.isOpen && !appLock.lockedForAutofill()) {
                repo.touchFromAutofill()
                PassAutofillResponses.fill(this, target, repo.state.value, repo.state.value.settings.reprompt && !repo.repromptValid(), inline)
            } else {
                PassAutofillResponses.locked(this, target, inline)
            }
            callback.onSuccess(response)
        } catch (e: Exception) {
            Log.w(TAG, "fill failed", e)
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        try {
            // Juntar todos os ecrãs do fluxo (utilizador num ecrã, password no seguinte).
            val parsed = request.fillContexts.map {
                PassAutofillParser.parse(it.structure, PassTrustedBrowsers.isVerified(this, it.structure.activityComponent?.packageName), withValues = true)
            }
            val last = parsed.lastOrNull() ?: return callback.onSuccess()
            val password = parsed.lastOrNull { it.passwordValue != null }?.passwordValue ?: return callback.onSuccess()
            val username = parsed.lastOrNull { it.usernameValue != null }?.usernameValue
            val target = last.copy(
                webDomain = parsed.firstNotNullOfOrNull { it.webDomain },
                usernameValue = username,
                passwordValue = password,
            )
            if (target.packageName == packageName) return callback.onSuccess()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                callback.onSuccess(PassAutofillResponses.savePendingIntent(this, target))
            } else {
                startActivity(PassAutofillResponses.saveIntent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                callback.onSuccess()
            }
        } catch (e: Exception) {
            Log.w(TAG, "save failed", e)
            callback.onFailure(null)
        }
    }

    private companion object {
        const val TAG = "PassAutofill"
    }
}
