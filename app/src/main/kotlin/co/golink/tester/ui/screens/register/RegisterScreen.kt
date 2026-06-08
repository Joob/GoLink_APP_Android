package co.golink.tester.ui.screens.register

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.ui.common.AuthScaffold

@Composable
fun RegisterScreen(
    onRegistered: (requiresVerification: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) {
        if (state.success) onRegistered(state.requiresVerification)
    }

    AuthScaffold(title = "Criar conta", subtitle = "Junta-te ao GoLink", onBack = onBack) {
        Field(
            label = "Nome",
            placeholder = "O teu nome",
            value = state.name,
            onChange = viewModel::onName,
            error = state.fieldErrors["name"],
            icon = Icons.Outlined.Person,
            cap = KeyboardCapitalization.Words,
        )
        Spacer(Modifier.height(14.dp))
        Field(
            label = "Email",
            placeholder = "o.teu.email@exemplo.com",
            value = state.email,
            onChange = viewModel::onEmail,
            error = state.fieldErrors["email"],
            icon = Icons.Outlined.Email,
            type = KeyboardType.Email,
        )
        Spacer(Modifier.height(14.dp))
        Field(
            label = "Password",
            placeholder = "Mínimo 6 caracteres",
            value = state.password,
            onChange = viewModel::onPassword,
            error = state.fieldErrors["password"],
            icon = Icons.Outlined.Lock,
            type = KeyboardType.Password,
            password = true,
        )
        Spacer(Modifier.height(14.dp))
        Field(
            label = "Confirmar password",
            placeholder = "Repete a password",
            value = state.passwordConfirmation,
            onChange = viewModel::onPasswordConfirmation,
            error = state.fieldErrors["password_confirmation"],
            icon = Icons.Outlined.Lock,
            type = KeyboardType.Password,
            password = true,
        )
        Spacer(Modifier.height(14.dp))
        Field(
            label = "Código de convite (opcional)",
            placeholder = "Código de convite",
            value = state.invitationToken,
            onChange = viewModel::onInvitationToken,
            error = state.fieldErrors["invitation_token"],
            icon = Icons.Outlined.VpnKey,
        )

        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = viewModel::submit,
            enabled = !state.isLoading,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Criar conta", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Field(
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    icon: ImageVector,
    type: KeyboardType = KeyboardType.Text,
    cap: KeyboardCapitalization = KeyboardCapitalization.None,
    password: Boolean = false,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        leadingIcon = { Icon(icon, contentDescription = null) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, capitalization = cap),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
