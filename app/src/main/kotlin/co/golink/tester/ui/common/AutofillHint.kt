package co.golink.tester.ui.common

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

/**
 * Diz ao serviço de preenchimento (Pass da app, Google, outros gestores) o que
 * é este campo: email, password, nova password. Compose 1.7: a semântica
 * `contentType` ainda não chega ao sistema, por isso usa a árvore de autofill.
 */
enum class AutofillField { Email, Password, NewEmail, NewPassword }

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.autofillHint(field: AutofillField, onFill: (String) -> Unit): Modifier = composed {
    val fill by rememberUpdatedState(onFill)
    val node = remember(field) {
        val types = when (field) {
            AutofillField.Email -> listOf(AutofillType.EmailAddress, AutofillType.Username)
            AutofillField.Password -> listOf(AutofillType.Password)
            AutofillField.NewEmail -> listOf(AutofillType.EmailAddress, AutofillType.NewUsername)
            AutofillField.NewPassword -> listOf(AutofillType.NewPassword)
        }
        AutofillNode(autofillTypes = types, onFill = { fill(it) })
    }
    val autofill = LocalAutofill.current
    val tree = LocalAutofillTree.current
    DisposableEffect(tree, node) {
        tree += node
        onDispose { (tree.children as? MutableMap<Int, AutofillNode>)?.remove(node.id) }
    }
    this
        .onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
        .onFocusChanged { state ->
            if (node.boundingBox == null) return@onFocusChanged
            if (state.isFocused) autofill?.requestAutofillForNode(node) else autofill?.cancelAutofillForNode(node)
        }
}
