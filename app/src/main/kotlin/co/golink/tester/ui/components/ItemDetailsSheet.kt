package co.golink.tester.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.ShareInfo
import co.golink.tester.ui.i18n.tr
import kotlinx.coroutines.delay

/**
 * Detalhes de um item, com a partilha completa (link, validade que falta,
 * visualizações, downloads, password, permissão). Mesma informação que o
 * painel lateral da web. `onShare` abre o diálogo de partilha — é por lá que
 * se copia o link de itens cifrados, porque só o diálogo junta a chave (#k=).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailsSheet(
    item: BrowseItem,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    // Lê a partilha de novo no servidor. Result null = já não existe.
    fetchShare: (suspend (String) -> Result<ShareInfo?>)? = null,
) {
    // Visualizações/downloads são gastos por quem abre o link, fora da app:
    // com limites ativos a folha vai buscar o estado de 8 em 8 s, para o
    // "0/2" mudar à frente do dono em vez de ficar parado.
    var liveShare by remember(item.id) { mutableStateOf(item.share) }
    val token = item.share?.token
    val hasLimits = (item.share?.viewLimit ?: 0) > 0 || (item.share?.downloadLimit ?: 0) > 0
    if (fetchShare != null && token != null && hasLimits) {
        LaunchedEffect(token) {
            while (true) {
                fetchShare(token).onSuccess { liveShare = it }
                if (liveShare == null) break
                delay(8_000)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            DetailsHeader(item)

            Section("Detalhes".tr()) {
                Card {
                    detailRows(item).forEachIndexed { i, (label, value) ->
                        if (i > 0) RowDivider()
                        InfoRow(label, value)
                    }
                }
            }

            if (onShare != null || liveShare != null) {
                Section("Partilhado".tr()) {
                    val share = liveShare
                    if (share != null) {
                        ShareCard(item, share, onShare?.let { { onDismiss(); it() } })
                    } else {
                        Card {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Text(
                                    "Não partilhado".tr(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                onShare?.let { share ->
                                    LinkButton(Icons.Outlined.Link, "Criar link".tr()) { onDismiss(); share() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsHeader(item: BrowseItem) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.height(96.dp), contentAlignment = Alignment.Center) {
            when (item) {
                is BrowseItem.Folder -> when {
                    // O mesmo ícone da listagem: emoji personalizado primeiro.
                    !item.emoji.isNullOrBlank() -> Text(item.emoji, fontSize = 64.sp)
                    else -> Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = parseHexColor(item.color) ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(96.dp),
                    )
                }
                is BrowseItem.File -> FileTypeIcon(item, Modifier.height(88.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            typeLabel(item).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun typeLabel(item: BrowseItem): String = when (item) {
    is BrowseItem.Folder -> if (item.isTeamFolder) "Pasta de equipa".tr() else "Pasta".tr()
    is BrowseItem.File -> fileExtension(item.name, item.mimetype).ifBlank { item.type }
}

private fun detailRows(item: BrowseItem): List<Pair<String, String>> = buildList {
    when (item) {
        is BrowseItem.File -> add("Tamanho".tr() to (item.filesize ?: "—"))
        is BrowseItem.Folder -> {
            add("Tamanho".tr() to (item.filesize ?: "—"))
            add("Itens".tr() to (item.itemCount?.toString() ?: "—"))
        }
    }
    add("Criado em".tr() to (item.createdAt ?: "—"))
    item.updatedAt?.takeIf { it != item.createdAt }?.let { add("Atualizado em".tr() to it) }
}

@Composable
private fun ShareCard(item: BrowseItem, share: ShareInfo, onEdit: (() -> Unit)?) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // Relógio próprio para o "expira em" não ficar parado com a folha aberta.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    // Itens cifrados (e pastas) precisam da chave no link, que só o diálogo de
    // partilha junta — copiar daqui daria um link que não abre.
    val canCopyHere = item is BrowseItem.File && !item.encrypted && !share.link.isNullOrBlank()

    Card {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    if (share.protected) Icons.Filled.Lock else Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    share.link ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (canCopyHere) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copiar".tr(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp)
                            .clickable {
                                clipboard.setText(AnnotatedString(share.link!!))
                                android.widget.Toast.makeText(context, "Link copiado".tr(), android.widget.Toast.LENGTH_SHORT).show()
                            },
                    )
                }
            }

            shareStats(item, share, now).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { stat -> StatTile(stat, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            onEdit?.let { LinkButton(Icons.Outlined.Edit, "Editar partilha".tr(), onClick = it) }
        }
    }
}

private data class Stat(val label: String, val value: String, val warn: Boolean = false)

private fun shareStats(item: BrowseItem, share: ShareInfo, now: Long): List<Stat> = buildList {
    if (item is BrowseItem.Folder) {
        add(Stat("Permissão".tr(), if (share.permission == "editor" || share.permission == "can-edit") "Pode editar".tr() else "Apenas ver".tr()))
    }
    add(Stat("Password", if (share.protected) "Protegido".tr() else "Sem proteção".tr()))

    val left = shareTimeLeft(share.expiresAt, now)
    add(Stat("Expira".tr(), left?.text ?: "Nunca".tr(), warn = left?.expired == true))

    share.viewLimit?.takeIf { it > 0 }?.let { limit ->
        val used = share.viewCount ?: 0
        add(Stat("Visualizações".tr(), "$used/$limit", warn = used >= limit))
    }
    share.downloadLimit?.takeIf { it > 0 }?.let { limit ->
        val used = share.downloadCount ?: 0
        add(Stat("Downloads", "$used/$limit", warn = used >= limit))
    }
}

@Composable
private fun StatTile(stat: Stat, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(stat.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stat.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (stat.warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) { content() }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun LinkButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

