package co.golink.tester.ui.screens.error

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BootstrapErrorViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {
    val state = sessionManager.state
    fun retry() = sessionManager.retryBootstrap()
    fun logout() = sessionManager.forceLogout()
}

@Composable
fun BootstrapErrorScreen(
    viewModel: BootstrapErrorViewModel = hiltViewModel(),
) {
    val authState by viewModel.state.collectAsState()
    val message = (authState as? AuthState.BootstrapFailed)?.message ?: "Erro desconhecido"

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Não foi possível carregar a tua conta",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = viewModel::retry,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Tentar de novo") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::logout,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Terminar sessão") }
            }
        }
    }
}
