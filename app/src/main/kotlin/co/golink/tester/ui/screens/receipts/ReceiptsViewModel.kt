package co.golink.tester.ui.screens.receipts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.receipts.Budget
import co.golink.tester.data.receipts.Budgets
import co.golink.tester.data.receipts.Member
import co.golink.tester.data.receipts.Receipt
import co.golink.tester.data.receipts.ReceiptPhotoException
import co.golink.tester.data.receipts.ReceiptsCache
import co.golink.tester.data.receipts.ReceiptsConflictException
import co.golink.tester.data.receipts.ReceiptsHttpException
import co.golink.tester.data.receipts.ReceiptsPrefs
import co.golink.tester.data.receipts.ReceiptsRepository
import co.golink.tester.data.receipts.ReceiptsState
import co.golink.tester.ui.common.UserDisplayPrefs
import co.golink.tester.ui.common.appLocale
import co.golink.tester.ui.i18n.rt
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Datas no idioma da app (= shortDate/longDate/monthName de format.js). */
object RcDates {
    fun short(d: LocalDate?): String = d?.let { DateTimeFormatter.ofPattern("d MMM", appLocale()).format(it).replace(".", "") } ?: "—"
    fun long(d: LocalDate?): String = d?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale()).format(it) } ?: "—"
    fun month(m0: Int, style: TextStyle = TextStyle.FULL_STANDALONE): String =
        java.time.Month.of(m0 + 1).getDisplayName(style, appLocale()).replaceFirstChar { it.titlecase(appLocale()) }
    fun monthShort(m0: Int): String = month(m0, TextStyle.SHORT_STANDALONE).replace(".", "")
    fun monthLetter(m0: Int): String = month(m0, TextStyle.NARROW_STANDALONE).uppercase(appLocale())
    fun weekday(d: LocalDate, style: TextStyle = TextStyle.SHORT_STANDALONE): String =
        d.dayOfWeek.getDisplayName(style, appLocale()).replace(".", "").replaceFirstChar { it.titlecase(appLocale()) }
    fun weekdayLetter(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW_STANDALONE, appLocale()).uppercase(appLocale())
    /** Cabeçalho do grupo do dia na lista: "Today", "Yesterday" ou "Mon, 9 Nov". */
    fun dayHeader(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> rt("today")
        else -> "${weekday(d)}, ${short(d)}"
    }

    /** 0 = domingo, 1 = segunda (como weekStart da web). */
    fun weekStart(): Int = if (UserDisplayPrefs.weekStart == DayOfWeek.SUNDAY) 0 else 1
}

/** Resultado de gravar um recibo no Scan/Editar. */
sealed interface RcSaveResult {
    data class Saved(val receipt: Receipt) : RcSaveResult
    /** Ficou gravado mas a foto falhou: gravar outra vez só atualiza este (sem duplicar). */
    data class PhotoFailed(val receipt: Receipt) : RcSaveResult
    data object Failed : RcSaveResult
}

@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ReceiptsRepository,
    private val cache: ReceiptsCache,
    val prefs: ReceiptsPrefs,
    private val lockManager: co.golink.tester.data.settings.AppLockManager,
) : ViewModel() {
    val state: StateFlow<ReceiptsState> = repo.state

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _hideAmounts = MutableStateFlow(prefs.hideAmountsOnLock)
    val hideAmounts: StateFlow<Boolean> = _hideAmounts.asStateFlow()

    init {
        // PDFs partilhados antes já foram lidos pela outra app.
        cache.clearShared()
    }

    fun toast(text: String) { _toast.value = text }
    fun consumeToast() { _toast.value = null }

    fun load() = viewModelScope.launch { repo.load() }
    fun retry() = viewModelScope.launch { repo.load(force = true) }
    suspend fun sync() = repo.sync()
    fun hasKey() = repo.hasKey

    /** Câmara / seletor / browser: voltar deles não pede o PIN da app. */
    fun expectExternal() = lockManager.expectExternalReturn()

    private fun messageOf(e: Throwable): String {
        val status = (e as? ReceiptsHttpException)?.status ?: ((e as? ReceiptPhotoException)?.cause as? ReceiptsHttpException)?.status
        return when {
            e is ReceiptsConflictException || status == 409 -> rt("conflict")
            status == 507 -> rt("scan.storage_full")
            status == 422 && e !is ReceiptPhotoException -> rt("scan.too_many")
            else -> rt("error")
        }
    }

    private fun fail(e: Throwable) {
        if (e is CancellationException) throw e
        toast(messageOf(e))
    }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { fail(e) }
    }

    // ---- Recibos -------------------------------------------------------------------

    suspend fun save(id: String?, payload: JsonObject, baseRevision: Int?, photo: ByteArray?): RcSaveResult {
        _busy.value = true
        return try {
            val saved = repo.save(id, payload, baseRevision, photo)
            toast(rt("scan.saved"))
            RcSaveResult.Saved(saved)
        } catch (e: ReceiptPhotoException) {
            val status = (e.cause as? ReceiptsHttpException)?.status
            toast(if (status == 507) rt("scan.storage_full") else rt("app.photo_retry"))
            RcSaveResult.PhotoFailed(e.saved)
        } catch (e: Exception) {
            fail(e)
            RcSaveResult.Failed
        } finally {
            _busy.value = false
        }
    }

    fun patch(id: String, vararg changes: Pair<String, JsonElement?>) = run { repo.patch(id, *changes) }

    fun trash(id: String, then: () -> Unit = {}) = run { repo.trash(id); toast(rt("moved_to_trash")); then() }
    fun restore(id: String) = run { repo.restore(id); toast(rt("restored")) }
    fun deleteForever(id: String, then: () -> Unit = {}) = run { repo.deleteForever(id); then() }
    fun emptyTrash() = run { repo.emptyTrash() }

    /** Foto decifrada reduzida para o ecrã (null sem foto ou se falhar). */
    suspend fun photoBitmap(id: String, maxSide: Int): Bitmap? = runCatching {
        val bytes = repo.photo(id)
        withContext(Dispatchers.Default) { ReceiptsImages.decodeSampled(bytes, maxSide) }
    }.getOrNull()

    suspend fun photoBytes(id: String, cache: Boolean = true): ByteArray? = runCatching { repo.photo(id, cache) }.getOrNull()

    // ---- Meta: orçamentos e agregado ---------------------------------------------------

    /** budget = null remove. Grava e volta a ver os alertas do mês (como a BudgetSheet da web). */
    suspend fun saveBudget(cat: String, budget: Budget?): Boolean = try {
        _busy.value = true
        repo.saveMeta { meta ->
            val b = meta.budgets.toMutableMap()
            if (budget == null) b.remove(cat) else b[cat] = budget
            meta.copy(budgets = b)
        }
        viewModelScope.launch { runCatching { repo.checkAlerts(Budgets.currentYm(), null) } }
        true
    } catch (e: Exception) {
        fail(e)
        false
    } finally {
        _busy.value = false
    }

    suspend fun saveHousehold(members: List<Member>): Boolean = try {
        repo.saveMeta { it.copy(household = members) }
        true
    } catch (e: Exception) {
        fail(e)
        false
    }

    fun toggleSetting(alertClose: Boolean? = null, alertOver: Boolean? = null, weekly: Boolean? = null) = run {
        repo.saveSettings(alertClose, alertOver, weekly)
    }

    fun setHideAmounts(on: Boolean) {
        prefs.hideAmountsOnLock = on
        _hideAmounts.value = on
    }

    // ---- Partilhar ---------------------------------------------------------------------

    /** Ficheiro em cache/share/ → folha de partilha do sistema (apagado na próxima vez). */
    fun shareFile(file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData: o chooser propaga a permissão de leitura ao destino (e à pré-visualização).
            clipData = android.content.ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        expectExternal()
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---- Exportar (F5): PDF IRS, CSV, ZIP das fotos — para um ficheiro escolhido (SAF) ----

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()

    fun hasPhotos(year: Int): Boolean = state.value.active.any { it.hasPhoto && !it.locked && it.date.startsWith(year.toString()) }

    fun export(kind: String, uri: android.net.Uri, year: Int) = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        try {
            val s = state.value
            val n = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    when (kind) {
                        "pdf" -> { ReceiptsPdf.irsSummary(out, s.items, year, s.meta.household); 1 }
                        "csv" -> ReceiptsPdf.csv(out, s.items, year, s.meta.household)
                        else -> ReceiptsPdf.photosZip(out, s.items, year, { id -> photoBytes(id, cache = false) }) { i, total ->
                            _progress.value = rt("efatura.decrypting", "i" to i, "n" to total)
                        }
                    }
                } ?: throw java.io.IOException("output")
            }
            if (kind == "zip" && n == 0) {
                runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
                toast(rt("efatura.no_photos"))
            } else toast(rt("app.export_done"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            toast(rt("error"))
        } finally {
            _progress.value = null
            _busy.value = false
        }
    }

    fun shareReceiptPdf(r: Receipt) = run {
        _busy.value = true
        try {
            val photo = if (r.hasPhoto && !r.locked) photoBytes(r.id) else null
            val file = withContext(Dispatchers.IO) {
                cache.clearShared()
                ReceiptsPdf.receipt(cache.shareDir, r, state.value.meta.household, photo)
            }
            shareFile(file, "application/pdf", rt("app.share_pdf"))
        } finally {
            _busy.value = false
        }
    }
}
