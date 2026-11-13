package co.golink.tester.data.pass

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Preferências só deste aparelho (não vão para o servidor). */
@Singleton
class PassPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("pass_prefs", Context.MODE_PRIVATE)

    /** FLAG_SECURE enquanto o Pass está visível (também o esconde nos recentes). Ligado por defeito. */
    var blockScreens: Boolean
        get() = prefs.getBoolean("block_screens", true)
        set(v) = prefs.edit().putBoolean("block_screens", v).apply()

    var genMode: String
        get() = prefs.getString("gen_mode", "random") ?: "random"
        set(v) = prefs.edit().putString("gen_mode", v).apply()

    var genRandom: PassGenerator.RandomOpts
        get() = PassGenerator.RandomOpts(
            length = prefs.getInt("gen_len", 18), upper = prefs.getBoolean("gen_upper", true), digits = prefs.getBoolean("gen_digits", true),
            symbols = prefs.getBoolean("gen_symbols", true), avoidLookalike = prefs.getBoolean("gen_lookalike", true),
        )
        set(o) = prefs.edit().putInt("gen_len", o.length).putBoolean("gen_upper", o.upper).putBoolean("gen_digits", o.digits)
            .putBoolean("gen_symbols", o.symbols).putBoolean("gen_lookalike", o.avoidLookalike).apply()

    var genPhrase: PassGenerator.PassphraseOpts
        get() = PassGenerator.PassphraseOpts(
            words = prefs.getInt("gen_words", 5), separator = prefs.getString("gen_sep", "-") ?: "-",
            capitalize = prefs.getBoolean("gen_cap", true), number = prefs.getBoolean("gen_num", true),
        )
        set(o) = prefs.edit().putInt("gen_words", o.words).putString("gen_sep", o.separator).putBoolean("gen_cap", o.capitalize).putBoolean("gen_num", o.number).apply()

    var genPin: PassGenerator.PinOpts
        get() = PassGenerator.PinOpts(prefs.getInt("gen_pin", 6))
        set(o) = prefs.edit().putInt("gen_pin", o.length).apply()

    // ---- Estado de bloqueio (sobrevive à morte do processo; a web guarda pass_locked) ----

    /** Trancado à mão ou por inatividade: ao arrancar a frio continua trancado. */
    var vaultLocked: Boolean
        get() = prefs.getBoolean("vault_locked", false)
        set(v) { prefs.edit().putBoolean("vault_locked", v).commit() }

    /** Última atividade no Pass (para aplicar o lock_after também ao arranque a frio). */
    var lastActiveAt: Long
        get() = prefs.getLong("last_active_at", 0L)
        set(v) = prefs.edit().putLong("last_active_at", v).apply()

    /** Cópia local do lock_after do cofre (minutos; 0 = nunca). */
    var lockAfterMinutes: Int
        get() = prefs.getInt("lock_after", 5)
        set(v) = prefs.edit().putInt("lock_after", v).apply()

    /** Arrancar trancado: lock persistido ou lock_after já passado (ou relógio andou para trás). */
    fun startsLocked(now: Long = System.currentTimeMillis()): Boolean {
        if (vaultLocked) return true
        val last = lastActiveAt
        val minutes = lockAfterMinutes
        if (last <= 0L || minutes <= 0) return false
        return now < last || now - last > minutes * 60_000L
    }

    fun clearLockState() {
        prefs.edit().remove("vault_locked").remove("last_active_at").remove("lock_after").commit()
    }
}

/**
 * Copiar segredos: marca o clip como sensível (Android 13+ não o mostra na pré-visualização)
 * e limpa-o ao fim de `clipboard_clear` segundos se ainda for o último que copiámos.
 * Rede de segurança: um OneTimeWork faz a mesma limpeza se o processo morrer entretanto.
 * Cada clip leva um marcador nos extras: só se apaga se o clip atual for esse.
 */
@Singleton
class PassClipboard @Inject constructor(@ApplicationContext private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var seq = 0

    fun copy(label: String, text: String, sensitive: Boolean, clearAfterSeconds: Int): Boolean {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = ClipData.newPlainText(label, text)
        val token = UUID.randomUUID().toString()
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                else putBoolean("android.content.extra.IS_SENSITIVE", true)
                putString(CLIP_MARKER, token)
            }
        }
        return runCatching {
            cm.setPrimaryClip(clip)
            val mine = ++seq
            if (sensitive && clearAfterSeconds > 0) {
                handler.postDelayed({
                    if (mine == seq) {
                        clearIfOurs(context, token)
                        // Já limpo: a rede de segurança não pode correr depois e apagar um clip mais novo de outra app.
                        runCatching { WorkManager.getInstance(context).cancelUniqueWork(PassClipClearWorker.WORK) }
                    }
                }, clearAfterSeconds * 1000L)
                // Processo morto antes do prazo: o WorkManager limpa na mesma (REPLACE = só o último clip conta).
                runCatching {
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        PassClipClearWorker.WORK,
                        ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<PassClipClearWorker>()
                            .setInitialDelay(clearAfterSeconds + 5L, TimeUnit.SECONDS)
                            .setInputData(workDataOf(PassClipClearWorker.TOKEN to token))
                            .build(),
                    )
                }
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        const val CLIP_MARKER = "co.golink.tester.pass_clip"

        /**
         * Limpa o clipboard se o clip atual for o nosso [token]. Se o sistema não deixar ler o
         * clip (Android 10+ em segundo plano), limpa na mesma: a password não pode ficar lá.
         */
        fun clearIfOurs(context: Context, token: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val desc = runCatching { cm.primaryClipDescription }.getOrNull()
            if (desc != null && desc.extras?.getString(CLIP_MARKER) != token) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { cm.clearPrimaryClip() }
            else runCatching { cm.setPrimaryClip(ClipData.newPlainText("", "")) }
        }
    }
}

/** Limpeza do clipboard que sobrevive à morte do processo (ver [PassClipboard]). */
@HiltWorker
class PassClipClearWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : Worker(appContext, params) {
    companion object {
        const val WORK = "pass_clip_clear"
        const val TOKEN = "token"
    }

    override fun doWork(): Result {
        val token = inputData.getString(TOKEN) ?: return Result.success()
        // ClipboardManager tem de ser usado numa thread com Looper.
        Handler(Looper.getMainLooper()).post { PassClipboard.clearIfOurs(applicationContext, token) }
        return Result.success()
    }
}
