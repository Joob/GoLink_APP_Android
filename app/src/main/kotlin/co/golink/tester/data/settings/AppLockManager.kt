package co.golink.tester.data.settings

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppLockManager @Inject constructor(
    private val securityPrefs: AppSecurityPreferences,
) {
    // Cold start: lock immediately if PIN or biometric is enabled.
    private val _locked = MutableStateFlow(securityPrefs.pinEnabled || securityPrefs.biometricEnabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt = 0L

    fun onBackground() {
        if (securityPrefs.biometricEnabled || securityPrefs.pinEnabled) {
            backgroundedAt = SystemClock.elapsedRealtime()
        }
    }

    fun onForeground() {
        if (backgroundedAt == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - backgroundedAt
        if ((securityPrefs.biometricEnabled || securityPrefs.pinEnabled) && elapsed >= 30_000L) {
            _locked.value = true
        }
        backgroundedAt = 0L
    }

    fun lock() { _locked.value = true }
    fun unlock() { _locked.value = false }
}
