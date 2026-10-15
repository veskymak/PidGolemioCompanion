package cz.pid.golemiocompanion.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pid.golemiocompanion.data.local.LabSettings
import cz.pid.golemiocompanion.domain.model.*
import cz.pid.golemiocompanion.ui.components.ActiveCountdownBanner
import cz.pid.golemiocompanion.ui.components.formatDelayLabel
import cz.pid.golemiocompanion.ui.components.liquidGlassCard
import kotlinx.coroutines.delay

@Composable
fun TripDashboardScreen(
    uiState: TripUiState,
    labSettings: LabSettings,
    onSearchQueryChanged: (String) -> Unit,
    onSelectStop: (PidStop) -> Unit,
    onSubmitCustomStop: () -> Unit,
    onToggleTrackDeparture: (DepartureInfo) -> Unit,
    onClearTrackedDeparture: () -> Unit,
    onRefreshDepartures: () -> Unit,
    onRequestGpsLocation: () -> Unit,
    onCheckHapticAlert: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            uiState.trackedDeparture?.let { tracked ->
                val remSec = tracked.remainingSecondsAt(nowMillis)
                onCheckHapticAlert(remSec)
            }
            delay(1_000L)
        }
    }

    // Striktní pojistka přímo v UI: zobrazit pouze spoje, které ještě neodjely
    val activeDepartures = remember(uiState.departures, nowMillis) {
        uiState.departures.filter { it.exactDepartureEpochMillis >= nowMillis - 5_000L }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .statusBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Vyhledávač a našeptávač zastávek (MB, MH, Žďár, Doubrava, Loukov, Turnov, Praha)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassCard(cornerRadius = 22.dp)
                .padding(12.dp)
        ) {
            Column {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text(
                            "Hledat zastávku (např. Žďár, Doubrava, Mnichovo Hradiště, MB, Turnov…)",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Vymazat",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            }
                            IconButton(onClick = onRequestGpsLocation) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Najít nejbližší podle GPS",
                                    tint = Color(0xFF22C55E)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSubmitCustomStop()
                            focusManager.clearFocus()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Našeptávač zastávek při psaní
                if (uiState.searchSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                    ) {
                        uiState.searchSuggestions.take(7).forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectStop(suggestion)
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    val icon = when (suggestion.trafficType) {
                                        "train" -> "🚆"
                                        "tram" -> "🚊"
                                        "metroA", "metroB", "metroC", "metroAB", "metroAC", "metroBC" -> "🚇"
                                        "funicular" -> "🚠"
                                        "ferry" -> "⛴️"
                                        else -> if (suggestion.isTrain) "🚆" else "🚌"
                                    }
                                    Text(
                                        text = "$icon ${suggestion.displayName}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (suggestion.lines.isNotEmpty()) {
                                        Text(
                                            text = "Linky: ${suggestion.lines.take(6).joinToString(", ")}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Text(
                                    text = suggestion.region,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        }
                    }
                }

                // Doporučené nejbližší zastávky podle GPS
                if (uiState.nearbyStops.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📍 Nejbližší zastávky podle GPS",
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Aktualizovat GPS",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { onRequestGpsLocation() }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.nearbyStops) { nearby ->
                            val isSelected = nearby.stop.golemioPrimaryName == uiState.selectedStop.golemioPrimaryName
                            AssistChip(
                                onClick = { onSelectStop(nearby.stop) },
                                label = {
                                    Text(
                                        text = "${if (nearby.stop.isTrain) "🚆" else "🚌"} ${nearby.stop.displayName} (${nearby.formattedDistance})",
                                        color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isSelected) Color(0xFF4ADE80) else Color(0xFF1E293B)
                                ),
                                border = null
                            )
                        }
                    }
                } else {
                    // Rychlé přepínače oblíbených zastávek (Žďár, Doubrava, Loukov, MH, MB, Turnov)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PidStopDirectory.quickSelectStops) { stop ->
                            val isSelected = stop.golemioPrimaryName == uiState.selectedStop.golemioPrimaryName
                            AssistChip(
                                onClick = { onSelectStop(stop) },
                                label = {
                                    Text(
                                        text = "${if (stop.isTrain) "🚆" else "🚌"} ${stop.displayName}",
                                        color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF283246)
                                ),
                                border = null
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Aktivní sekundový odpočet vybraného spoje
        uiState.trackedDeparture?.let { tracked ->
            ActiveCountdownBanner(
                departure = tracked,
                isPipMode = false,
                onStopTracking = onClearTrackedDeparture
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Hlavička odjezdové tabule
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Nejbližší odjezdy • ${uiState.selectedStop.displayName}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "LIVE Golemio",
                    color = Color(0xFF22C55E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = onRefreshDepartures,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Obnovit",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (uiState.isLoading && activeDepartures.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF38BDF8))
            }
        } else if (uiState.errorMessage != null && activeDepartures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .liquidGlassCard(cornerRadius = 18.dp)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.errorMessage,
                        color = Color(0xFFF87171),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (uiState.errorMessage.contains("401") || labSettings.golemioApiKey.isBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ℹ️ Vložte prosím svůj Golemio API klíč v záložce Laboratoř pro načítání reálných dat.",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else if (activeDepartures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
                    .liquidGlassCard()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V nejbližších 3 hodinách nejsou ze zastávky ${uiState.selectedStop.displayName} hlášeny žádné další odjezdy.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 110.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(activeDepartures, key = { it.id }) { departure ->
                    DepartureRowCard(
                        departure = departure,
                        nowMillis = nowMillis,
                        showExactSeconds = labSettings.showExactSecondsInBoard,
                        isTracked = uiState.trackedDeparture?.id == departure.id,
                        onToggleTrack = { onToggleTrackDeparture(departure) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DepartureRowCard(
    departure: DepartureInfo,
    nowMillis: Long,
    showExactSeconds: Boolean,
    isTracked: Boolean,
    onToggleTrack: () -> Unit
) {
    val remainingSeconds = departure.remainingSecondsAt(nowMillis).coerceAtLeast(0L)
    val remainingMinutes = remainingSeconds / 60L
    val remSecPart = remainingSeconds % 60L

    val countdownText = when {
        showExactSeconds && remainingMinutes < 60 -> String.format("%d:%02d", remainingMinutes, remSecPart)
        remainingSeconds < 60 -> "< 1 min"
        else -> "$remainingMinutes min"
    }

    val badgeColor = when (departure.vehicleType) {
        VehicleType.TRAM -> Color(0xFFC8102E)
        VehicleType.METRO -> when (departure.routeShortName) {
            "A" -> Color(0xFF00A562)
            "B" -> Color(0xFFF8B323)
            "C" -> Color(0xFFCF003D)
            else -> Color(0xFF2563EB)
        }
        VehicleType.TRAIN -> Color(0xFF2563EB)
        else -> Color(0xFF0284C7)
    }

    val delayColor = when {
        !departure.isDelayAvailable -> Color(0xFF94A3B8)
        departure.delaySeconds <= 60 -> Color(0xFF4ADE80)
        departure.delaySeconds <= 180 -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(
                cornerRadius = 20.dp,
                borderAlpha = if (isTracked) 0.45f else 0.16f
            )
            .clickable { onToggleTrack() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Číslo linky
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(badgeColor)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = departure.routeShortName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Směr a přesný čas JŘ + zpoždění Golemio
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = departure.headsign,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val platformText = departure.platformCode?.let { "Nást. $it • " } ?: ""
                Text(
                    text = "${platformText}JŘ ${departure.scheduledTimeFormatted}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.5.sp,
                    maxLines = 1
                )
                val delayFormatted = if (departure.isDelayAvailable) {
                    if (departure.delaySeconds in -30..30) "včas"
                    else {
                        val m = kotlin.math.abs(departure.delaySeconds) / 60
                        val s = kotlin.math.abs(departure.delaySeconds) % 60
                        val sign = if (departure.delaySeconds > 0) "+" else "-"
                        if (m > 0 && s > 0) "$sign$m min $s s"
                        else if (m > 0) "$sign$m min"
                        else "$sign${s}s"
                    }
                } else "dle JŘ"

                Surface(
                    color = delayColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = delayFormatted,
                        color = delayColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Odpočet a hvězdička
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = countdownText,
                    color = if (remainingSeconds < 60) Color(0xFF38BDF8) else Color(0xFF60A5FA),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = if (isTracked) "Odpočet běží" else "Spustit",
                    color = if (isTracked) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = if (isTracked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (isTracked) Color(0xFFFBBF24) else Color(0xFF94A3B8).copy(alpha = 0.5f),
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 2.dp)
            )
        }
    }
}