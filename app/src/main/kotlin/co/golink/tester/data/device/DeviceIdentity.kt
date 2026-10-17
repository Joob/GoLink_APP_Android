package co.golink.tester.data.device

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identidade desta instalação para os backups por dispositivo (cartões na web,
 * pausa por aparelho). O id é gerado uma vez e guardado nas prefs; como o
 * manifest tem allowBackup=false, uma reinstalação conta como aparelho novo.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val id: String by lazy {
        prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_ID, it).apply()
        }
    }

    /** "Samsung SM-S918B", "Google Pixel 7". */
    val name: String by lazy {
        val maker = Build.MANUFACTURER.orEmpty().trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        val model = Build.MODEL.orEmpty().trim()
        when {
            model.isEmpty() -> maker.ifEmpty { "Android" }
            maker.isEmpty() || model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
            // Cabeçalhos HTTP só aceitam ASCII visível.
            .filter { it in ' '..'~' }.trim().take(120).ifEmpty { "Android" }
    }

    private companion object {
        const val PREFS = "device_identity"
        const val KEY_ID = "install_id"
    }
}
