package co.golink.tester.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fonte única do FLAG_SECURE da MainActivity: ecrãs que pedem ecrã seguro
 * (ex.: Pass com "Bloquear capturas") registam-se aqui e a atividade decide.
 * Contador (e não booleano) para sobreviver a transições em que o ecrã novo
 * entra antes de o antigo sair.
 */
object SecureScreens {
    val active = MutableStateFlow(0)

    fun acquire() = active.update { it + 1 }
    fun release() = active.update { (it - 1).coerceAtLeast(0) }
}
