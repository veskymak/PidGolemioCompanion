package cz.pid.golemiocompanion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkPidScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF4ADE80),
    background = Color(0xFF0B0F19),
    surface = Color(0xFF151B28),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun GolemioCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkPidScheme,
        content = content
    )
}