package co.golink.tester.ui.screens.socialite

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialiteWebViewScreen(
    provider: String,
    onCompleted: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SocialiteViewModel = hiltViewModel(),
) {
    LaunchedEffect(provider) { viewModel.start(provider) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.tokenAdopted) {
        if (state.tokenAdopted) onCompleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entrar com ${provider.replaceFirstChar { it.titlecase() }}") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                    }
                },
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val url = state.initialUrl
            val signInHost = state.backendHost
            val signInPath = state.signInPath
            when {
                state.loadingUrl -> CenteredLoading()
                state.error != null -> ErrorBlock(message = state.error!!, onRetry = { viewModel.start(provider) })
                url != null && signInHost != null -> OauthWebView(
                    initialUrl = url,
                    backendHost = signInHost,
                    signInPath = signInPath,
                    onSignInLanded = { backendOrigin ->
                        val cookies = CookieManager.getInstance().getCookie(backendOrigin).orEmpty()
                        viewModel.onCallbackLanded(cookies)
                    },
                )
                else -> ErrorBlock(message = "Configuração do servidor inválida", onRetry = onCancel)
            }
            if (state.finishingAuth) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OauthWebView(
    initialUrl: String,
    backendHost: String,
    signInPath: String,
    onSignInLanded: (backendOrigin: String) -> Unit,
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val parsed = url?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                    val isSignIn = parsed?.host == backendHost &&
                        (parsed.path == signInPath || parsed.path?.startsWith("$signInPath?") == true)
                    if (isSignIn) {
                        onSignInLanded("${parsed.scheme}://${parsed.host}")
                    }
                }
            }
            loadUrl(initialUrl)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Tentar de novo") }
    }
}
