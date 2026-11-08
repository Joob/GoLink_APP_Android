package co.golink.tester.data.pass

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
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
}

/**
 * Copiar segredos: marca o clip como sensível (Android 13+ não o mostra na pré-visualização)
 * e limpa-o ao fim de `clipboard_clear` segundos se ainda for o último que copiámos.
 */
@Singleton
class PassClipboard @Inject constructor(@ApplicationContext private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var seq = 0

    fun copy(label: String, text: String, sensitive: Boolean, clearAfterSeconds: Int): Boolean {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                else putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        return runCatching {
            cm.setPrimaryClip(clip)
            val mine = ++seq
            if (sensitive && clearAfterSeconds > 0) {
                handler.postDelayed({
                    if (mine == seq) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { cm.clearPrimaryClip() }
                        else runCatching { cm.setPrimaryClip(ClipData.newPlainText("", "")) }
                    }
                }, clearAfterSeconds * 1000L)
            }
            true
        }.getOrDefault(false)
    }
}
