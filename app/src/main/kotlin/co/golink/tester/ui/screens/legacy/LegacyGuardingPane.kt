package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.data.legacy.LegacyCombineResult
import co.golink.tester.data.legacy.LegacyCombinedRecipient
import co.golink.tester.data.legacy.LegacyGuardingRepository
import co.golink.tester.domain.legacy.LegacyGuardingRow
import co.golink.tester.ui.i18n.lt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val ABLE_TO_RELEASE = setOf("released", "opened")

private fun releasable(row: LegacyGuardingRow) = row.status == "accepted" && row.sealed && row.vault_status in ABLE_TO_RELEASE

data class LegacyGuardingState(
    val loaded: Boolean = false,
    val rows: List<LegacyGuardingRow> = emptyList(),
    val busy: Boolean = false,
    val results: Map<String, LegacyCombineResult> = emptyMap(),
)

@HiltViewModel
class LegacyGuardingViewModel @Inject constructor(
    private val repo: LegacyGuardingRepository,
    private val keys: E2EKeyManager,
) : ViewModel() {
    private val _state = MutableStateFlow(LegacyGuardingState())
    val state: StateFlow<LegacyGuardingState> = _state.asStateFlow()

    val ownFingerprint: String get() = keys.fingerprint().orEmpty()

    /** Mostrado ao ecrã que chamou (Guarding.vue só tem o erro genérico). */
    var onToast: (LegacyToast) -> Unit = {}

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        runCatching { block() }.onFailure {
            android.util.Log.w("Legacy", "guardião", it)
            onToast(LegacyToast("error", true))
        }
        _state.update { it.copy(busy = false) }
    }

    fun load() = run {
        val rows = repo.load()
        _state.update { it.copy(rows = rows, loaded = true) }
    }

    fun act(row: LegacyGuardingRow, action: String) = run {
        repo.act(row.id, action)
        _state.update { it.copy(rows = repo.load()) }
    }

    fun combine(row: LegacyGuardingRow) = run {
        val result = repo.combine(row.id)
        _state.update { it.copy(results = it.results + (row.id to result)) }
    }

    fun resend(row: LegacyGuardingRow, person: LegacyCombinedRecipient) = run {
        repo.resendLink(row.id, person.id)
        onToast(LegacyToast("link_sent"))
    }
}

/** Guarding.vue: os cofres em que sou guardião. */
@Composable
fun LegacyGuardingPane(onToast: (LegacyToast) -> Unit, vm: LegacyGuardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    vm.onToast = onToast
    LaunchedEffect(Unit) { vm.load() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegacyHeader(lt("guarding"), lt("guarding_text"))
        val print = vm.ownFingerprint
        if (print.isNotEmpty() && state.rows.isNotEmpty()) {
            LegacyCard {
                Text(lt("guarding_fingerprint"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(print, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (state.loaded && state.rows.isEmpty()) {
            LegacyCard { Text(lt("no_guarding"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        state.rows.forEach { row ->
            val open = releasable(row)
            LegacyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.owner_name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        Text(lt("guarding_rule", "count" to row.threshold, "total" to row.count), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        row.status == "invited" -> LegacyChip(lt("guardian_invited"), ChipTone.Warn)
                        open -> LegacyChip(lt("guarding_released"), ChipTone.Danger)
                        else -> LegacyChip(lt("guarding_standing_by"), ChipTone.Ok)
                    }
                }
                when {
                    row.status == "invited" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        Button(onClick = { vm.act(row, "accept") }, enabled = !state.busy, shape = RoundedCornerShape(12.dp)) { Text(lt("accept")) }
                        OutlinedButton(onClick = { vm.act(row, "decline") }, enabled = !state.busy, shape = RoundedCornerShape(12.dp)) { Text(lt("decline")) }
                    }
                    open -> {
                        Text(lt("combine_text", "name" to row.owner_name), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
                        val result = state.results[row.id]
                        if (result == null) {
                            Button(onClick = { vm.combine(row) }, enabled = !state.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 10.dp)) { Text(lt("combine")) }
                        } else {
                            if (result.have < result.threshold) {
                                Text(lt("combine_waiting", "have" to result.have, "count" to result.threshold), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                                OutlinedButton(onClick = { vm.combine(row) }, enabled = !state.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp)) { Text(lt("check_again")) }
                            }
                            if (result.recipients.isNotEmpty()) {
                                Text(lt("codes_text"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    result.recipients.forEach { person ->
                                        CodeRow(
                                            person,
                                            busy = state.busy,
                                            onCopy = { code ->
                                                clipboard.setText(AnnotatedString(code))
                                                onToast(LegacyToast("copied"))
                                            },
                                            onResend = { vm.resend(row, person) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> Text(
                        lt(if (row.sealed) "guarding_idle" else "guarding_wait_owner"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeRow(person: LegacyCombinedRecipient, busy: Boolean, onCopy: (String) -> Unit, onResend: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(personName(person.meta, person.email), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        Text(person.email, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            if (person.code != null) {
                Text(person.code, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = { onCopy(person.code) }) { Text(lt("copy"), fontSize = 12.sp) }
            } else {
                Text(lt("code_unreadable"), fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onResend, enabled = !busy) { Text(lt("resend"), fontSize = 12.sp) }
        }
    }
}
