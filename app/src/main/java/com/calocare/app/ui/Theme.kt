package com.calocare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.calocare.app.data.Warning

object Palette {
    val In = Color(0xFF1F9D61) // calories eaten
    val Out = Color(0xFFF28C38) // calories burned
    val Protein = Color(0xFFE05D6F)
    val Carbs = Color(0xFFE6A700)
    val Fat = Color(0xFF3B82C4)
    val Danger = Color(0xFFD32F2F)
    val Warn = Color(0xFFB26A00)
}

private val Light = lightColorScheme(
    primary = Color(0xFF1F9D61), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F2E0), onPrimaryContainer = Color(0xFF0B3D25),
    secondary = Color(0xFFF28C38), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6D0), onSecondaryContainer = Color(0xFF4A2500),
    tertiary = Color(0xFF3B82C4), tertiaryContainer = Color(0xFFDCEBFA), onTertiaryContainer = Color(0xFF0D2F4F),
    background = Color(0xFFF6F8F4), onBackground = Color(0xFF1B1F1C),
    surface = Color.White, onSurface = Color(0xFF1B1F1C),
    surfaceVariant = Color(0xFFEDF1EA), onSurfaceVariant = Color(0xFF5B635C),
    errorContainer = Color(0xFFFDE2E0), onErrorContainer = Color(0xFF6E1410),
    outlineVariant = Color(0xFFDDE3DA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6FD9A0), onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF12512F), onPrimaryContainer = Color(0xFFC9F2D8),
    secondary = Color(0xFFFFB57A), onSecondary = Color(0xFF4A2500),
    secondaryContainer = Color(0xFF5E3410), onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFF8DBFF0), tertiaryContainer = Color(0xFF1C4468), onTertiaryContainer = Color(0xFFD3E6FA),
    background = Color(0xFF111512), onBackground = Color(0xFFE3E7E2),
    surface = Color(0xFF1A1F1B), onSurface = Color(0xFFE3E7E2),
    surfaceVariant = Color(0xFF262C27), onSurfaceVariant = Color(0xFFA9B2AA),
    errorContainer = Color(0xFF5C1A16), onErrorContainer = Color(0xFFFFDAD6),
    outlineVariant = Color(0xFF353C36),
)

@Composable
fun CaloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** Photo when we have one (online results, AI dishes), otherwise the food's emoji on a soft disc. */
@Composable
fun FoodImage(emoji: String, imageUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        }
    }
}

@Composable
fun WarningLine(w: Warning, modifier: Modifier = Modifier) {
    Text(
        (if (w.danger) "⛔ " else "⚠️ ") + w.text,
        color = if (w.danger) Palette.Danger else Palette.Warn,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (w.danger) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier,
    )
}
