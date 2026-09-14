package co.golink.tester.ui.screens.landing

import co.golink.tester.ui.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import co.golink.tester.ui.common.EncryptedVaultHero
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
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Seletor de idioma no topo, para trocar antes de entrar/registar.
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                co.golink.tester.ui.i18n.LanguageMenu()
            }
            Spacer(Modifier.weight(1f))

            // Encrypted vault presentation (privacy-first brand visual).
            EncryptedVaultHero()

            Spacer(Modifier.height(28.dp))

            Image(
                painter = painterResource(R.drawable.logo_wordmark),
                contentDescription = "GoLink",
                modifier = Modifier.height(30.dp),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "A tua informação está sempre sob o teu controlo.".tr(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 36.sp,
                    fontSize = 28.sp,
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Encriptação ponta-a-ponta em cada ficheiro. Só tu e quem escolheres têm a chave.".tr(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(18.dp))

            // E2E reassurance pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(BrandGreenLight.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Encriptado ponta-a-ponta".tr(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandGreen,
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onRegister,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            ) {
                Text(
                    "Criar conta".tr(),
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
                    "Entrar".tr(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandGreen,
                )
            }

            Spacer(Modifier.height(22.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "© ${java.time.Year.now().value} GoLink.co # Encrypted Cloud Storage",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "All Rights Reserved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
