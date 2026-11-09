package co.golink.tester.network.interceptors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Corpos reais do servidor (Handler::unauthenticated, EnsureOtpVerified, SuspendUser). */
class SessionGuardRulesTest {
    private fun ends(code: Int, body: String) = SessionGuardInterceptor.endsSession(code, body)

    @Test
    fun revokedOrSuspendedEndsSession() {
        assertTrue(ends(401, """{"message":"Unauthenticated.","session_revoked":true,"reason":"password_changed"}"""))
        assertTrue(ends(403, """{"type":"error","reason":"account_suspended","message":"Suspensa","suspended_until":null}"""))
    }

    @Test
    fun genericLaravel401EndsSessionOnOldServers() {
        assertTrue(ends(401, """{"message":"Unauthenticated."}"""))
    }

    @Test
    fun otherAuthErrorsKeepSession() {
        // OTP por validar: o utilizador continua no ecrã OTP.
        assertFalse(ends(401, """{"message":"Unauthenticated.","otp_required":true}"""))
        assertFalse(ends(403, """{"message":"OTP verification required.","otp_required":true}"""))
        // 401 próprios (ZIP/downloads em dunning) e 403 de permissões/WAF.
        assertFalse(ends(401, """{"type":"error","message":"Your subscription is past due."}"""))
        assertFalse(ends(403, """{"message":"This action is unauthorized."}"""))
        assertFalse(ends(403, "<html>Cloudflare</html>"))
        // Só 401/403 contam.
        assertFalse(ends(500, """{"session_revoked":true}"""))
    }

    @Test
    fun loginPathsAreExcluded() {
        assertTrue(SessionGuardInterceptor.isLoginPath("/api/login"))
        assertTrue(SessionGuardInterceptor.isLoginPath("/api/user/validate-otp-code"))
        assertTrue(SessionGuardInterceptor.isLoginPath("/api/passkeys/login/options"))
        assertTrue(SessionGuardInterceptor.isLoginPath("/api/socialite/exchange"))
        assertFalse(SessionGuardInterceptor.isLoginPath("/api/user"))
        assertFalse(SessionGuardInterceptor.isLoginPath("/api/browse/folders/x"))
    }
}
