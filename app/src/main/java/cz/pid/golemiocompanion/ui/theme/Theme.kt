package cz.pid.golemiocompanion.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LiquidDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF818CF8),
    tertiary = Color(0xFFF472B6),
    background = Color(0xFF070B19),
    surface = Color(0xFF0F172A)
)

@Composable
fun PidGolemioGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiquidDarkColorScheme
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0B1120),
                            Color(0xFF050811)
                        ),
                        center = Offset(300f, 400f),
                        radius = 1600f
                    )
                )
        ) {
            content()
        }
    }
}