package co.golink.tester.data.pass.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.slice.Slice
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveInfo
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import co.golink.tester.R
import co.golink.tester.data.pass.PassItem
import co.golink.tester.data.pass.PassState
import co.golink.tester.ui.i18n.pt
import co.golink.tester.ui.screens.pass.autofill.PassAutofillActivity
import java.util.concurrent.atomic.AtomicInteger

/** Constrói as respostas de preenchimento (sugestões, autenticação, gravação). */
@Suppress("DEPRECATION")
object PassAutofillResponses {
    const val MAX_SUGGESTIONS = 5
    private val requestCodes = AtomicInteger((System.currentTimeMillis() % 100_000).toInt())

    /** Lista de sugestões para um pedido (inline no teclado se o IME as pedir). */
    fun fill(context: Context, target: PassAutofillTarget, state: PassState, needsReprompt: Boolean, inline: InlineSuggestionsRequest?): FillResponse {
        // Só correspondências verificadas (domínio de browser conhecido ou pacote em login.apps).
        // A heurística pelo nome do pacote (com.evil.paypal ↔ paypal.com) nunca preenche.
        val hits = PassAutofillMatch.match(state.items, target.packageName, target.webDomain)
            .filter { it.strength != PassAutofillMatch.Strength.Heuristic }
            .take(MAX_SUGGESTIONS)
        val specs = inlineSpecs(inline)
        val b = FillResponse.Builder()
        hits.forEachIndexed { i, hit ->
            b.addDataset(dataset(context, target, hit.item, state.health?.badgeOf(hit.item.id), needsReprompt, specs.getOrNull(i)))
        }
        // "Outros logins…" — escolher qualquer login do cofre (e lembrar para esta app).
        val otherPres = presentation(context, "…", pt("af.other"), if (hits.isEmpty()) pt("af.no_match") else target.label, null)
        val other = Dataset.Builder(otherPres)
        val otherInline = specs.getOrNull(hits.size)?.let { inline(context, it, pt("af.other"), null) }
        target.allIds.forEach { id ->
            if (otherInline != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) other.setValue(id, null, otherPres, otherInline)
            else other.setValue(id, null, otherPres)
        }
        other.setAuthentication(authIntent(context, PassAutofillActivity.MODE_PICK, target))
        b.addDataset(other.build())
        saveInfo(target)?.let(b::setSaveInfo)
        return b.build()
    }

    /** Cofre trancado: um só "Desbloquear o Pass" que abre o ecrã de desbloqueio. */
    fun locked(context: Context, target: PassAutofillTarget, inline: InlineSuggestionsRequest?): FillResponse {
        val pres = presentation(context, "🔒", pt("af.unlock"), target.label, null)
        val b = FillResponse.Builder()
        val spec = inlineSpecs(inline).firstOrNull()
        if (spec != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.setAuthentication(target.allIds.toTypedArray(), authIntent(context, PassAutofillActivity.MODE_UNLOCK, target), pres, inline(context, spec, pt("af.unlock"), null))
        } else {
            b.setAuthentication(target.allIds.toTypedArray(), authIntent(context, PassAutofillActivity.MODE_UNLOCK, target), pres)
        }
        saveInfo(target)?.let(b::setSaveInfo)
        return b.build()
    }

    /** Um login → dataset. Com reprompt, os valores só seguem depois de confirmar. */
    fun dataset(context: Context, target: PassAutofillTarget, item: PassItem, badge: String?, needsReprompt: Boolean, spec: InlinePresentationSpec?): Dataset {
        val login = item.login
        val title = item.name.ifEmpty { login?.username.orEmpty().ifEmpty { "—" } }
        val sub = login?.username.orEmpty().ifEmpty { pt("af.from_pass") }
        val pres = presentation(context, avatarLetter(title), title, sub, badge?.let { pt("badge.$it") })
        val inl = spec?.let { inline(context, it, title, sub) }
        val b = Dataset.Builder(pres)
        fun put(id: AutofillId, value: String?) {
            val v = if (needsReprompt) null else AutofillValue.forText(value.orEmpty())
            if (inl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) b.setValue(id, v, pres, inl) else b.setValue(id, v, pres)
        }
        target.usernameIds.forEach { put(it, login?.username) }
        target.passwordIds.forEach { put(it, login?.password) }
        if (needsReprompt) b.setAuthentication(authIntent(context, PassAutofillActivity.MODE_CONFIRM, target, item.id))
        return b.build()
    }

    /** Dataset já com valores (resultado do ecrã de escolha/confirmação). */
    fun filled(context: Context, target: PassAutofillTarget, item: PassItem): Dataset =
        dataset(context, target, item, null, needsReprompt = false, spec = null)

    fun saveInfo(target: PassAutofillTarget): SaveInfo? {
        if (target.passwordIds.isEmpty()) return null
        var type = SaveInfo.SAVE_DATA_TYPE_PASSWORD
        if (target.usernameIds.isNotEmpty()) type = type or SaveInfo.SAVE_DATA_TYPE_USERNAME
        val b = SaveInfo.Builder(type, target.passwordIds.toTypedArray())
        if (target.usernameIds.isNotEmpty()) b.setOptionalIds(target.usernameIds.toTypedArray())
        // Muitas apps não fecham a atividade após o login: gravar quando os campos somem.
        b.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
        return b.build()
    }

    // ---- Intents ---------------------------------------------------------------

    @SuppressLint("UnspecifiedImmutableFlag")
    fun authIntent(context: Context, mode: String, target: PassAutofillTarget, itemId: String? = null): IntentSender {
        val intent = Intent(context, PassAutofillActivity::class.java)
            .putExtra(PassAutofillActivity.EXTRA_MODE, mode)
            .putExtra(PassAutofillActivity.EXTRA_PACKAGE, target.packageName)
            .putExtra(PassAutofillActivity.EXTRA_DOMAIN, target.webDomain)
            .putParcelableArrayListExtra(PassAutofillActivity.EXTRA_USER_IDS, ArrayList(target.usernameIds))
            .putParcelableArrayListExtra(PassAutofillActivity.EXTRA_PASS_IDS, ArrayList(target.passwordIds))
            .putExtra(PassAutofillActivity.EXTRA_ITEM, itemId)
        // O sistema junta EXTRA_ASSIST_STRUCTURE ao intent: tem de ser mutável (API 31+).
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getActivity(context, requestCodes.incrementAndGet(), intent, flags).intentSender
    }

    fun saveIntent(context: Context, target: PassAutofillTarget): Intent =
        Intent(context, PassAutofillActivity::class.java)
            .putExtra(PassAutofillActivity.EXTRA_MODE, PassAutofillActivity.MODE_SAVE)
            .putExtra(PassAutofillActivity.EXTRA_PACKAGE, target.packageName)
            .putExtra(PassAutofillActivity.EXTRA_DOMAIN, target.webDomain)
            .putExtra(PassAutofillActivity.EXTRA_USERNAME, target.usernameValue)
            .putExtra(PassAutofillActivity.EXTRA_PASSWORD, target.passwordValue)

    fun savePendingIntent(context: Context, target: PassAutofillTarget): IntentSender =
        PendingIntent.getActivity(
            context, requestCodes.incrementAndGet(), saveIntent(context, target),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ).intentSender

    // ---- Apresentação ----------------------------------------------------------

    private fun avatarLetter(s: String) = s.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"

    fun presentation(context: Context, letter: String, title: String, sub: String?, badge: String?): RemoteViews =
        RemoteViews(context.packageName, R.layout.pass_autofill_item).apply {
            setTextViewText(R.id.pass_af_avatar, letter)
            setTextViewText(R.id.pass_af_title, title)
            setTextViewText(R.id.pass_af_sub, sub.orEmpty())
            setViewVisibility(R.id.pass_af_sub, if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE)
            if (badge != null) {
                setTextViewText(R.id.pass_af_badge, badge)
                setViewVisibility(R.id.pass_af_badge, View.VISIBLE)
            }
        }

    private fun inlineSpecs(req: InlineSuggestionsRequest?): List<InlinePresentationSpec> {
        if (req == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val specs = req.inlinePresentationSpecs
        if (specs.isEmpty()) return emptyList()
        // O último spec vale para as sugestões seguintes.
        return (0 until req.maxSuggestionCount.coerceAtMost(MAX_SUGGESTIONS + 1)).map { specs[minOf(it, specs.size - 1)] }
            .filter { UiVersions.getVersions(it.style).contains(UiVersions.INLINE_UI_VERSION_1) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun inline(context: Context, spec: InlinePresentationSpec, title: String, sub: String?): InlinePresentation? = runCatching {
        // Toque longo no chip abre a app (atribuição exigida pelo sistema).
        val attribution = PendingIntent.getActivity(
            context, 0, Intent(context, PassAutofillActivity::class.java).putExtra(PassAutofillActivity.EXTRA_MODE, PassAutofillActivity.MODE_NONE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val content = InlineSuggestionUi.newContentBuilder(attribution)
            .setTitle(title)
            .setStartIcon(Icon.createWithResource(context, R.drawable.ic_pass_key))
            .setContentDescription(title)
        if (!sub.isNullOrEmpty()) content.setSubtitle(sub)
        val slice: Slice = content.build().slice
        InlinePresentation(slice, spec, false)
    }.getOrNull()
}
