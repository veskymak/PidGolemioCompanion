package cz.pid.golemiocompanion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pid.golemiocompanion.domain.model.DepartureInfo
import kotlinx.coroutines.delay

@Composable
fun ActiveCountdownBanner(
    departure: DepartureInfo,
    isPipMode: Boolean = false,
    onStopTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(departure.id) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(250L)
        }
    }

    // Přesný rozdíl mezi (JŘ + zpoždění Golemio) a aktuálním časem v sekundách
    val remainingSec = ((departure.exactDepartureEpochMillis - nowMillis) / 1000L).coerceAtLeast(0L)
    val minutes = remainingSec / 60L
    val seconds = remainingSec % 60L

    val formattedCountdown = String.format("%02d:%02d", minutes, seconds)
    val urgencyColor = when {
        remainingSec <= 60 -> Color(0xFFFF5252)
        remainingSec <= 180 -> Color(0xFFFFB74D)
        else -> Color(0xFF38BDF8)
    }

    val progressTarget = (remainingSec.toFloat() / 900f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 250),
        label = "countdown_progress"
    )

    if (isPipMode) {
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Linka ${departure.routeShortName} • ${departure.headsign}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedCountdown,
                    color = urgencyColor,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "JŘ ${departure.scheduledTimeFormatted} (${formatDelayLabel(departure.delaySeconds, departure.isDelayAvailable)})",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassCard(cornerRadius = 20.dp, borderAlpha = 0.28f)
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AKTIVNÍ ODPOČET (JŘ + GOLEMIO)",
                        color = urgencyColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Linka ${departure.routeShortName} → ${departure.headsign}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "JŘ ${departure.scheduledTimeFormatted} • Zpoždění ${formatDelayLabel(departure.delaySeconds, departure.isDelayAvailable)} • Odjezd v ${departure.exactDepartureTimeFormatted}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = formattedCountdown,
                    color = urgencyColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                IconButton(onClick = onStopTracking) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Zrušit odpočet",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                color = urgencyColor,
                trackColor = Color(0xFF334155),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            )
        }
    }
}

fun formatDelayLabel(delaySeconds: Int, isDelayAvailable: Boolean): String {
    if (!isDelayAvailable) return "dle JŘ"
    if (delaySeconds in -30..30) return "včas"
    val absSec = kotlin.math.abs(delaySeconds)
    val sign = if (delaySeconds > 0) "+" else "-"
    val m = absSec / 60
    val s = absSec % 60
    return when {
        m > 0 && s > 0 -> "$sign$m min $s s"
        m > 0 -> "$sign$m min"
        else -> "$sign${s}s"
    }
}