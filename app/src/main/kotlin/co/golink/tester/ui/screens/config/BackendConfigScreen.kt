package co.golink.tester.ui.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun BackendConfigScreen(
    onSaved: () -> Unit,
    viewModel: BackendConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "VueFileManager",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Configurar servidor",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Indica o URL da tua instância VueFileManager",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChange,
                    label = { Text("URL do servidor") },
                    placeholder = { Text("https://tester.golink.co") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                StatusRow(state)
                Spacer(Modifier.height(24.dp))

                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = state.status !is ConnectionStatus.Testing,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Testar ligação")
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.save(onSaved) },
                    enabled = state.url.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Guardar e continuar")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(state: BackendConfigUiState) {
    when (val status = state.status) {
        ConnectionStatus.Idle -> Spacer(Modifier.height(20.dp))
        ConnectionStatus.Testing -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ConnectionStatus.Ok -> StatusLine(
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            text = "Ligação OK",
            color = MaterialTheme.colorScheme.primary,
        )
        ConnectionStatus.Failed -> StatusLine(
            icon = { Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error) },
            text = "Falha: ${state.errorMessage ?: "desconhecida"}",
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun StatusLine(
    icon: @Composable () -> Unit,
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            Box { icon() }
            Spacer(Modifier.height(4.dp))
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
