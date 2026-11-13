package co.golink.tester.ui.screens.socialite

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Recebe o regresso do login social aberto em Custom Tabs
 * (<applicationId>.oauth:/callback?code=…&state=… ou ?error=…&state=…),
 * entrega-o ao [SocialiteViewModel] e volta à MainActivity — que é
 * singleTask, por isso o CLEAR_TOP fecha o Custom Tab que ficou por cima.
 *
 * O URI não traz segredos utilizáveis sozinho: o código só se troca com o
 * code_verifier (PKCE) que ficou na app, e o state tem de bater certo.
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { OAuthRedirects.deliver(it) }
        startActivity(
            Intent(this, co.golink.tester.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}

/** Último regresso OAuth por consumir (o ViewModel pode estar a ser recriado). */
object OAuthRedirects {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    fun deliver(uri: Uri) {
        _pending.value = uri
    }

    /** Lê e esquece, para não ser processado duas vezes. */
    fun consume(uri: Uri) {
        _pending.compareAndSet(uri, null)
    }

    val scheme: String get() = "${co.golink.tester.BuildConfig.APPLICATION_ID}.oauth"
}
