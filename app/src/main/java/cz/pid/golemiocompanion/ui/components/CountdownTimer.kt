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

import androidx.compose.material.icons.filled.Map
import cz.pid.golemiocompanion.domain.model.VehicleTrackingTelemetry

@Composable
fun ActiveCountdownBanner(
    departure: DepartureInfo,
    telemetry: VehicleTrackingTelemetry? = null,
    isLabDistanceEta: Boolean = true,
    isPipMode: Boolean = false,
    onOpenLiveMap: (() -> Unit)? = null,
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

    val targetDepartureEpoch = if (isLabDistanceEta && telemetry != null) {
        telemetry.calculatedDepartureEpochMillis
    } else {
        departure.exactDepartureEpochMillis
    }

    val isAtStop = telemetry?.isAtStop == true

    // Přesný rozdíl v sekundách
    val remainingSec = ((targetDepartureEpoch - nowMillis) / 1000L).coerceAtLeast(0L)
    val minutes = remainingSec / 60L
    val seconds = remainingSec % 60L

    val formattedCountdown = if (isAtStop) {
        "V ZASTÁVCE"
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    val urgencyColor = when {
        isAtStop -> Color(0xFF22C55E) // Zelená - odbavování
        remainingSec <= 60 -> Color(0xFFFF5252)
        remainingSec <= 180 -> Color(0xFFFFB74D)
        else -> Color(0xFF38BDF8)
    }

    val progressTarget = if (isAtStop) 0.05f else (remainingSec.toFloat() / 900f).coerceIn(0f, 1f)
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
                    fontSize = if (isAtStop) 18.sp else 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
                val subtext = if (telemetry != null) {
                    "${telemetry.distanceMeters} m • ${telemetry.speedKmh} km/h • ${formatDelayLabel(telemetry.delaySeconds, true)}"
                } else {
                    "JŘ ${departure.scheduledTimeFormatted} (${formatDelayLabel(departure.delaySeconds, departure.isDelayAvailable)})"
                }
                Text(
                    text = subtext,
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
                        text = if (isLabDistanceEta && telemetry?.isGpsCorrectionActive == true) "AKTIVNÍ ODPOČET (🧪 GPS KOREKCE < 700 M)" else "AKTIVNÍ ODPOČET (JŘ + GOLEMIO)",
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
                    val detailText = if (telemetry != null) {
                        val corrPart = if (telemetry.isGpsCorrectionActive && telemetry.correctionSeconds != 0) {
                            val sign = if (telemetry.correctionSeconds > 0) "+" else ""
                            " • GPS ${sign}${telemetry.correctionSeconds}s"
                        } else ""
                        "Vzdálenost ${telemetry.distanceMeters} m • Poloha před ${telemetry.pingAgeSeconds} s • ${telemetry.speedKmh} km/h • Zpoždění ${formatDelayLabel(telemetry.delaySeconds, true)}$corrPart"
                    } else {
                        "JŘ ${departure.scheduledTimeFormatted} • Zpoždění ${formatDelayLabel(departure.delaySeconds, departure.isDelayAvailable)} • Odjezd v ${departure.exactDepartureTimeFormatted}"
                    }
                    Text(
                        text = detailText,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formattedCountdown,
                        color = urgencyColor,
                        fontSize = if (isAtStop) 18.sp else 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    if (onOpenLiveMap != null) {
                        IconButton(
                            onClick = onOpenLiveMap,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0xFF0284C7).copy(alpha = 0.25f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Zobrazit vůz na mapě",
                                tint = Color(0xFF38BDF8)
                            )
                        }
                    }

                    IconButton(onClick = onStopTracking) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zrušit odpočet",
                            tint = Color(0xFF94A3B8)
                        )
                    }
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