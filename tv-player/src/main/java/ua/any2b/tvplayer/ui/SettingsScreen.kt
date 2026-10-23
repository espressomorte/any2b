package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    autoPlayNext: Boolean,
    onAutoPlayChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E))
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Налаштування",
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = IbmPlexMono,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF15171E), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Відтворення",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = IbmPlexMono,
                    color = Color(0xFFD0BCFF)
                )

                var isFocused by remember { mutableStateOf(false) }

                Surface(
                    onClick = { onAutoPlayChanged(!autoPlayNext) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isFocused) Color(0xFF4F378B).copy(alpha = 0.5f) else Color(0xFF20232D),
                        focusedContainerColor = Color(0xFF4F378B).copy(alpha = 0.8f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "По завершенню серії відтворення наступної",
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = IbmPlexMono,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (autoPlayNext) "Увімкнено (автоматичний перехід)" else "Вимкнено (закривати плеєр)",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = IbmPlexMono,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (autoPlayNext) Color(0xFFD0BCFF) else Color(0xFF333842)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (autoPlayNext) {
                                Text("✓", color = Color(0xFF21005D), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
