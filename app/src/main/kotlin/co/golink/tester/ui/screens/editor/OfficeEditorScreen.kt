package co.golink.tester.ui.screens.editor

import co.golink.tester.ui.i18n.tr
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.ui.theme.LocalDarkTheme

/**
 * Editor de documentos (docx/xlsx): o editor da web (/editor-embed) numa
 * WebView. A página não tem sessão — fala com a app por window.GoLinkEditor
 * (ver [OfficeEditorViewModel]). Fechar e ir para segundo plano gravam antes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficeEditorScreen(
    onClose: () -> Unit,
    viewModel: OfficeEditorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val closeRequested by viewModel.closeRequested.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    LaunchedEffect(closeRequested) { if (closeRequested) onClose() }
    LaunchedEffect(Unit) {
        viewModel.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Inserir imagem no documento (<input type="file"> da página).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    // Voltar: a página grava o que falta e depois pede para fechar.
    BackHandler {
        val view = webView
        if (view == null || loading || failed) onClose()
        else view.evaluateJavascript("window.GoLinkEmbed ? (window.GoLinkEmbed.close(), 1) : 0") { result ->
            if (result != "1") onClose()
        }
    }

    // Segundo plano: grava já (a app pode ser morta a seguir).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                webView?.evaluateJavascript("window.GoLinkEmbed && window.GoLinkEmbed.flush()", null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val editorUri = remember { Uri.parse(viewModel.editorUrl) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        if (viewModel.file == null) {
            EditorMessage("O ficheiro já não está disponível".tr(), onRetry = null, onClose = onClose)
            return@Box
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportZoom(false)
                    setBackgroundColor(if (dark) 0xFF151515.toInt() else 0xFFFFFFFF.toInt())

                    addJavascriptInterface(EditorBridge(viewModel, dark) { webView }, "GoLinkEditor")

                    webViewClient = object : WebViewClient() {
                        // Só a página do editor corre aqui dentro (é a que tem a ponte);
                        // ligações dos documentos abrem no browser.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            if (url.host == editorUri.host && url.path == editorUri.path) return false
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                failed = true
                                loading = false
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                            fileCallback?.onReceiveValue(null)
                            fileCallback = callback
                            val accept = params.acceptTypes.orEmpty().flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
                            val mime = when {
                                accept.isEmpty() -> "*/*"
                                accept.all { it.startsWith("image/") } -> "image/*"
                                else -> accept.first()
                            }
                            return runCatching { picker.launch(mime) }.isSuccess.also { ok ->
                                if (!ok) {
                                    fileCallback = null
                                    callback.onReceiveValue(null)
                                }
                            }
                        }
                    }

                    loadUrl(viewModel.editorUrl)
                    webView = this
                }
            },
            onRelease = { view ->
                view.removeJavascriptInterface("GoLinkEditor")
                view.destroy()
            },
        )

        if (failed) {
            EditorMessage(
                "Não foi possível abrir o editor. Verifica a ligação.".tr(),
                onRetry = {
                    failed = false
                    loading = true
                    webView?.loadUrl(viewModel.editorUrl)
                },
                onClose = onClose,
            )
        } else if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun EditorMessage(message: String, onRetry: (() -> Unit)?, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose) { Text("Fechar".tr()) }
            if (onRetry != null) Button(onClick = onRetry) { Text("Tentar novamente".tr()) }
        }
    }
}

/**
 * window.GoLinkEditor. Os métodos correm numa thread do WebView: o trabalho vai
 * para o ViewModel e a resposta volta pela thread principal (__glkResolve).
 */
private class EditorBridge(
    private val viewModel: OfficeEditorViewModel,
    private val dark: Boolean,
    private val webView: () -> WebView?,
) {
    @JavascriptInterface
    fun info(): String = viewModel.infoJson(dark)

    @JavascriptInterface
    fun request(id: Int, method: String, args: String) {
        viewModel.request(id, method, args) { done ->
            webView()?.post { webView()?.evaluateJavascript("window.__glkResolve && window.__glkResolve($done)", null) }
        }
    }

    @JavascriptInterface
    fun take(id: Int): String = viewModel.take(id)

    @JavascriptInterface
    fun toast(message: String) = viewModel.toast(message)

    @JavascriptInterface
    fun close() = viewModel.requestClose()
}
