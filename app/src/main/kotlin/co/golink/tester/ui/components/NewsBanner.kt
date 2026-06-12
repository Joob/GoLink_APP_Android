package co.golink.tester.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import co.golink.tester.data.news.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

val NewsOrange = Color(0xFFF97316)

@HiltViewModel
class NewsBannerViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {
    val news = repository.news
    val dismissed = repository.dismissed
    fun dismiss() = repository.dismiss()
}

@Composable
fun NewsBanner(
    modifier: Modifier = Modifier,
    viewModel: NewsBannerViewModel = hiltViewModel(),
) {
    val news by viewModel.news.collectAsState()
    val dismissed by viewModel.dismissed.collectAsState()
    val current = news
    if (dismissed || current == null || !current.allowed || current.message.isBlank()) return

    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, NewsOrange, RoundedCornerShape(12.dp))
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        Text(
            current.message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = viewModel::dismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Fechar notícia",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
