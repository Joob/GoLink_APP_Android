package co.golink.tester.data.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enquanto a eliminação de conta decorre, o backend suspende a conta
 * (suspended_reason = account_deletion) e todos os pedidos exceto o polling do
 * progresso devolvem 403 account_suspended. Sem esta guarda, o
 * SessionGuardInterceptor limpava o token ao primeiro poll de background
 * (notificações, backup) e expulsava o utilizador do ecrã de progresso.
 */
@Singleton
class DeletionGuard @Inject constructor() {
    @Volatile
    var active: Boolean = false
}
