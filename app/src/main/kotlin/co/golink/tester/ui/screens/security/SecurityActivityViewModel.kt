package co.golink.tester.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SecurityActivityUiState(
    val isLoading: Boolean = false,
    val items: List<SecurityItemUi> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val toast: String? = null,
)

/**
 * Ecrã próprio da Atividade de segurança (aberto do Bloqueio da app). Usa a mesma
 * fonte paginada do painel das Definições — antes tinha API/DTO próprios e só
 * mostrava a 1.ª página.
 */
@HiltViewModel
class SecurityActivityViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SecurityActivityUiState())
    val state: StateFlow<SecurityActivityUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = load(page = 1)

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || !s.hasMore) return
        load(page = s.page + 1)
    }

    private fun load(page: Int) {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repository.securityEvents(page)
                .onSuccess { r ->
                    val mapped = r.items.map { e ->
                        SecurityItemUi(e.id, e.attributes.event, e.attributes.created_at, e.attributes.ip, e.attributes.user_agent)
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = if (page == 1) mapped else it.items + mapped,
                            page = page,
                            hasMore = r.hasMore,
                        )
                    }
                }
                .onFailure { t ->
                    // netCatching já traduz falhas de rede; o resto também chega traduzido.
                    _state.update { it.copy(isLoading = false, toast = t.message ?: "Não foi possível carregar a atividade de segurança.") }
                }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}
