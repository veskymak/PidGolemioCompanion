package cz.pid.golemiocompanion.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pid.golemiocompanion.domain.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val timeFormatterHHmm: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Prague"))

@Composable
fun MeteoAirQualityGlassCard(
    weatherInfo: AirQualityWeatherInfo,
    modifier: Modifier = Modifier
) {
    val aqiColor = remember(weatherInfo.aqiIndex) {
        when (weatherInfo.aqiIndex) {
            1, 2 -> Color(0xFF4ADE80)
            3 -> Color(0xFFFACC15)
            else -> Color(0xFFF87171)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 20.dp, tintColor = aqiColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Thermostat,
                contentDescription = "Teplota",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "${String.format("%.1f", weatherInfo.temperatureCelsius)} °C",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = weatherInfo.stationName,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Air,
                    contentDescription = "Kvalita ovzduší",
                    tint = aqiColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ovzduší: ${weatherInfo.aqiLabelCs}",
                    color = aqiColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            Text(
                text = "PM10: ${weatherInfo.pm10UgM3} µg/m³ • NO₂: ${weatherInfo.no2UgM3} µg/m³",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ActiveTripCountdownGlassPanel(
    trackingMode: TripTrackingMode,
    remainingSecondsProvider: () -> Long,
    onActionStillNotHere: () -> Unit,
    onActionBoarded: () -> Unit,
    onActionMissed: () -> Unit,
    onStopTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingSeconds = remainingSecondsProvider()

    val formattedTime by remember(remainingSeconds) {
        derivedStateOf {
            val isNegative = remainingSeconds < 0
            val absSec = abs(remainingSeconds)
            val mins = absSec / 60
            val secs = absSec % 60
            val prefix = if (isNegative) "-" else ""
            String.format("%s%02d:%02d", prefix, mins, secs)
        }
    }

    val showZeroActionCards by remember(trackingMode, remainingSeconds) {
        derivedStateOf {
            when (trackingMode) {
                is TripTrackingMode.ReachedZeroActionPrompt -> true
                is TripTrackingMode.CountingDownToDeparture -> remainingSeconds <= 0L
                is TripTrackingMode.WaitingInNegativeTime -> true
                else -> false
            }
        }
    }

    val activeConnection: StopConnection? = when (trackingMode) {
        is TripTrackingMode.CountingDownToDeparture -> trackingMode.connection
        is TripTrackingMode.ReachedZeroActionPrompt -> trackingMode.connection
        is TripTrackingMode.WaitingInNegativeTime -> trackingMode.connection
        is TripTrackingMode.BoardedCountingToDestination -> trackingMode.connection
        is TripTrackingMode.MissedConnectionRescue -> trackingMode.nextConnection ?: trackingMode.missedConnection
        TripTrackingMode.Idle -> null
    }

    if (activeConnection == null) return

    val isBoarded = trackingMode is TripTrackingMode.BoardedCountingToDestination
    val isWaitingNegative = trackingMode is TripTrackingMode.WaitingInNegativeTime

    val pulseScale by animateFloatAsState(
        targetValue = if (remainingSeconds in 0..10 && !isBoarded) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "120HzCountdownScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                cornerRadius = 28.dp,
                tintColor = when {
                    isBoarded -> Color(0xFF34D399)
                    isWaitingNegative -> Color(0xFFFBBF24)
                    remainingSeconds <= 0 -> Color(0xFFF87171)
                    else -> Color(0xFF60A5FA)
                }
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF2563EB),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = activeConnection.routeShortName,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = activeConnection.headsign,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${activeConnection.stopName} • Nástupiště ${activeConnection.platformCode}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(onClick = onStopTracking) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Ukončit sledování",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                isBoarded -> "JÍZDA V SPOJI • ODPOČET DO CÍLOVÉ STANICE"
                isWaitingNegative -> "REŽIM ČEKÁNÍ NA SPOJ (ČEKÁM NA AKTUALIZACI DELAY Z GOLEMIO API)"
                remainingSeconds <= 0 -> "SPOJ BY MĚL BÝT NA ZASTÁVCE"
                else -> "ODJEZD ZE ZASTÁVKY ZA"
            },
            color = Color(0xFF93C5FD),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
        ) {
            AnimatedContent(
                targetState = formattedTime,
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { height -> height / 3 } + fadeIn()).togetherWith(
                        slideOutVertically { height -> -height / 3 } + fadeOut()
                    )
                },
                label = "DigitTicker120Hz"
            ) { targetText ->
                Text(
                    text = targetText,
                    color = when {
                        isWaitingNegative -> Color(0xFFFBBF24)
                        isBoarded -> Color(0xFF4ADE80)
                        else -> Color.White
                    },
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        val delaySec = activeConnection.totalDelaySeconds
        val arrivalHHmm = remember(activeConnection.effectiveDestinationArrivalEpochMs) {
            timeFormatterHHmm.format(Instant.ofEpochMilli(activeConnection.effectiveDestinationArrivalEpochMs))
        }

        if (isBoarded) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Cíl: ${activeConnection.destinationName} • Plánovaný příjezd v $arrivalHHmm",
                color = Color(0xFF86EFAC),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            val delayText = if (delaySec <= 0) {
                "Bez zpoždění (přesně dle JŘ)"
            } else {
                val aiNote = if (activeConnection.phantomAiExtraDelaySeconds > 0) {
                    " (vč. +${activeConnection.phantomAiExtraDelaySeconds}s AI heuristiky)"
                } else ""
                "Aktuální zpoždění: +${delaySec} s$aiNote"
            }
            Text(
                text = delayText,
                color = if (delaySec > 60) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }

        AnimatedVisibility(
            visible = showZeroActionCards,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Jaká je situace na zastávce?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                ActionZeroGlassCard(
                    title = "Ještě tu není",
                    subtitle = "Přejít do záporného odpočtu a čekat na aktualizovaný delay z API",
                    icon = Icons.Default.HourglassBottom,
                    accentColor = Color(0xFFFBBF24),
                    onClick = onActionStillNotHere
                )

                ActionZeroGlassCard(
                    title = "Nastoupil/a jsem",
                    subtitle = "Přepnout na odpočet do cílové stanice (příjezd v $arrivalHHmm)",
                    icon = Icons.Default.DirectionsBus,
                    accentColor = Color(0xFF34D399),
                    onClick = onActionBoarded
                )

                ActionZeroGlassCard(
                    title = "Ujel mi",
                    subtitle = "Najít další spoj, sdílená kola (Nextbike/Rekola) a krizové přestupy",
                    icon = Icons.Default.DirectionsBike,
                    accentColor = Color(0xFFF87171),
                    onClick = onActionMissed
                )
            }
        }
    }
}

@Composable
private fun ActionZeroGlassCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 18.dp, tintColor = accentColor, surfaceAlphaTop = 0.22f)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f)
        )
    }
}