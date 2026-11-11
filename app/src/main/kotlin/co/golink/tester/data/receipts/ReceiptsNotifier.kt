package co.golink.tester.data.receipts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.golink.tester.MainActivity
import co.golink.tester.R
import co.golink.tester.ui.i18n.rt
import co.golink.tester.ui.i18n.rtc
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notificações locais dos Recibos (com valores, decifrados no aparelho):
 * alertas de orçamento (o servidor só põe na Inbox um aviso sem valores) e o resumo
 * semanal. Com "Hide amounts on lock screen" o ecrã bloqueado só mostra a versão pública.
 */
@Singleton
class ReceiptsNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ReceiptsRepository,
    private val prefs: ReceiptsPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { repo.alerts.collect { list -> list.forEach(::notifyAlert) } }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WEEKLY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReceiptsWeeklyWorker>(3, TimeUnit.HOURS).build(),
        )
    }

    private fun open(pane: String, code: Int): PendingIntent = PendingIntent.getActivity(
        context,
        code,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReceiptsDeepLink.EXTRA, pane),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannels() {
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        if (m.getNotificationChannel(CH_ALERTS) == null) {
            m.createNotificationChannel(NotificationChannel(CH_ALERTS, rt("app.n_channel"), NotificationManager.IMPORTANCE_HIGH))
        }
        if (m.getNotificationChannel(CH_WEEKLY) == null) {
            m.createNotificationChannel(NotificationChannel(CH_WEEKLY, rt("app.n_week_channel"), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun catName(cat: String) = rt("cat.$cat")

    fun notifyAlert(a: BudgetAlert) {
        ensureChannels()
        val whole = { c: Long -> RcFormat.money(c, whole = true) }
        val title = rt("app.n_title", "cat" to catName(a.cat), "spent" to whole(a.spent), "budget" to whole(a.budget))
        val over = a.spent - a.budget
        val body = when {
            a.kind == "over" && a.merchant != null && a.total != null ->
                rt("app.n_added_over", "merchant" to a.merchant, "amount" to RcFormat.money(a.total), "over" to whole(over))
            a.kind == "over" -> rt("app.n_over", "over" to whole(over))
            a.merchant != null && a.total != null ->
                rt("app.n_added_left", "merchant" to a.merchant, "amount" to RcFormat.money(a.total), "left" to whole(a.budget - a.spent), "n" to a.daysLeft)
            else -> rt("app.n_left", "left" to whole(a.budget - a.spent), "n" to a.daysLeft)
        }
        val code = "rcpt-${a.cat}".hashCode()
        val publicVersion = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_receipt)
            .setContentTitle(rt("app.n_public_title"))
            .setContentText(rt("app.n_public_text"))
            .build()
        val pct = if (a.budget > 0) (a.spent.toDouble() / a.budget * 100).roundToInt().coerceIn(0, 100) else 100
        val b = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_receipt)
            .setColor(0xFF0D9488.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setProgress(100, pct, false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(open("budget/${a.cat}", code))
            .addAction(0, rt("app.n_view_budget"), open("budget/${a.cat}", code + 1))
        if (a.receiptId != null) {
            // "Not {cat}": abre o recibo com a categoria em destaque (não muda às cegas).
            b.addAction(0, rt("app.n_not_cat", "cat" to rt("cat_short.${a.cat}")), open("recat/${a.receiptId}", code + 2))
        }
        if (prefs.hideAmountsOnLock) b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
        else b.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        runCatching { NotificationManagerCompat.from(context).notify("rcpt-alert-${a.cat}", 0, b.build()) }
    }

    fun notifyWeekly(spent: Long, irs: Long, noNif: Int, over: Int, weekStart: LocalDate) {
        ensureChannels()
        val title = rt("app.n_week_title", "amount" to RcFormat.money(spent, whole = true))
        val lines = buildList {
            if (irs > 0) add(rt("app.n_week_irs", "amount" to RcFormat.money(irs, whole = true)))
            if (noNif > 0) add(rtc("app.n_week_no_nif", noNif, "n" to noNif))
            add(if (over > 0) rtc("app.n_week_over", over, "n" to over) else rt("app.n_week_ok"))
        }
        val body = lines.joinToString(" ")
        val pane = "spending/week/${RcFormat.ymd(weekStart)}"
        val publicVersion = NotificationCompat.Builder(context, CH_WEEKLY)
            .setSmallIcon(R.drawable.ic_stat_receipt)
            .setContentTitle(rt("app.n_week_public"))
            .build()
        val b = NotificationCompat.Builder(context, CH_WEEKLY)
            .setSmallIcon(R.drawable.ic_stat_receipt)
            .setColor(0xFF0D9488.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open(pane, WEEKLY_CODE))
            .addAction(0, rt("app.n_open_summary"), open(pane, WEEKLY_CODE + 1))
        if (prefs.hideAmountsOnLock) b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
        else b.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        runCatching { NotificationManagerCompat.from(context).notify("rcpt-weekly", 0, b.build()) }
    }

    companion object {
        const val CH_ALERTS = "receipts_alerts"
        const val CH_WEEKLY = "receipts_weekly"
        private const val WEEKLY_WORK = "receipts-weekly"
        private const val WEEKLY_CODE = 0x52435057
    }
}

/**
 * Resumo semanal local: domingo a partir das 19h (ou segunda de manhã, se falhou).
 * Só com "Weekly summary" ligado; abre com a chave do aparelho e a cache cifrada.
 */
@HiltWorker
class ReceiptsWeeklyWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: ReceiptsRepository,
    private val prefs: ReceiptsPrefs,
    private val notifier: ReceiptsNotifier,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        run()
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Throwable) {
        Result.success()
    }

    private suspend fun run() {
        if (!prefs.weeklyEnabled) return
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        // Semana que acabou (âncora = domingo).
        val sunday = when {
            today.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 19 -> today
            today.dayOfWeek == DayOfWeek.MONDAY && now.hour < 12 -> today.minusDays(1)
            else -> return
        }
        val key = sunday.toString()
        if (prefs.weeklyShownFor == key) return
        if (!repo.openForBackground()) return
        val s = repo.state.value
        if (!s.settings.weekly) return
        val weekStart = if (co.golink.tester.ui.common.UserDisplayPrefs.weekStart == DayOfWeek.SUNDAY) 0 else 1
        val sum = Aggregate.spendingSummary(s.items, "week", sunday, s.meta.household, s.meta.budgets, weekStart, today = today, now = now)
        val noNif = sum.list.count { IrsRules.efStatus(it, s.meta.household, now) == "no_nif" }
        val over = Budgets.summary(s.active, s.meta.budgets, Budgets.currentYm(sunday), sunday).cards.count { it.status == "over" }
        prefs.weeklyShownFor = key
        if (sum.count == 0) return
        notifier.notifyWeekly(sum.spent, sum.irs.spent, noNif, over, sum.range.start)
    }
}
