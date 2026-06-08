package co.golink.tester.ui.screens.landing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.R
import co.golink.tester.ui.theme.BrandGreen
import co.golink.tester.ui.theme.BrandGreenLight

@Composable
fun LandingScreen(
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Logo in a soft circular tile
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BrandGreenLight, Color(0xFFE5F7EF)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_symbol),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            Image(
                painter = painterResource(R.drawable.logo_wordmark),
                contentDescription = "GoLink",
                modifier = Modifier.height(26.dp),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Armazenamento\ndescentralizado e seguro.",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 32.sp,
                ),
                textAlign = TextAlign.Center,
                color = Color(0xFF1B2539),
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "A tua informação, sempre sob o teu controlo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1B2539).copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onRegister,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            ) {
                Text(
                    "Criar conta",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSignIn,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.5.dp, BrandGreen.copy(alpha = 0.4f)),
            ) {
                Text(
                    "Entrar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandGreen,
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                "GoLink.co  ·  Privacidade por defeito",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1B2539).copy(alpha = 0.35f),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
