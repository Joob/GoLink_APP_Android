package co.golink.tester.ui.screens.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.dialogs.TextInputDialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ViewerDialog { None, Rename, Delete, Details, Move }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    onClose: () -> Unit,
    viewModel: FileViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val current = state.files.getOrNull(state.currentIndex)

    LaunchedEffect(state.toast) {
        val msg = state.toast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeToast()
    }

    LaunchedEffect(state.closeRequested) {
        if (state.closeRequested) {
            viewModel.acknowledgeClose()
            onClose()
        }
    }

    LaunchedEffect(state.files.isEmpty()) {
        if (state.files.isEmpty()) onClose()
    }

    if (current == null) return

    val scope = rememberCoroutineScope()
    var fileSheetOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(ViewerDialog.None) }

    val showSoon: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar("Em breve") }
        Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                current.isImageLike() -> Icons.Filled.Image
                                current.isPdfLike() -> Icons.Filled.PictureAsPdf
                                current.isAudioLike() -> Icons.Filled.MusicNote
                                current.isVideoLike() -> Icons.Filled.PlayCircle
                                else -> Icons.Filled.Description
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = current.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "(${state.currentIndex + 1} de ${state.files.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.download() }) {
                            Icon(Icons.Outlined.Download, contentDescription = "Descarregar")
                        }
                        if (current.isImageLike() || current.isPdfLike()) {
                            IconButton(onClick = viewModel::rotateLeft) {
                                Icon(Icons.Outlined.RotateLeft, contentDescription = "Rodar à esquerda")
                            }
                            IconButton(onClick = viewModel::rotateRight) {
                                Icon(Icons.Outlined.RotateRight, contentDescription = "Rodar à direita")
                            }
                            IconButton(
                                onClick = { viewModel.setZoom((state.zoom / 1.25f).coerceAtLeast(0.2f)) },
                                enabled = state.zoom > 0.25f,
                            ) {
                                Icon(Icons.Outlined.ZoomOut, contentDescription = "Reduzir zoom")
                            }
                            IconButton(
                                onClick = { viewModel.setZoom((state.zoom * 1.25f).coerceAtMost(8f)) },
                                enabled = state.zoom < 7.9f,
                            ) {
                                Icon(Icons.Outlined.ZoomIn, contentDescription = "Aumentar zoom")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { fileSheetOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = state.currentIndex,
                    pageCount = { state.files.size },
                )
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != state.currentIndex) {
                        viewModel.goTo(pagerState.currentPage)
                    }
                }
                LaunchedEffect(state.currentIndex) {
                    if (pagerState.currentPage != state.currentIndex) {
                        pagerState.scrollToPage(state.currentIndex)
                    }
                }
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = state.zoom <= 1.01f,
                ) { page ->
                    val file = state.files[page]
                    when {
                        file.isImageLike() -> ImageViewer(
                            file = file,
                            url = viewModel.authedUrlFor(file),
                            token = viewModel.authToken(),
                            zoom = if (page == state.currentIndex) state.zoom else 1f,
                            rotation = if (page == state.currentIndex) state.rotation else 0f,
                            onZoomChange = { if (page == state.currentIndex) viewModel.setZoom(it) },
                        )
                        file.isPdfLike() -> PdfViewer(
                            url = viewModel.authedUrlFor(file),
                            token = viewModel.authToken(),
                            fileKey = file.id,
                            zoom = if (page == state.currentIndex) state.zoom else 1f,
                            rotation = if (page == state.currentIndex) state.rotation else 0f,
                            onZoomChange = { if (page == state.currentIndex) viewModel.setZoom(it) },
                        )
                        file.isTextLike() -> TextViewer(
                            if (page == state.currentIndex) state.textContent else null,
                            if (page == state.currentIndex) state.textLoading else false,
                            if (page == state.currentIndex) state.textError else null,
                        )
                        file.isAudioLike() -> AudioViewer(
                            file = file,
                            url = viewModel.authedUrlFor(file),
                            token = viewModel.authToken(),
                            fileKey = file.id,
                            isActive = page == state.currentIndex,
                        )
                        file.isVideoLike() -> VideoViewer(
                            url = viewModel.authedUrlFor(file),
                            token = viewModel.authToken(),
                            fileKey = file.id,
                            isActive = page == state.currentIndex,
                        )
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Pré-visualização não suportada")
                        }
                    }
                }
                if (state.currentIndex > 0) {
                    NavArrow(
                        forward = false,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                        onClick = viewModel::prev,
                    )
                }
                if (state.currentIndex < state.files.lastIndex) {
                    NavArrow(
                        forward = true,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                        onClick = viewModel::next,
                    )
                }
            }
        }
    }

    if (fileSheetOpen) {
        ViewerFileSheet(
            file = current,
            onDismiss = { fileSheetOpen = false },
            onRename = { dialog = ViewerDialog.Rename },
            onMove = {
                viewModel.loadNavigationTree()
                dialog = ViewerDialog.Move
            },
            onDelete = { dialog = ViewerDialog.Delete },
            onDownload = { viewModel.download() },
            onEditShare = { showSoon() },
            onDetails = { dialog = ViewerDialog.Details },
        )
    }

    when (dialog) {
        ViewerDialog.Rename -> TextInputDialog(
            title = "Renomear",
            label = "Novo nome",
            initialValue = current.name,
            confirmText = "Guardar",
            onDismiss = { dialog = ViewerDialog.None },
            onConfirm = { name -> viewModel.rename(name); dialog = ViewerDialog.None },
        )
        ViewerDialog.Delete -> ConfirmDialog(
            title = "Mover para o lixo?",
            message = "${current.name} ficará no lixo e podes restaurar depois.",
            confirmText = "Mover para o lixo",
            destructive = true,
            onDismiss = { dialog = ViewerDialog.None },
            onConfirm = { viewModel.delete(); dialog = ViewerDialog.None },
        )
        ViewerDialog.Details -> DetailsSheet(file = current, onDismiss = { dialog = ViewerDialog.None })
        ViewerDialog.Move -> {
            var createInMoveFor by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
            co.golink.tester.ui.components.dialogs.MoveDestinationDialog(
                sections = state.navigationTree,
                excludeId = current.id,
                onDismiss = { dialog = ViewerDialog.None },
                onConfirm = { destinationId -> viewModel.move(destinationId) },
                onCreateFolder = { parentId -> createInMoveFor = true to parentId },
            )
            createInMoveFor?.let { (_, parentId) ->
                TextInputDialog(
                    title = "Nova pasta",
                    label = "Nome",
                    confirmText = "Criar",
                    onDismiss = { createInMoveFor = null },
                    onConfirm = { name ->
                        viewModel.createFolderIn(name, parentId)
                        createInMoveFor = null
                    },
                )
            }
        }
        ViewerDialog.None -> Unit
    }
}

@Composable
private fun NavArrow(forward: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                if (forward) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (forward) "Seguinte" else "Anterior",
            )
        }
    }
}

@Composable
private fun ImageViewer(
    file: BrowseItem.File,
    url: String,
    token: String?,
    zoom: Float,
    rotation: Float,
    onZoomChange: (Float) -> Unit,
) {
    var bitmap by remember(file.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember(file.id) { mutableStateOf(true) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(file.id) {
        loading = true
        error = null
        bitmap = null
        runCatching {
            withContext(Dispatchers.IO) {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Accept", "*/*")
                if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.inputStream.use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)
                        ?: error("Falha na descodificação")
                }
            }
        }
            .onSuccess { bmp -> bitmap = bmp; loading = false }
            .onFailure { t -> error = t.message ?: "erro"; loading = false }
    }

    var offsetX by remember(file.id) { mutableStateOf(0f) }
    var offsetY by remember(file.id) { mutableStateOf(0f) }
    LaunchedEffect(zoom) { if (zoom <= 1.01f) { offsetX = 0f; offsetY = 0f } }

    val zoomState = androidx.compose.runtime.rememberUpdatedState(zoom)
    val onZoomChangeState = androidx.compose.runtime.rememberUpdatedState(onZoomChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clipToBounds()
            .pointerInput(file.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val currentZoom = zoomState.value
                        if (pressedCount >= 2) {
                            val z = event.calculateZoom()
                            val p = event.calculatePan()
                            if (z != 1f) {
                                onZoomChangeState.value((currentZoom * z).coerceIn(0.2f, 8f))
                                event.changes.forEach { it.consume() }
                            }
                            if (currentZoom > 1.01f && (p.x != 0f || p.y != 0f)) {
                                offsetX += p.x
                                offsetY += p.y
                                event.changes.forEach { it.consume() }
                            }
                        } else if (pressedCount == 1 && currentZoom > 1.01f) {
                            val change = event.changes.first()
                            val delta = change.positionChange()
                            if (delta.x != 0f || delta.y != 0f) {
                                offsetX += delta.x
                                offsetY += delta.y
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text("Erro: $error", color = MaterialTheme.colorScheme.onSurface)
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoom,
                        scaleY = zoom,
                        rotationZ = rotation,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
        }
    }
}

@Composable
private fun TextViewer(content: String?, loading: Boolean, error: String?) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text("Erro: $error")
            content != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
            else -> Text("Sem conteúdo")
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoViewer(url: String, token: String?, fileKey: String, isActive: Boolean) {
    val context = LocalContext.current
    var isBuffering by remember(fileKey) { mutableStateOf(true) }

    val player = remember(fileKey) {
        val headers = if (!token.isNullOrBlank()) mapOf("Authorization" to "Bearer $token") else emptyMap()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(60_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(fileKey) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) player.play() else player.pause()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun AudioViewer(
    file: BrowseItem.File,
    url: String,
    token: String?,
    fileKey: String,
    isActive: Boolean,
) {
    val context = LocalContext.current

    val player = remember(fileKey) {
        val headers = if (!token.isNullOrBlank()) mapOf("Authorization" to "Bearer $token") else emptyMap()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(60_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                playWhenReady = false
                prepare()
            }
    }

    DisposableEffect(fileKey) {
        onDispose { player.release() }
    }

    LaunchedEffect(isActive) {
        if (isActive) player.play() else player.pause()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Moldura do áudio: a capa embebida (frame do mp3) é mostrada contida
        // (ARTWORK_DISPLAY_MODE_FIT) — não ocupa o ecrã todo como um vídeo. Sem
        // capa, o fundo é transparente e deixa ver o ícone musical por trás.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(96.dp),
            )
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        controllerShowTimeoutMs = 0
                        controllerHideOnTouch = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setArtworkDisplayMode(PlayerView.ARTWORK_DISPLAY_MODE_FIT)
                        // Fundo/obturador transparentes: quando não há capa
                        // embebida, vê-se o ícone musical por trás.
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = file.name,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PdfViewer(
    url: String,
    token: String?,
    fileKey: String,
    zoom: Float,
    rotation: Float,
    onZoomChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageBitmaps by remember(fileKey) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember(fileKey) { mutableStateOf<String?>(null) }
    var loading by remember(fileKey) { mutableStateOf(true) }
    var renderer by remember(fileKey) { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember(fileKey) { mutableStateOf<ParcelFileDescriptor?>(null) }

    DisposableEffect(fileKey) {
        loading = true
        error = null
        pageBitmaps = emptyList()
        val job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val cacheFile = File(context.cacheDir, "viewer_pdf_${fileKey}.pdf")
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 60_000
                    conn.inputStream.use { input ->
                        cacheFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    cacheFile
                }
                val openedPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(openedPfd)
                pfd = openedPfd
                renderer = r
                val pages = withContext(Dispatchers.IO) {
                    val list = mutableListOf<Bitmap>()
                    for (i in 0 until r.pageCount) {
                        r.openPage(i).use { page ->
                            val w = page.width * 2
                            val h = page.height * 2
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            list.add(bmp)
                        }
                    }
                    list
                }
                pageBitmaps = pages
                loading = false
            } catch (t: Throwable) {
                error = t.message ?: "erro"
                loading = false
            }
        }
        onDispose {
            job.cancel()
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }

    var offsetX by remember(fileKey) { mutableStateOf(0f) }
    var offsetY by remember(fileKey) { mutableStateOf(0f) }
    LaunchedEffect(zoom) { if (zoom == 1f) { offsetX = 0f; offsetY = 0f } }

    val zoomState = androidx.compose.runtime.rememberUpdatedState(zoom)
    val onZoomChangeState = androidx.compose.runtime.rememberUpdatedState(onZoomChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clipToBounds()
            .pointerInput(fileKey) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val currentZoom = zoomState.value
                        if (pressedCount >= 2) {
                            val z = event.calculateZoom()
                            val p = event.calculatePan()
                            if (z != 1f) {
                                onZoomChangeState.value((currentZoom * z).coerceIn(0.2f, 8f))
                                event.changes.forEach { it.consume() }
                            }
                            if (currentZoom > 1.01f && (p.x != 0f || p.y != 0f)) {
                                offsetX += p.x
                                offsetY += p.y
                                event.changes.forEach { it.consume() }
                            }
                        } else if (pressedCount == 1 && currentZoom > 1.01f) {
                            val change = event.changes.first()
                            val delta = change.positionChange()
                            if (delta.x != 0f || delta.y != 0f) {
                                offsetX += delta.x
                                offsetY += delta.y
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text("Erro: $error", color = MaterialTheme.colorScheme.onSurface)
            pageBitmaps.isEmpty() -> Text("PDF vazio", color = MaterialTheme.colorScheme.onSurface)
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            rotationZ = rotation,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pageBitmaps.size) { idx ->
                        val bmp = pageBitmaps[idx]
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Página ${idx + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                                .background(Color.White),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(file: BrowseItem.File, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text("Detalhes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            DetailRow("Nome", file.name)
            DetailRow("Tipo", file.type)
            DetailRow("Mimetype", file.mimetype ?: "—")
            DetailRow("Tamanho", file.filesize ?: "—")
            DetailRow("Criado", file.createdAt ?: "—")
            DetailRow("Atualizado", file.updatedAt ?: "—")
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerFileSheet(
    file: BrowseItem.File,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onEditShare: () -> Unit,
    onDetails: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            ViewerSheetHeader(file)
            Spacer(Modifier.height(4.dp))
            ViewerActionGroup {
                ViewerActionItem(Icons.Outlined.DriveFileRenameOutline, "Renomear", MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), MaterialTheme.colorScheme.primary) { onRename(); onDismiss() }
                ViewerSheetDivider()
                ViewerActionItem(Icons.AutoMirrored.Outlined.DriveFileMove, "Mover", MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f), MaterialTheme.colorScheme.secondary) { onMove(); onDismiss() }
                ViewerSheetDivider()
                ViewerActionItem(Icons.Outlined.Share, "Editar partilha", MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), MaterialTheme.colorScheme.primary) { onEditShare(); onDismiss() }
                ViewerSheetDivider()
                ViewerActionItem(Icons.Outlined.DeleteOutline, "Eliminar", MaterialTheme.colorScheme.error.copy(alpha = 0.10f), MaterialTheme.colorScheme.error, labelColor = MaterialTheme.colorScheme.error) { onDelete(); onDismiss() }
            }
            Spacer(Modifier.height(8.dp))
            ViewerActionGroup {
                ViewerActionItem(Icons.Outlined.Download, "Descarregar", MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f), MaterialTheme.colorScheme.tertiary) { onDownload(); onDismiss() }
                ViewerSheetDivider()
                ViewerActionItem(Icons.Outlined.Info, "Detalhes", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) { onDetails(); onDismiss() }
            }
        }
    }
}

@Composable
private fun ViewerActionGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        content()
    }
}

@Composable
private fun ViewerSheetDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(start = 62.dp, end = 16.dp),
    )
}

@Composable
private fun ViewerActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconBg: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    labelColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (labelColor == androidx.compose.ui.graphics.Color.Unspecified) MaterialTheme.colorScheme.onSurface else labelColor,
        )
    }
}

@Composable
private fun ViewerSheetHeader(file: BrowseItem.File) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    file.isImageLike() -> Icons.Filled.Image
                    file.isPdfLike() -> Icons.Filled.PictureAsPdf
                    file.isAudioLike() -> Icons.Filled.MusicNote
                    file.isVideoLike() -> Icons.Filled.PlayCircle
                    else -> Icons.Filled.Description
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                file.type.takeIf { it.isNotBlank() },
                file.filesize,
                (file.createdAt ?: file.updatedAt)?.take(10),
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
