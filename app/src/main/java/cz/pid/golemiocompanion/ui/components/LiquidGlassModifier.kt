package cz.pid.golemiocompanion.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
fun Modifier.liquidGlass(
    cornerRadius: Dp = 24.dp,
    tintColor: Color = Color(0xFF8AB4F8),
    surfaceAlphaTop: Float = 0.18f,
    surfaceAlphaBottom: Float = 0.08f,
    borderWidth: Dp = 1.dp
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = surfaceAlphaTop),
                    tintColor.copy(alpha = 0.12f),
                    Color.White.copy(alpha = surfaceAlphaBottom)
                )
            ),
            shape = shape
        )
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.65f),
                    Color.White.copy(alpha = 0.18f),
                    Color.Transparent,
                    tintColor.copy(alpha = 0.35f)
                ),
                start = Offset(0f, 0f),
                end = Offset(900f, 900f)
            ),
            shape = shape
        )
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val innerHighlightBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.28f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.18f)
                )
            )
            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    brush = innerHighlightBrush,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = 2.dp.toPx()),
                    blendMode = BlendMode.SrcOver
                )
            }
        }
}

@Stable
fun Modifier.liquidBackgroundBlur(blurRadiusDp: Dp = 16.dp): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            val px = blurRadiusDp.toPx()
            renderEffect = RenderEffect.createBlurEffect(
                px,
                px,
                Shader.TileMode.DECAL
            ).asComposeRenderEffect()
        }
    } else {
        this
    }
}