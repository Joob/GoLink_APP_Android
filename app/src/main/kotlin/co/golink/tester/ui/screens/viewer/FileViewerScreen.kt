package co.golink.tester.ui.screens.viewer

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Link
import co.golink.tester.ui.i18n.tr
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.material3.OutlinedTextField
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
import co.golink.tester.ui.components.FileTypeIcon
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.dialogs.ShareDialog
import co.golink.tester.ui.components.dialogs.ShareDialogState
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class ViewerDialog { None, Rename, Delete, DeleteForever, Details, Move }

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
        scope.launch { snackbarHostState.showSnackbar("Em breve".tr()) }
        Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Barra como na web: ‹, extensão, nome + "13.51 MB · 3 de 10",
                // Partilhar, Detalhes e claro/escuro.
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr())
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ext = current.name.substringAfterLast('.', "").take(4).uppercase()
                        if (ext.isNotEmpty()) {
                            Text(
                                ext,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.14f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            FileTypeIcon(current, Modifier.height(24.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                text = current.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = listOfNotNull(current.filesize, "%d de %d".tr().format(state.currentIndex + 1, state.files.size)).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (!viewModel.fromTrash) {
                        IconButton(onClick = { viewModel.openShareDialog() }) {
                            Icon(Icons.Outlined.Link, contentDescription = "Partilhar".tr())
                        }
                        IconButton(onClick = { dialog = ViewerDialog.Details }) {
                            Icon(Icons.Outlined.Info, contentDescription = "Detalhes".tr())
                        }
                    }
                    co.golink.tester.ui.theme.ThemeToggle(Modifier.padding(end = 8.dp))
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
                            Icon(Icons.Outlined.Download, contentDescription = "Descarregar".tr())
                        }
                        if (current.isImageLike() || current.isPdfLike()) {
                            IconButton(onClick = viewModel::rotateLeft) {
                                Icon(Icons.Outlined.RotateLeft, contentDescription = "Rodar à esquerda".tr())
                            }
                            IconButton(onClick = viewModel::rotateRight) {
                                Icon(Icons.Outlined.RotateRight, contentDescription = "Rodar à direita".tr())
                            }
                            // PDF: o zoom está na barra flutuante do leitor.
                            if (current.isImageLike()) {
                            IconButton(
                                onClick = { viewModel.setZoom((state.zoom / 1.25f).coerceAtLeast(0.2f)) },
                                enabled = state.zoom > 0.25f,
                            ) {
                                Icon(Icons.Outlined.ZoomOut, contentDescription = "Reduzir zoom".tr())
                            }
                            IconButton(
                                onClick = { viewModel.setZoom((state.zoom * 1.25f).coerceAtMost(8f)) },
                                enabled = state.zoom < 7.9f,
                            ) {
                                Icon(Icons.Outlined.ZoomIn, contentDescription = "Aumentar zoom".tr())
                            }
                            }
                        }
                        // Editor de texto: editar / guardar em linha.
                        // Ver | Editar (como na web); a editar, Guardar ao lado.
                        if (current.isTextLike() && viewModel.canEditText()) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .padding(3.dp),
                            ) {
                                listOf(false to "Ver".tr(), true to "Editar".tr()).forEach { (edit, label) ->
                                    val active = state.editing == edit
                                    Text(
                                        label,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                            .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                                            .clickable(enabled = !state.saving) {
                                                if (edit && !state.editing) viewModel.startEditing()
                                                if (!edit && state.editing) viewModel.cancelEditing()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                            if (state.editing) {
                                Spacer(Modifier.width(6.dp))
                                androidx.compose.material3.Button(
                                    onClick = viewModel::saveText,
                                    enabled = !state.saving && state.dirty,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp),
                                ) { Text(if (state.saving) "A guardar…".tr() else "Guardar".tr(), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // No Lixo não há renomear/mover/partilhar: o menu fica só para o modo normal.
                        if (!viewModel.fromTrash) {
                            IconButton(onClick = { fileSheetOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções".tr())
                            }
                        }
                    }
                    if (viewModel.fromTrash) {
                        TrashViewerBanner(
                            onRestore = viewModel::restoreFromTrash,
                            onDeleteForever = { dialog = ViewerDialog.DeleteForever },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = state.currentIndex,
                    pageCount = { state.files.size },
                )
                // O índice do ViewModel só muda quando o swipe ASSENTA (settledPage).
                // Com currentPage mudava a meio do gesto (aos 50%), o que fazia
                // recompor o ecrã todo durante o fling e, num swipe rápido, o
                // efeito inverso fazia scrollToPage para a página antiga (salto).
                val currentIndexState = androidx.compose.runtime.rememberUpdatedState(state.currentIndex)
                LaunchedEffect(pagerState) {
                    androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { page ->
                        if (page != currentIndexState.value) viewModel.goTo(page)
                    }
                }
                // Setas/ações do ViewModel: anima até à página em vez de saltar.
                LaunchedEffect(state.currentIndex) {
                    if (!pagerState.isScrollInProgress && pagerState.settledPage != state.currentIndex) {
                        pagerState.animateScrollToPage(state.currentIndex)
                    }
                }
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Pré-carrega a página de cada lado: ao começar o swipe a
                    // imagem seguinte já está descodificada.
                    beyondViewportPageCount = 1,
                    userScrollEnabled = state.zoom <= 1.01f,
                ) { page ->
                    val file = state.files[page]
                    when {
                        file.isImageLike() -> ImageViewer(
                            file = file,
                            // E2E: usa o download-by-id (que faz dual-read do bucket cifrado).
                            url = if (file.encrypted) viewModel.authedUrlFor(file) else viewModel.authedUrlFor(file),
                            token = viewModel.authToken(),
                            zoom = if (page == state.currentIndex) state.zoom else 1f,
                            rotation = if (page == state.currentIndex) state.rotation else 0f,
                            onZoomChange = { if (page == state.currentIndex) viewModel.setZoom(it) },
                            decrypt = if (file.encrypted) { { c -> viewModel.decryptImageBytes(file, c) } } else null,
                            cache = viewModel.bitmapCache,
                        )
                        file.isPdfLike() -> PdfViewer(
                            url = viewModel.authedUrlFor(file),
                            token = viewModel.authToken(),
                            fileKey = file.id,
                            zoom = if (page == state.currentIndex) state.zoom else 1f,
                            rotation = if (page == state.currentIndex) state.rotation else 0f,
                            onZoomChange = { if (page == state.currentIndex) viewModel.setZoom(it) },
                            decrypt = if (file.encrypted) { { c -> viewModel.decryptImageBytes(file, c) } } else null,
                        )
                        file.isTextLike() -> TextReader(
                            name = file.name,
                            content = if (page == state.currentIndex) state.textContent else null,
                            loading = if (page == state.currentIndex) state.textLoading else false,
                            error = if (page == state.currentIndex) state.textError else null,
                            encrypted = file.encrypted,
                            editing = page == state.currentIndex && state.editing,
                            draft = state.draft,
                            onDraftChange = viewModel::updateDraft,
                        )
                        // Vizinhos de áudio/vídeo não criam player nem decifram: só a
                        // página ativa (senão o vídeo ao lado começava a descarregar).
                        (file.isAudioLike() || file.isVideoLike()) && page != state.currentIndex -> MediaLoadingBox(showSpinner = false)
                        file.isAudioLike() -> {
                            val src = rememberPlayableUrl(file, viewModel)
                            if (src == null) MediaLoadingBox()
                            else AudioViewer(
                                file = file,
                                url = src,
                                token = viewModel.authToken(),
                                fileKey = file.id,
                                isActive = page == state.currentIndex,
                            )
                        }
                        file.isVideoLike() -> {
                            val src = rememberPlayableUrl(file, viewModel)
                            if (src == null) MediaLoadingBox()
                            else VideoViewer(
                                url = src,
                                token = viewModel.authToken(),
                                fileKey = file.id,
                                isActive = page == state.currentIndex,
                            )
                        }
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Pré-visualização não suportada".tr())
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
            // Tira de miniaturas (como na web), só com vários ficheiros.
            if (state.files.size > 1 && !state.editing) {
                ThumbnailStrip(files = state.files, current = state.currentIndex, onSelect = viewModel::goTo)
            }
        }
    }

    if (fileSheetOpen) {
        // Mesmo menu de ações dos Ficheiros/Partilhas/Lixo.
        co.golink.tester.ui.components.ItemActionsSheet(
            item = current,
            isFavourite = false,
            isShared = current.share != null,
            onDismiss = { fileSheetOpen = false },
            onDownload = { viewModel.download() },
            onShare = { viewModel.openShareDialog() },
            onToggleFavourite = {},
            onRename = { dialog = ViewerDialog.Rename },
            onMove = {
                viewModel.loadNavigationTree()
                dialog = ViewerDialog.Move
            },
            onDelete = { dialog = ViewerDialog.Delete },
            onDetails = { dialog = ViewerDialog.Details },
            showFolderCollaboration = false,
        )
    }

    val shareState by viewModel.shareState.collectAsStateWithLifecycle()
    shareState?.let { ss ->
        ShareDialog(
            state = ShareDialogState(
                item = ss.item,
                share = ss.share,
                qrSvg = ss.qrSvg,
                sendingEmail = ss.sendingEmail,
                loadingQr = ss.loadingQr,
                isWorking = ss.isWorking,
                emailDialogVisible = ss.emailDialogVisible,
            ),
            onDismiss = { viewModel.closeShareDialog() },
            onCreate = { pwd, perm, minutes, limit, views -> viewModel.createShare(pwd, perm, minutes, limit, views) },
            onUpdate = { pwd, perm, minutes, limit, views, prot -> viewModel.updateCurrentShare(pwd, perm, minutes, limit, views, prot) },
            onCopy = { /* clipboard handled inside dialog */ },
            onShowQr = viewModel::fetchQrCode,
            onSendEmail = viewModel::sendShareEmail,
            onShowEmailDialog = viewModel::setEmailDialogVisible,
            onRevoke = viewModel::revokeCurrentShare,
        )
    }

    when (dialog) {
        ViewerDialog.Rename -> TextInputDialog(
            title = "Renomear".tr(),
            label = "Novo nome".tr(),
            initialValue = current.name,
            confirmText = "Guardar".tr(),
            onDismiss = { dialog = ViewerDialog.None },
            onConfirm = { name -> viewModel.rename(name); dialog = ViewerDialog.None },
        )
        ViewerDialog.Delete -> ConfirmDialog(
            title = "Mover para o lixo?".tr(),
            message = "${current.name} ficará no lixo e podes restaurar depois.",
            confirmText = "Mover para o lixo".tr(),
            destructive = true,
            onDismiss = { dialog = ViewerDialog.None },
            onConfirm = { viewModel.delete(); dialog = ViewerDialog.None },
        )
        ViewerDialog.DeleteForever -> ConfirmDialog(
            title = "Eliminar para sempre?".tr(),
            message = "${current.name} " + "será eliminado definitivamente e não pode ser recuperado.".tr(),
            confirmText = "Eliminar para sempre".tr(),
            destructive = true,
            onDismiss = { dialog = ViewerDialog.None },
            onConfirm = { viewModel.deleteForever(); dialog = ViewerDialog.None },
        )
        ViewerDialog.Details -> co.golink.tester.ui.components.ItemDetailsSheet(
            item = current,
            onDismiss = { dialog = ViewerDialog.None },
            onShare = { viewModel.openShareDialog() },
            fetchShare = viewModel::fetchShare,
        )
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
                    title = "Nova pasta".tr(),
                    label = "Nome",
                    confirmText = "Criar".tr(),
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
                contentDescription = if (forward) "Seguinte".tr() else "Anterior",
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
    decrypt: (suspend (ByteArray) -> ByteArray?)? = null,
    cache: android.util.LruCache<String, Bitmap>? = null,
) {
    // Voltar a uma imagem já vista é instantâneo: vem da cache do ViewModel.
    var bitmap by remember(file.id) { mutableStateOf(cache?.get(file.id)) }
    var loading by remember(file.id) { mutableStateOf(bitmap == null) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }
    val metrics = LocalContext.current.resources.displayMetrics
    // Descodifica ao tamanho do ecrã (com margem para zoom), não à resolução
    // original: uma foto de 12 MP eram ~48 MB por bitmap — pausas de GC no swipe.
    val maxSide = (maxOf(metrics.widthPixels, metrics.heightPixels) * 1.5f).toInt()

    LaunchedEffect(file.id) {
        if (bitmap != null) return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Accept", "*/*")
                if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.inputStream.use { input ->
                    // Lê os bytes uma vez para decodificar E ler a orientação EXIF
                    // do mesmo conteúdo. Sem isto as fotos apareciam de lado (o
                    // BitmapFactory ignora a orientação; o Coil, usado nas
                    // miniaturas, aplica-a — daí a diferença).
                    val raw = input.readBytes()
                    // E2E: se cifrado, decifra os bytes antes de descodificar.
                    val bytes = if (decrypt != null) (decrypt(raw) ?: error("Falha ao decifrar".tr())) else raw
                    val bmp = decodeSampled(bytes, maxSide)
                        ?: error("Falha na descodificação".tr())
                    val orientation = runCatching {
                        android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                            .getAttributeInt(
                                android.media.ExifInterface.TAG_ORIENTATION,
                                android.media.ExifInterface.ORIENTATION_NORMAL,
                            )
                    }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
                    applyExifOrientation(bmp, orientation)
                }
            }
        }
            .onSuccess { bmp -> cache?.put(file.id, bmp); bitmap = bmp; loading = false }
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
                                onZoomChangeState.value((currentZoom * z).coerceIn(1f, 8f))
                                event.changes.forEach { it.consume() }
                            }
                            if (currentZoom > 1.01f && (p.x != 0f || p.y != 0f)) {
                                offsetX = clampPan(offsetX + p.x, size.width, currentZoom)
                                offsetY = clampPan(offsetY + p.y, size.height, currentZoom)
                                event.changes.forEach { it.consume() }
                            }
                        } else if (pressedCount == 1 && currentZoom > 1.01f) {
                            val change = event.changes.first()
                            val delta = change.positionChange()
                            if (delta.x != 0f || delta.y != 0f) {
                                offsetX = clampPan(offsetX + delta.x, size.width, currentZoom)
                                offsetY = clampPan(offsetY + delta.y, size.height, currentZoom)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Enquanto a original carrega, mostra a miniatura (já em cache do
            // Coil, vinda da grelha) em vez de um ecrã vazio com spinner.
            loading && !file.encrypted && !file.thumbnailUrl.isNullOrBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = file.thumbnailUrl,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
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

// Pan limitado às bordas do conteúdo ampliado (antes podia arrastar-se para fora).
private fun clampPan(value: Float, extent: Int, zoom: Float): Float {
    val max = extent * (zoom - 1f) / 2f
    return value.coerceIn(-max, max)
}

// Descodifica com inSampleSize (potência de 2) para o lado maior ficar perto
// de maxSide — muito menos memória e descodificação mais rápida.
private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (maxSide > 0) while (longest / (sample * 2) >= maxSide) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

// Roda/espelha o bitmap segundo a orientação EXIF para a foto aparecer direita.
private fun applyExifOrientation(bmp: android.graphics.Bitmap, orientation: Int): android.graphics.Bitmap {
    val m = android.graphics.Matrix()
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
        android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        else -> return bmp
    }
    return runCatching {
        android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }.getOrDefault(bmp)
}

// E2E: para media cifrado devolve um file:// local já decifrado (seek nativo);
// senão o URL HTTP normal. null enquanto decifra (mostra loading).
@Composable
private fun rememberPlayableUrl(file: BrowseItem.File, viewModel: FileViewerViewModel): String? {
    if (!file.encrypted) return viewModel.authedUrlFor(file)
    val state = androidx.compose.runtime.produceState<String?>(null, file.id) {
        value = viewModel.decryptedMediaFile(file)?.let { android.net.Uri.fromFile(it).toString() }
    }
    return state.value
}

@Composable
private fun MediaLoadingBox(showSpinner: Boolean = true) {
    // Aparece enquanto um media E2E está a ser decifrado (os não cifrados têm
    // URL imediato) e, sem spinner, nas páginas vizinhas de áudio/vídeo.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (showSpinner) CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun TextViewer(
    content: String?,
    loading: Boolean,
    error: String?,
    encrypted: Boolean = false,
    editing: Boolean = false,
    draft: String = "",
    onDraftChange: (String) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        when {
            editing -> OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
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
            else -> Text("Sem conteúdo".tr())
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
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(60_000)
        // Envolve o factory HTTP num DefaultDataSource para também abrir file://:
        // vídeos/áudio E2E são decifrados para um ficheiro local antes de tocar e
        // um factory só-HTTP não abre file://, por isso a reprodução falhava.
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                playWhenReady = isActive
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
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(60_000)
        // Envolve o factory HTTP num DefaultDataSource para também abrir file://:
        // vídeos/áudio E2E são decifrados para um ficheiro local antes de tocar e
        // um factory só-HTTP não abre file://, por isso a reprodução falhava.
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
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
    decrypt: (suspend (ByteArray) -> ByteArray?)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageCount by remember(fileKey) { mutableStateOf(0) }
    // O PdfRenderer só deixa ter uma página aberta de cada vez.
    val renderLock = remember(fileKey) { kotlinx.coroutines.sync.Mutex() }
    var error by remember(fileKey) { mutableStateOf<String?>(null) }
    var loading by remember(fileKey) { mutableStateOf(true) }
    var renderer by remember(fileKey) { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember(fileKey) { mutableStateOf<ParcelFileDescriptor?>(null) }

    DisposableEffect(fileKey) {
        loading = true
        error = null
        pageCount = 0
        val job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val cacheFile = File(context.cacheDir, "viewer_pdf_${fileKey}.pdf")
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 60_000
                    try {
                        if (decrypt != null) {
                            // E2E: o download é ciphertext — decifrar antes de dar ao PdfRenderer
                            // (senão: "Unable to load the document").
                            val cipher = conn.inputStream.use { it.readBytes() }
                            val plain = decrypt(cipher) ?: error("decifra falhou (E2E bloqueado ou sem chave)")
                            cacheFile.writeBytes(plain)
                            plain.fill(0)
                        } else {
                            conn.inputStream.use { input ->
                                cacheFile.outputStream().use { out -> input.copyTo(out) }
                            }
                        }
                    } catch (t: Throwable) {
                        cacheFile.delete()
                        throw t
                    }
                    cacheFile
                }
                val openedPfd = try {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } finally {
                    // Só unlink (NÃO sobrescrever: o descritor aberto lê o mesmo inode).
                    // O PdfRenderer continua a ler pelo descritor; o ficheiro em claro
                    // deixa de existir na cache.
                    withContext(Dispatchers.IO) { file.delete() }
                }
                val r = PdfRenderer(openedPfd)
                pfd = openedPfd
                renderer = r
                // As páginas são renderizadas à medida que aparecem (PdfPage),
                // não todas antes de mostrar a primeira.
                pageCount = r.pageCount
                loading = false
            } catch (t: Throwable) {
                error = t.message ?: "erro"
                loading = false
            }
        }
        onDispose {
            job.cancel()
            val r = renderer
            val d = pfd
            // Fecha depois de qualquer página que esteja a meio de renderizar
            // (fechar com uma página aberta lança e deixava o renderer pendurado).
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                renderLock.withLock {
                    try { r?.close() } catch (_: Throwable) {}
                    try { d?.close() } catch (_: Throwable) {}
                }
            }
        }
    }

    var offsetX by remember(fileKey) { mutableStateOf(0f) }
    var offsetY by remember(fileKey) { mutableStateOf(0f) }
    LaunchedEffect(zoom) { if (zoom == 1f) { offsetX = 0f; offsetY = 0f } }

    // Leitor (como na web): página atual, miniaturas e fundo de leitura.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentPage by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    var readingTheme by remember { mutableStateOf(PdfReaderPrefs.theme(context)) }
    var showPages by remember(fileKey) { mutableStateOf(false) }
    val pageFilter = remember(readingTheme) { pdfThemeFilter(readingTheme) }

    val zoomState = androidx.compose.runtime.rememberUpdatedState(zoom)
    val onZoomChangeState = androidx.compose.runtime.rememberUpdatedState(onZoomChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                when (readingTheme) {
                    "sepia" -> Color(0xFFE9E3D6)
                    "dark" -> Color(0xFF18191B)
                    else -> if (co.golink.tester.ui.theme.LocalDarkTheme.current) Color(0xFF0D0E10) else Color(0xFFEBECEE)
                },
            )
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
                                onZoomChangeState.value((currentZoom * z).coerceIn(1f, 8f))
                                event.changes.forEach { it.consume() }
                            }
                            if (currentZoom > 1.01f && (p.x != 0f || p.y != 0f)) {
                                offsetX = clampPan(offsetX + p.x, size.width, currentZoom)
                                offsetY = clampPan(offsetY + p.y, size.height, currentZoom)
                                event.changes.forEach { it.consume() }
                            }
                        } else if (pressedCount == 1 && currentZoom > 1.01f) {
                            val change = event.changes.first()
                            val delta = change.positionChange()
                            if (delta.x != 0f || delta.y != 0f) {
                                offsetX = clampPan(offsetX + delta.x, size.width, currentZoom)
                                offsetY = clampPan(offsetY + delta.y, size.height, currentZoom)
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
            pageCount == 0 -> Text("PDF vazio".tr(), color = MaterialTheme.colorScheme.onSurface)
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            rotationZ = rotation,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(pageCount) { idx ->
                        val r = renderer
                        if (r != null) PdfPage(r, renderLock, idx, filter = pageFilter)
                    }
                }
                // Barra flutuante: páginas · ⌃ 2 de 6 ⌄ · − % + · fundo de leitura.
                PdfReaderBar(
                    page = currentPage,
                    total = pageCount,
                    zoom = zoom,
                    theme = readingTheme,
                    onPages = { showPages = true },
                    onGoTo = { n -> scope.launch { listState.animateScrollToItem((n - 1).coerceIn(0, pageCount - 1)) } },
                    onZoom = { z -> onZoomChangeState.value(z.coerceIn(1f, 8f)) },
                    onTheme = { t -> readingTheme = t; PdfReaderPrefs.setTheme(context, t) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                )
                if (showPages) {
                    val r = renderer
                    if (r != null) PdfPagesSheet(
                        renderer = r,
                        lock = renderLock,
                        pageCount = pageCount,
                        current = currentPage,
                        onDismiss = { showPages = false },
                        onSelect = { n -> showPages = false; scope.launch { listState.scrollToItem(n - 1) } },
                    )
                }
            }
        }
    }
}

// Uma página do PDF, renderizada só quando entra no ecrã (o LazyColumn
// descarta-a ao sair). Até lá ocupa o espaço com a proporção certa.
@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    lock: kotlinx.coroutines.sync.Mutex,
    index: Int,
    filter: androidx.compose.ui.graphics.ColorFilter? = null,
    // Miniaturas: renderiza pequeno (bem mais leve).
    targetWidthPx: Int? = null,
) {
    val width = targetWidthPx ?: LocalContext.current.resources.displayMetrics.widthPixels
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(null, renderer, index) {
        value = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    renderer.openPage(index).use { page ->
                        // Largura do ecrã ×2 (nitidez com zoom), limitada ao dobro da página.
                        val w = (if (targetWidthPx != null) width else minOf(width * 2, page.width * 3)).coerceAtLeast(1)
                        val h = (w.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }.getOrNull()
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Página ${index + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                .shadow(if (targetWidthPx == null) 3.dp else 0.dp, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(Color.White),
            contentScale = ContentScale.Fit,
            colorFilter = filter,
        )
    } else {
        Box(
            // Proporção A4 até a página estar pronta.
            modifier = Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
    }
}






/** Faixa do modo Lixo: o ficheiro só pode ser visto; restaurar ou eliminar de vez. */
@Composable
private fun TrashViewerBanner(onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    val amber = androidx.compose.ui.graphics.Color(0xFFF59E0B)
    androidx.compose.material3.Surface(
        color = amber.copy(alpha = 0.12f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, amber.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Está no Lixo — só pode ser visto.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = onRestore) {
                Text("Restaurar".tr(), style = MaterialTheme.typography.labelMedium)
            }
            androidx.compose.material3.TextButton(onClick = onDeleteForever) {
                Text(
                    "Eliminar".tr(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ThumbnailStrip(files: List<BrowseItem.File>, current: Int, onSelect: (Int) -> Unit) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current) { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(files.size, key = { files[it].id }) { i ->
                val active = i == current
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .border(
                            if (active) 2.dp else 1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    co.golink.tester.ui.components.FilePreview(item = files[i], iconSize = 28.dp, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ---- Leitor de PDF: barra flutuante, miniaturas e fundos de leitura ----------------

private object PdfReaderPrefs {
    fun theme(context: android.content.Context): String =
        context.getSharedPreferences("pdf_reader", android.content.Context.MODE_PRIVATE).getString("theme", "light") ?: "light"

    fun setTheme(context: android.content.Context, theme: String) {
        context.getSharedPreferences("pdf_reader", android.content.Context.MODE_PRIVATE).edit().putString("theme", theme).apply()
    }
}

// Sépia (tom quente) e escuro (inverte as cores da página), como na web.
private fun pdfThemeFilter(theme: String): androidx.compose.ui.graphics.ColorFilter? = when (theme) {
    "sepia" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(
            floatArrayOf(
                0.92f, 0.08f, 0.02f, 0f, 0f,
                0.06f, 0.86f, 0.04f, 0f, 0f,
                0.04f, 0.06f, 0.70f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    "dark" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(
            floatArrayOf(
                -0.85f, 0f, 0f, 0f, 235f,
                0f, -0.85f, 0f, 0f, 235f,
                0f, 0f, -0.85f, 0f, 235f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    else -> null
}

@Composable
private fun PdfReaderBar(
    page: Int,
    total: Int,
    zoom: Float,
    theme: String,
    onPages: () -> Unit,
    onGoTo: (Int) -> Unit,
    onZoom: (Float) -> Unit,
    onTheme: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = Color(0xFFE5E7EB)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .shadow(10.dp, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(Color(0xFF111827), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onPages, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.GridView, contentDescription = "Páginas".tr(), tint = fg, modifier = Modifier.size(17.dp))
        }
        BarDivider()
        IconButton(onClick = { onGoTo(page - 1) }, enabled = page > 1, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Página anterior".tr(), tint = fg.copy(alpha = if (page > 1) 1f else 0.35f))
        }
        Text("%d de %d".tr().format(page, total), color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onGoTo(page + 1) }, enabled = page < total, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Página seguinte".tr(), tint = fg.copy(alpha = if (page < total) 1f else 0.35f))
        }
        BarDivider()
        IconButton(onClick = { onZoom(zoom / 1.25f) }, enabled = zoom > 1.01f, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Reduzir zoom".tr(), tint = fg.copy(alpha = if (zoom > 1.01f) 1f else 0.35f), modifier = Modifier.size(16.dp))
        }
        Text(
            "${(zoom * 100).toInt()}%",
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onZoom(1f) }.padding(horizontal = 2.dp),
        )
        IconButton(onClick = { onZoom(zoom * 1.25f) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Aumentar zoom".tr(), tint = fg, modifier = Modifier.size(16.dp))
        }
        BarDivider()
        listOf("light" to Color.White, "sepia" to Color(0xFFEFE6D2), "dark" to Color(0xFF26282B)).forEach { (key, color) ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (theme == key) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f))
                    .padding(2.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color)
                    .clickable { onTheme(key) },
            )
        }
    }
}

@Composable
private fun BarDivider() {
    Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 18.dp).background(Color.White.copy(alpha = 0.16f)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfPagesSheet(
    renderer: PdfRenderer,
    lock: kotlinx.coroutines.sync.Mutex,
    pageCount: Int,
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "Páginas".tr(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            state = gridState,
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(max = 560.dp),
        ) {
            items(pageCount) { idx ->
                val active = idx + 1 == current
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(idx + 1) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                            )
                            .padding(2.dp),
                    ) {
                        PdfPage(renderer, lock, idx, targetWidthPx = 240)
                    }
                    Text(
                        "${idx + 1}",
                        fontSize = 11.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
