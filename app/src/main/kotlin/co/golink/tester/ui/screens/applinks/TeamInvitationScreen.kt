package co.golink.tester.ui.screens.applinks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.teams.TeamsRepository
import co.golink.tester.domain.teams.TeamInvitationData
import co.golink.tester.ui.common.AuthScaffold
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamInvitationUiState(
    val loading: Boolean = true,
    val invitation: TeamInvitationData? = null,
    /** 410: já usado/expirado. */
    val gone: Boolean = false,
    /** 403: aberto noutra conta. */
    val wrongAccount: Boolean = false,
    val working: Boolean = false,
    val error: String? = null,
    /** accepted | declined | awaiting (sem conta: à espera do registo). */
    val outcome: String? = null,
)

@HiltViewModel
class TeamInvitationViewModel @Inject constructor(
    private val teams: TeamsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamInvitationUiState())
    val state: StateFlow<TeamInvitationUiState> = _state
    private var loadedId: String? = null

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        _state.value = TeamInvitationUiState()
        viewModelScope.launch {
            teams.invitationPreview(id)
                .onSuccess { inv -> _state.update { it.copy(loading = false, invitation = inv) } }
                .onFailure { t -> _state.update { it.copy(loading = false, gone = t.code() == 410 || t.code() == 404, error = errorText(t)) } }
        }
    }

    fun accept() = act("accepted") { teams.acceptInvitation(it) }
    fun decline() = act("declined") { teams.declineInvitation(it) }
    fun awaitRegistration() = act("awaiting") { teams.awaitInvitationRegistration(it) }

    private fun act(outcome: String, call: suspend (String) -> Result<Unit>) {
        val id = loadedId ?: return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            call(id)
                .onSuccess { _state.update { it.copy(working = false, outcome = outcome) } }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            working = false,
                            wrongAccount = t.code() == 403,
                            gone = t.code() == 410 || t.code() == 422,
                            error = errorText(t),
                        )
                    }
                }
        }
    }

    private fun Throwable.code() = (this as? TeamsRepository.InvitationError)?.code
    private fun errorText(t: Throwable): String? = when (t.code()) {
        403, 404, 410, 422 -> null
        else -> "Não foi possível ligar ao servidor. Tenta outra vez.".tr()
    }
}

/**
 * Convite para uma pasta de equipa aberto a partir do email. Com sessão: aceitar
 * ou recusar. Sem sessão: entrar com a conta convidada ou, sem conta, criá-la
 * (o convite é redimido quando o email for confirmado).
 */
@Composable
fun TeamInvitationScreen(
    id: String,
    authenticated: Boolean,
    onOpenTeamFolder: (folderId: String) -> Unit,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onDone: () -> Unit,
    viewModel: TeamInvitationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.outcome) {
        when (state.outcome) {
            "accepted" -> state.invitation?.attributes?.parent_id?.let(onOpenTeamFolder) ?: onDone()
            "awaiting" -> onRegister()
        }
    }

    val inv = state.invitation
    val inviter = inv?.relationships?.inviter?.data?.attributes?.name?.takeIf { it.isNotBlank() }
    val subtitle = when {
        state.loading -> "A carregar o convite…".tr()
        state.outcome == "declined" -> "Recusaste o convite.".tr()
        state.gone -> "Este convite já foi usado ou expirou.".tr()
        state.wrongAccount -> "Este convite é para %s. Termina sessão e entra com essa conta para o aceitar.".tr().format(inv?.attributes?.email.orEmpty())
        inv == null -> state.error ?: "Não foi possível abrir o convite.".tr()
        inviter != null -> "%s convidou-te para uma pasta de equipa.".tr().format(inviter)
        else -> "Foste convidado para uma pasta de equipa.".tr()
    }

    AuthScaffold(title = "Convite de equipa".tr(), subtitle = subtitle, onBack = onDone) {
        Spacer(Modifier.height(8.dp))
        when {
            state.loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            inv == null || state.gone || state.wrongAccount || state.outcome == "declined" ->
                PrimaryButton("Continuar".tr(), onClick = onDone)
            authenticated -> {
                if (inv.attributes.permission == "can-view") {
                    Text(
                        "Vais poder ver os ficheiros desta pasta.".tr(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                PrimaryButton("Aceitar convite".tr(), loading = state.working, onClick = viewModel::accept)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Recusar".tr(), enabled = !state.working, onClick = viewModel::decline)
            }
            inv.attributes.isExistedUser -> {
                Text(
                    "Inicia sessão como %s para aceitar.".tr().format(inv.attributes.email),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Iniciar sessão".tr(), onClick = onSignIn)
            }
            else -> {
                Text(
                    "Cria uma conta com %s. O convite fica ativo assim que confirmares o email.".tr().format(inv.attributes.email),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Criar conta".tr(), loading = state.working, onClick = viewModel::awaitRegistration)
            }
        }
        state.error?.takeIf { inv != null }?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text) }
}
