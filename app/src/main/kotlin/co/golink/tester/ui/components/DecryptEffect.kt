package co.golink.tester.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Efeito visual de "algoritmo a cifrar/decifrar": linha de hex a baralhar que
 * se resolve da esquerda para a direita, em loop — igual à Web (E2EDecryptEffect).
 * Puro visual; usado no gate E2E e no banner de uploads.
 */
@Composable
fun DecryptEffect(label: String, modifier: Modifier = Modifier) {
    val pool = "ABCDEF0123456789"
    fun rand(n: Int) = buildString { repeat(n) { append(pool.random()) } }
    // Agrupa 4-em-4 no fim: comprimento e alinhamento dos blocos são sempre
    // iguais, senão a linha centrada "salta" a cada tick.
    fun format(s: String) = s.chunked(4).joinToString(" ")
    var line1 by remember { mutableStateOf(format(rand(20))) }
    var line2 by remember { mutableStateOf(format(rand(20))) }
    var fixed by remember { mutableStateOf(rand(20)) }
    var step by remember { mutableIntStateOf(0) }
    var dots by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            step = (step + 1) % 28
            if (step == 0) fixed = rand(20)
            val solved = minOf(step, 20)
            line1 = format(fixed.take(solved) + rand(20 - solved))
            line2 = format(rand(20))
            if (step % 6 == 0) dots = if (dots.length >= 3) "" else "$dots."
            delay(65)
        }
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(line1, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        Text(line2, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Spacer(Modifier.height(6.dp))
        Text(label + dots, style = MaterialTheme.typography.bodyMedium)
    }
}
