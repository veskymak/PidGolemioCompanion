package cz.pid.golemiocompanion.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import cz.pid.golemiocompanion.domain.model.LiveVehicleMarker
import cz.pid.golemiocompanion.ui.components.liquidGlass
import cz.pid.golemiocompanion.ui.trip.TripViewModel

@Composable
fun LiveMapScreen(
    viewModel: TripViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var useTacticalRadarCanvas by remember { mutableStateOf(false) }

    val pragueCenter = remember { LatLng(50.0755, 14.4178) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pragueCenter, 12.5f)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!useTacticalRadarCanvas) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                uiState.liveVehicles.forEach { vehicle ->
                    Marker(
                        state = MarkerState(position = LatLng(vehicle.lat, vehicle.lon)),
                        title = "Linka ${vehicle.routeShortName} → ${vehicle.headsign}",
                        snippet = buildString {
                            append("Zpoždění: ${vehicle.delaySeconds}s • ${vehicle.speedKmh.toInt()} km/h")
                            if (vehicle.isDeadReckoningEstimated) append(" [DEAD RECKONING ODHAD]")
                            if (vehicle.isFromRawInputGateway) append(" [RAW GATEWAY]")
                        },
                        rotation = vehicle.bearing
                    )
                }

                if (uiState.showParkingsLayer) {
                    uiState.parkingsPR.forEach { pr ->
                        Marker(
                            state = MarkerState(position = LatLng(pr.lat, pr.lon)),
                            title = pr.name,
                            snippet = "Volná místa: ${pr.freePlaces} / ${pr.totalPlaces}"
                        )
                    }
                }

                if (uiState.showBikesLayer) {
                    uiState.mapSharedBikes.forEach { bike ->
                        Marker(
                            state = MarkerState(position = LatLng(bike.lat, bike.lon)),
                            title = "${bike.provider}: ${bike.name}",
                            snippet = "Volných kol: ${bike.freeBikes}"
                        )
                    }
                }
            }
        } else {
            InteractivePragueTelemetryRadar(
                vehicles = uiState.liveVehicles,
                showParkings = uiState.showParkingsLayer,
                parkings = uiState.parkingsPR,
                showBikes = uiState.showBikesLayer,
                bikes = uiState.mapSharedBikes
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .liquidGlass(cornerRadius = 24.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.mapLineFilter,
                    onValueChange = viewModel::onMapLineFilterChanged,
                    label = { Text("Filtrovat linku (např. 191)", color = Color.White.copy(alpha = 0.75f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = useTacticalRadarCanvas,
                    onClick = { useTacticalRadarCanvas = !useTacticalRadarCanvas },
                    label = { Text(if (useTacticalRadarCanvas) "Google Mapa" else "120Hz Radar") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.showParkingsLayer,
                    onClick = viewModel::toggleParkingsLayer,
                    leadingIcon = {
                        Icon(Icons.Default.LocalParking, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("P+R (${uiState.parkingsPR.size})") }
                )

                FilterChip(
                    selected = uiState.showBikesLayer,
                    onClick = viewModel::toggleBikesLayer,
                    leadingIcon = {
                        Icon(Icons.Default.PedalBike, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Sdílená kola (${uiState.mapSharedBikes.sumOf { it.freeBikes }})") }
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 96.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.liveVehicles, key = { it.tripId }) { vehicle ->
                VehicleTelemetryCard(vehicle = vehicle)
            }
        }
    }
}

@Composable
private fun VehicleTelemetryCard(vehicle: LiveVehicleMarker) {
    val delayColor = when {
        vehicle.delaySeconds <= 30 -> Color(0xFF4ADE80)
        vehicle.delaySeconds <= 120 -> Color(0xFFFACC15)
        else -> Color(0xFFF87171)
    }

    Column(
        modifier = Modifier
            .width(235.dp)
            .liquidGlass(cornerRadius = 18.dp, tintColor = delayColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Linka ${vehicle.routeShortName}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
            Text(
                text = "Azimut ${vehicle.bearing.toInt()}°",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
        Text(
            text = vehicle.headsign,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Zpoždění: +${vehicle.delaySeconds} s",
                color = delayColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = "${vehicle.speedKmh.toInt()} km/h",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp
            )
        }
        if (vehicle.isDeadReckoningEstimated) {
            Text(
                text = "DEAD RECKONING (V TUNELU)",
                color = Color(0xFFFBBF24),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun InteractivePragueTelemetryRadar(
    vehicles: List<LiveVehicleMarker>,
    showParkings: Boolean,
    parkings: List<cz.pid.golemiocompanion.domain.model.ParkingLotPR>,
    showBikes: Boolean,
    bikes: List<cz.pid.golemiocompanion.domain.model.SharedBikeStation>
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        for (r in listOf(140f, 280f, 420f, 560f)) {
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                radius = r,
                center = center,
                style = Stroke(width = 2f)
            )
        }

        fun project(lat: Double, lon: Double): Offset {
            val dx = ((lon - 14.4178) * 14000.0).toFloat()
            val dy = ((50.0755 - lat) * 20000.0).toFloat()
            return Offset(center.x + dx, center.y + dy)
        }

        if (showParkings) {
            parkings.forEach { pr ->
                val pos = project(pr.lat, pr.lon)
                drawCircle(color = Color(0xFF60A5FA), radius = 14f, center = pos)
            }
        }

        if (showBikes) {
            bikes.forEach { bike ->
                val pos = project(bike.lat, bike.lon)
                drawCircle(color = Color(0xFFEC4899), radius = 10f, center = pos)
            }
        }

        vehicles.forEach { v ->
            val pos = project(v.lat, v.lon)
            val color = when {
                v.delaySeconds <= 30 -> Color(0xFF4ADE80)
                v.delaySeconds <= 120 -> Color(0xFFFACC15)
                else -> Color(0xFFF87171)
            }
            rotate(degrees = v.bearing, pivot = pos) {
                val arrowPath = Path().apply {
                    moveTo(pos.x, pos.y - 22f)
                    lineTo(pos.x - 14f, pos.y + 16f)
                    lineTo(pos.x, pos.y + 8f)
                    lineTo(pos.x + 14f, pos.y + 16f)
                    close()
                }
                drawPath(path = arrowPath, color = color)
            }
        }
    }
}