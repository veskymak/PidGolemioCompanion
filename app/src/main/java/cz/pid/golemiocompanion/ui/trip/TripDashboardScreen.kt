package cz.pid.golemiocompanion.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.pid.golemiocompanion.domain.model.*
import cz.pid.golemiocompanion.ui.components.ActiveTripCountdownGlassPanel
import cz.pid.golemiocompanion.ui.components.MeteoAirQualityGlassCard
import cz.pid.golemiocompanion.ui.components.liquidGlass
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val departureHHmmFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Prague"))

@Composable
fun TripDashboardScreen(
    viewModel: TripViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val remainingSeconds by viewModel.remainingSecondsFlow.collectAsStateWithLifecycle()
    val favorites by viewModel.favoritesFlow.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "meteo_aqi_card") {
            MeteoAirQualityGlassCard(weatherInfo = uiState.weatherAndAqi)
        }

        if (uiState.trackingMode !is TripTrackingMode.Idle) {
            item(key = "active_countdown_panel") {
                ActiveTripCountdownGlassPanel(
                    trackingMode = uiState.trackingMode,
                    remainingSecondsProvider = { remainingSeconds },
                    onActionStillNotHere = viewModel::onActionStillNotHere,
                    onActionBoarded = viewModel::onActionBoarded,
                    onActionMissed = viewModel::onActionMissedConnection,
                    onStopTracking = viewModel::stopTracking
                )
            }
        }

        val rescueMode = uiState.trackingMode as? TripTrackingMode.MissedConnectionRescue
        if (rescueMode != null) {
            item(key = "missed_rescue_panel") {
                MissedConnectionRescueGlassSection(rescueMode = rescueMode)
            }
        }

        if (favorites.isNotEmpty()) {
            item(key = "favorites_row") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Oblíbené spoje (s časem odjezdu)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(favorites, key = { it.id }) { fav ->
                            FavoriteChipGlass(
                                favorite = fav,
                                onSelect = { viewModel.selectFavorite(fav) },
                                onRemove = { viewModel.removeFavoriteConnection(fav.id) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "search_bar_glass") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 22.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.stopSearchQuery,
                        onValueChange = viewModel::onStopQueryChanged,
                        label = { Text("Zastávka PID (např. Mladá Boleslav)", color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.stopSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.onStopQueryChanged("")
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Vymazat zastávku",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF60A5FA),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilledIconButton(
                        onClick = viewModel::refreshStopData,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF2563EB)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Vyhledat odjezdy",
                            tint = Color.White
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.lineFilterQuery,
                        onValueChange = viewModel::onLineFilterChanged,
                        label = { Text("Filtr linky (např. 412, 177)", color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.lineFilterQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.onLineFilterChanged("")
                                        viewModel.refreshStopData()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Vymazat filtr linky",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF60A5FA),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Našeptávač návrhů zastávek PID (Suggestions s automatickým načtením po dopsání)
                if (uiState.stopSuggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(cornerRadius = 16.dp, tintColor = Color(0xFF38BDF8), surfaceAlphaTop = 0.18f)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 Návrhy zastávek PID:",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "automatické načtení za 3s",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.stopSuggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.onSelectSuggestedStop(suggestion.name)
                                        }
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = suggestion.icon,
                                        fontSize = 16.sp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${suggestion.typeDescription} • ${suggestion.subtitle}",
                                            color = Color.White.copy(alpha = 0.72f),
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowOutward,
                                        contentDescription = "Zvolit zastávku",
                                        tint = Color(0xFF38BDF8).copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Rychlý výběr oblíbených a mimopražských uzlů (Mladá Boleslav atd.)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(
                        "Mladá Boleslav,aut.st." to "🚌 MB aut. st.",
                        "Mladá Boleslav město" to "🚆 MB město",
                        "Mladá Boleslav, hl.nádr." to "🚆 MB hl.nádr.",
                        "Černý Most" to "🚌 Černý Most",
                        "Anděl" to "📍 Anděl",
                        "Hlavní nádraží" to "🚆 Praha hl.n.",
                        "Dejvická" to "📍 Dejvická"
                    )
                    items(presets) { (query, label) ->
                        val isSelected = uiState.stopSearchQuery.equals(query, ignoreCase = true)
                        Surface(
                            color = if (isSelected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.clickable {
                                viewModel.onStopQueryChanged(query)
                                viewModel.refreshStopData()
                            }
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Dynamický našeptávač zastávek pro Mladou Boleslav
                val queryNorm = uiState.stopSearchQuery.lowercase()
                if (queryNorm.contains("boles") || queryNorm.contains("mlad") || queryNorm == "mb") {
                    val mbStops = listOf(
                        "Mladá Boleslav město",
                        "Mladá Boleslav,aut.st.",
                        "Mladá Boleslav, hl.nádr.",
                        "Mladá Boleslav, nám. Republiky",
                        "Mladá Boleslav, Václava Klementa",
                        "Mladá Boleslav, U Sportu",
                        "Mladá Boleslav, Kosmonosy"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Zastávky v Mladé Boleslavi:",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(mbStops) { stopName ->
                                Surface(
                                    color = Color(0xFF0369A1).copy(alpha = 0.4f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.clickable {
                                        viewModel.onStopQueryChanged(stopName)
                                        viewModel.refreshStopData()
                                    }
                                ) {
                                    val chipText = if (stopName.contains(", ")) {
                                        stopName.substringAfter(", ")
                                    } else {
                                        stopName.replace("Mladá Boleslav ", "")
                                    }
                                    Text(
                                        text = chipText,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "departures_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nejbližší odjezdy • ${uiState.stopSearchQuery}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF60A5FA)
                    )
                } else {
                    val badgeText = when {
                        uiState.isLiveData -> "● LIVE Golemio"
                        uiState.isApiKeyConfigured -> "● Golemio API"
                        else -> "DEMO data"
                    }
                    val badgeColor = when {
                        uiState.isLiveData -> Color(0xFF4ADE80)
                        uiState.isApiKeyConfigured -> Color(0xFF38BDF8)
                        else -> Color(0xFFFBBF24)
                    }
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        uiState.departuresError?.let { error ->
            item(key = "departures_error") {
                Text(
                    text = error,
                    color = Color(0xFFFCA5A5),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(cornerRadius = 18.dp)
                        .padding(14.dp)
                )
            }
        }

        if (uiState.departures.isEmpty() && uiState.departuresError == null && !uiState.isLoading) {
            item(key = "departures_empty") {
                Text(
                    text = if (uiState.lineFilterQuery.isNotBlank()) {
                        "Pro linku ${uiState.lineFilterQuery} nebyly na zastávce „${uiState.stopSearchQuery}“ nalezeny žádné odjezdy v příští hodině."
                    } else {
                        "Pro zastávku „${uiState.stopSearchQuery}“ nebyly nalezeny žádné odjezdy v příští hodině."
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(cornerRadius = 18.dp)
                        .padding(14.dp)
                )
            }
        }

        items(uiState.departures, key = { it.tripId }) { connection ->
            DepartureRowGlassCard(
                connection = connection,
                onSelectForCountdown = { viewModel.startTrackingConnection(connection) },
                onSaveFavorite = { timeHHmm ->
                    viewModel.saveFavoriteConnection(connection, timeHHmm)
                }
            )
        }
    }
}

@Composable
private fun DepartureRowGlassCard(
    connection: StopConnection,
    onSelectForCountdown: () -> Unit,
    onSaveFavorite: (String) -> Unit
) {
    val nowMs = System.currentTimeMillis()
    val diffMs = connection.effectiveDepartureEpochMs - nowMs
    val inMinutes = (diffMs / 60_000L).coerceAtLeast(0L)
    val isArrivingNow = diffMs in -120_000L..45_000L
    val plannedHHmm = remember(connection.scheduledDepartureEpochMs) {
        departureHHmmFormatter.format(Instant.ofEpochMilli(connection.scheduledDepartureEpochMs))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 20.dp)
            .clickable(onClick = onSelectForCountdown)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                color = if (connection.routeType == 0) Color(0xFFDC2626) else Color(0xFF2563EB),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = connection.routeShortName,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = connection.headsign,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Nástupiště ${connection.platformCode} • JŘ $plannedHHmm" +
                        if (connection.totalDelaySeconds > 0) " (+${connection.totalDelaySeconds}s)" else " (včas)",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        isArrivingNow -> "PŘÍJEZD"
                        inMinutes == 0L -> "< 1 min"
                        inMinutes >= 60L -> "${inMinutes / 60} h ${inMinutes % 60} min"
                        else -> "$inMinutes min"
                    },
                    color = if (isArrivingNow) Color(0xFF34D399) else Color(0xFF38BDF8),
                    fontWeight = FontWeight.Black,
                    fontSize = if (isArrivingNow) 15.sp else 18.sp
                )
                Text(
                    text = "Spustit odpočet",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = { onSaveFavorite(plannedHHmm) }) {
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = "Uložit spoj s časem do oblíbených",
                    tint = Color(0xFFFACC15)
                )
            }
        }
    }
}

@Composable
private fun FavoriteChipGlass(
    favorite: FavoriteConnection,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .liquidGlass(cornerRadius = 16.dp, tintColor = Color(0xFFFACC15))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color(0xFFFACC15),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = "${favorite.routeShortName} • ${favorite.stopName}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = "Odjezd ${favorite.preferredDepartureTimeHHmm} → ${favorite.headsign}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Odebrat",
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onRemove)
        )
    }
}

@Composable
private fun MissedConnectionRescueGlassSection(
    rescueMode: TripTrackingMode.MissedConnectionRescue
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 24.dp, tintColor = Color(0xFFEC4899))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DirectionsBike,
                contentDescription = null,
                tint = Color(0xFFF472B6)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Záchranná mikromobilita (Nextbike & Rekola v okolí)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        rescueMode.nearbyBikes.forEach { bike ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 14.dp, surfaceAlphaTop = 0.14f)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${bike.provider}: ${bike.name}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Vzdálenost ${bike.distanceMeters} m (~${bike.walkingMinutes} min chůze)",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.25f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${bike.freeBikes} volných kol",
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (rescueMode.emergencyTransfers.isNotEmpty()) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TransferWithinAStation,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Krizový přestup z okolních zastávek (do 600 m)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            rescueMode.emergencyTransfers.forEach { transfer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(cornerRadius = 14.dp, tintColor = Color(0xFF38BDF8))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Linka ${transfer.routeShortName} ze zast. ${transfer.nearbyStopName} (${transfer.platformCode})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${transfer.headsign} • ${transfer.distanceMeters} m pěšky",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "za ${transfer.departureInMinutes} min",
                        color = Color(0xFF7DD3FC),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}