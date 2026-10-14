package cz.pid.golemiocompanion.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import cz.pid.golemiocompanion.BuildConfig
import cz.pid.golemiocompanion.domain.model.LiveVehicleMarker
import cz.pid.golemiocompanion.domain.model.ParkingLotPR
import cz.pid.golemiocompanion.domain.model.SharedBikeStation
import cz.pid.golemiocompanion.ui.components.liquidGlass
import cz.pid.golemiocompanion.ui.trip.TripViewModel
import kotlinx.coroutines.launch

enum class MapDisplayMode(val label: String) {
    GOOGLE_WEB("Webová mapa"),
    GOOGLE_SDK("Google SDK"),
    RADAR("120Hz Radar")
}

@Composable
fun LiveMapScreen(
    viewModel: TripViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val labSettings by viewModel.labSettingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val effectiveMapsKey = remember(labSettings.googleMapsApiKey) {
        labSettings.googleMapsApiKey.ifBlank { BuildConfig.MAPS_API_KEY }.trim()
    }
    val isMapsKeyConfigured = remember(effectiveMapsKey) {
        effectiveMapsKey.isNotBlank() && !effectiveMapsKey.contains("YOUR_GOOGLE_MAPS_API_KEY")
    }

    var displayMode by remember {
        mutableStateOf(MapDisplayMode.GOOGLE_WEB)
    }

    val pragueCenter = remember { LatLng(50.0755, 14.4178) }
    val mbCenter = remember { LatLng(50.4114, 14.9032) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pragueCenter, 12.5f)
    }

    var webCenterLat by remember { mutableDoubleStateOf(50.0755) }
    var webCenterLon by remember { mutableDoubleStateOf(14.4178) }
    var webZoom by remember { mutableFloatStateOf(12.5f) }

    val fusedLocationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fine || coarse
        if (hasLocationPermission) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            val latLng = LatLng(loc.latitude, loc.longitude)
                            userLocation = latLng
                            webCenterLat = loc.latitude
                            webCenterLon = loc.longitude
                            webZoom = 15f
                            scope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                            }
                        }
                    }
            } catch (_: SecurityException) {
            }
        }
    }

    val requestAndCenterOnUserLocation: () -> Unit = {
        if (hasLocationPermission) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            val latLng = LatLng(loc.latitude, loc.longitude)
                            userLocation = latLng
                            webCenterLat = loc.latitude
                            webCenterLon = loc.longitude
                            webZoom = 15.5f
                            scope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 15.5f))
                            }
                        } else {
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                if (lastLoc != null) {
                                    val latLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                                    userLocation = latLng
                                    webCenterLat = lastLoc.latitude
                                    webCenterLon = lastLoc.longitude
                                    webZoom = 15.5f
                                    scope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 15.5f))
                                    }
                                }
                            }
                        }
                    }
            } catch (_: SecurityException) {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        userLocation = LatLng(loc.latitude, loc.longitude)
                    }
                }
            } catch (_: SecurityException) {
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (displayMode) {
            MapDisplayMode.GOOGLE_WEB -> {
                WebGoogleMap(
                    apiKey = effectiveMapsKey,
                    vehicles = uiState.liveVehicles,
                    userLat = userLocation?.latitude,
                    userLon = userLocation?.longitude,
                    showParkings = uiState.showParkingsLayer,
                    parkings = uiState.parkingsPR,
                    showBikes = uiState.showBikesLayer,
                    bikes = uiState.mapSharedBikes,
                    centerLat = webCenterLat,
                    centerLon = webCenterLon,
                    zoom = webZoom
                )
            }
            MapDisplayMode.GOOGLE_SDK -> {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = hasLocationPermission
                    ),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
                ) {
                    userLocation?.let { uLoc ->
                        if (!hasLocationPermission) {
                            Marker(
                                state = MarkerState(position = uLoc),
                                title = "Moje poloha",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            )
                        }
                    }

                    uiState.liveVehicles.forEach { vehicle ->
                        val markerHue = when {
                            vehicle.delaySeconds <= 30 -> BitmapDescriptorFactory.HUE_AZURE
                            vehicle.delaySeconds <= 120 -> BitmapDescriptorFactory.HUE_YELLOW
                            else -> BitmapDescriptorFactory.HUE_RED
                        }
                        Marker(
                            state = MarkerState(position = LatLng(vehicle.lat, vehicle.lon)),
                            title = "Linka ${vehicle.routeShortName} → ${vehicle.headsign}",
                            snippet = buildString {
                                append("Zpoždění: ${vehicle.delaySeconds} s • ${vehicle.speedKmh.toInt()} km/h")
                                if (vehicle.isDeadReckoningEstimated) append(" [DEAD RECKONING]")
                                if (vehicle.isFromRawInputGateway) append(" [RAW STREAM]")
                            },
                            icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                            rotation = vehicle.bearing
                        )
                    }

                    if (uiState.showParkingsLayer) {
                        uiState.parkingsPR.forEach { pr ->
                            Marker(
                                state = MarkerState(position = LatLng(pr.lat, pr.lon)),
                                title = pr.name,
                                snippet = "Volná místa: ${pr.freePlaces} / ${pr.totalPlaces}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                            )
                        }
                    }

                    if (uiState.showBikesLayer) {
                        uiState.mapSharedBikes.forEach { bike ->
                            Marker(
                                state = MarkerState(position = LatLng(bike.lat, bike.lon)),
                                title = "${bike.provider}: ${bike.name}",
                                snippet = "Volných kol: ${bike.freeBikes}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA)
                            )
                        }
                    }
                }
            }
            MapDisplayMode.RADAR -> {
                DynamicTelemetryRadar(
                    centerTarget = cameraPositionState.position.target,
                    userLocation = userLocation,
                    vehicles = uiState.liveVehicles,
                    showParkings = uiState.showParkingsLayer,
                    parkings = uiState.parkingsPR,
                    showBikes = uiState.showBikesLayer,
                    bikes = uiState.mapSharedBikes
                )
            }
        }

        // Horní ovládací a filtrační panel
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                        label = { Text("Filtrovat linku (např. 412, 191)", color = Color.White.copy(alpha = 0.75f)) },
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.mapLineFilter.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onMapLineFilterChanged("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Vymazat filtr",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Výběr režimu mapy (Webová mapa, Google SDK, 120Hz Radar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapDisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = displayMode == mode,
                            onClick = { displayMode = mode },
                            label = { Text(mode.label, fontSize = 11.sp, fontWeight = if (displayMode == mode) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                // Rychlé skoky: Moje poloha, Praha, Mladá Boleslav, Vozidla
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SuggestionChip(
                            onClick = requestAndCenterOnUserLocation,
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (hasLocationPermission) "🎯 Moje poloha" else "🎯 Povolit polohu",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        )
                    }

                    item {
                        SuggestionChip(
                            onClick = {
                                webCenterLat = pragueCenter.latitude
                                webCenterLon = pragueCenter.longitude
                                webZoom = 12.5f
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(pragueCenter, 12.5f)
                                    )
                                }
                            },
                            label = { Text("📍 Praha", color = Color.White, fontSize = 11.sp) }
                        )
                    }

                    item {
                        SuggestionChip(
                            onClick = {
                                webCenterLat = mbCenter.latitude
                                webCenterLon = mbCenter.longitude
                                webZoom = 13.5f
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(mbCenter, 13.5f)
                                    )
                                }
                            },
                            label = { Text("🚌 Mladá Boleslav", color = Color.White, fontSize = 11.sp) }
                        )
                    }

                    if (uiState.liveVehicles.isNotEmpty()) {
                        item {
                            SuggestionChip(
                                onClick = {
                                    val lats = uiState.liveVehicles.map { it.lat }
                                    val lons = uiState.liveVehicles.map { it.lon }
                                    val minLat = lats.minOrNull() ?: 50.0
                                    val maxLat = lats.maxOrNull() ?: 50.5
                                    val minLon = lons.minOrNull() ?: 14.0
                                    val maxLon = lons.maxOrNull() ?: 15.0
                                    webCenterLat = (minLat + maxLat) / 2.0
                                    webCenterLon = (minLon + maxLon) / 2.0
                                    webZoom = 12f
                                    val bounds = LatLngBounds(
                                        LatLng(minLat, minLon),
                                        LatLng(maxLat, maxLon)
                                    )
                                    scope.launch {
                                        runCatching {
                                            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                                        }
                                    }
                                },
                                label = { Text("🔍 Vozidla (${uiState.liveVehicles.size})", color = Color(0xFF38BDF8), fontSize = 11.sp) }
                            )
                        }
                    }
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

            // Upozornění pouze při zvoleném Google SDK, pokud není v manifestu/local.properties klíč
            if (!isMapsKeyConfigured && displayMode == MapDisplayMode.GOOGLE_SDK) {
                Surface(
                    color = Color(0xDD0F172A),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nativní Google SDK vyžaduje Maps SDK pro Android",
                                color = Color(0xFFFDE68A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Režim „Webová mapa“ funguje okamžitě a bez omezení.",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                        TextButton(
                            onClick = { displayMode = MapDisplayMode.GOOGLE_WEB },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                        ) {
                            Text("Webová mapa", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Spodní horizontální lišta s detaily vozidel
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 96.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.liveVehicles, key = { it.tripId }) { vehicle ->
                VehicleTelemetryCard(
                    vehicle = vehicle,
                    onSelect = {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(LatLng(vehicle.lat, vehicle.lon), 15.5f)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun VehicleTelemetryCard(
    vehicle: LiveVehicleMarker,
    onSelect: () -> Unit = {}
) {
    val delayColor = when {
        vehicle.delaySeconds <= 30 -> Color(0xFF4ADE80)
        vehicle.delaySeconds <= 120 -> Color(0xFFFACC15)
        else -> Color(0xFFF87171)
    }

    Column(
        modifier = Modifier
            .width(235.dp)
            .liquidGlass(cornerRadius = 18.dp, tintColor = delayColor)
            .clickable(onClick = onSelect)
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
private fun DynamicTelemetryRadar(
    centerTarget: LatLng,
    userLocation: LatLng?,
    vehicles: List<LiveVehicleMarker>,
    showParkings: Boolean,
    parkings: List<ParkingLotPR>,
    showBikes: Boolean,
    bikes: List<SharedBikeStation>
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

        // Dynamická projekce relativně k aktuálnímu středu
        fun project(lat: Double, lon: Double): Offset {
            val dx = ((lon - centerTarget.longitude) * 16000.0).toFloat()
            val dy = ((centerTarget.latitude - lat) * 22000.0).toFloat()
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

        userLocation?.let { uLoc ->
            val uPos = project(uLoc.latitude, uLoc.longitude)
            drawCircle(color = Color(0xFF38BDF8).copy(alpha = 0.3f), radius = 22f, center = uPos)
            drawCircle(color = Color(0xFF38BDF8), radius = 9f, center = uPos)
            drawCircle(color = Color.White, radius = 4f, center = uPos)
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebGoogleMap(
    apiKey: String,
    vehicles: List<LiveVehicleMarker>,
    userLat: Double?,
    userLon: Double?,
    showParkings: Boolean,
    parkings: List<ParkingLotPR>,
    showBikes: Boolean,
    bikes: List<SharedBikeStation>,
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isReady by remember { mutableStateOf(false) }

    val html = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0b1120; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                .bus-marker {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    border-radius: 8px;
                    padding: 3px 6px;
                    font-weight: 900;
                    font-size: 11px;
                    color: #ffffff;
                    border: 2px solid rgba(255, 255, 255, 0.9);
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
                    white-space: nowrap;
                }
                .bus-green { background: #16a34a; }
                .bus-yellow { background: #d97706; }
                .bus-red { background: #dc2626; }
                
                .user-marker {
                    position: relative;
                    width: 24px;
                    height: 24px;
                }
                .user-dot {
                    width: 14px;
                    height: 14px;
                    background: #38bdf8;
                    border: 2px solid #ffffff;
                    border-radius: 50%;
                    position: absolute;
                    top: 5px;
                    left: 5px;
                    box-shadow: 0 0 12px rgba(56, 189, 248, 0.9);
                }
                .user-pulse {
                    width: 24px;
                    height: 24px;
                    background: rgba(56, 189, 248, 0.45);
                    border-radius: 50%;
                    position: absolute;
                    top: 0;
                    left: 0;
                    animation: userPulse 2s infinite ease-out;
                }
                @keyframes userPulse {
                    0% { transform: scale(0.6); opacity: 1; }
                    100% { transform: scale(2.2); opacity: 0; }
                }

                .parking-marker {
                    background: #2563eb;
                    color: white;
                    border-radius: 6px;
                    padding: 2px 5px;
                    font-size: 10px;
                    font-weight: bold;
                    border: 1px solid #93c5fd;
                    white-space: nowrap;
                }
                .bike-marker {
                    background: #db2777;
                    color: white;
                    border-radius: 6px;
                    padding: 2px 5px;
                    font-size: 10px;
                    font-weight: bold;
                    border: 1px solid #f472b6;
                    white-space: nowrap;
                }
                .leaflet-popup-content-wrapper {
                    background: rgba(15, 23, 42, 0.94);
                    color: white;
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    backdrop-filter: blur(12px);
                    border-radius: 14px;
                    font-size: 12px;
                }
                .leaflet-popup-tip {
                    background: rgba(15, 23, 42, 0.94);
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map', { zoomControl: false, attributionControl: false })
                    .setView([$centerLat, $centerLon], ${zoom.toInt()});
                
                // CartoDB Dark Matter tiles (velmi rychlé, tmavé, dokonale ladící s Liquid Glass)
                var baseTileLayer = L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
                    subdomains: 'abcd',
                    maxZoom: 19
                }).addTo(map);

                baseTileLayer.on('tileerror', function() {
                    if (!window.osmFallbackAdded) {
                        window.osmFallbackAdded = true;
                        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);
                    }
                });

                var markers = {};
                var parkingsLayer = L.layerGroup().addTo(map);
                var bikesLayer = L.layerGroup().addTo(map);
                var userMarker = null;

                function updateUserLocation(lat, lng) {
                    if (lat == null || lng == null) {
                        if (userMarker) {
                            map.removeLayer(userMarker);
                            userMarker = null;
                        }
                        return;
                    }
                    if (userMarker) {
                        userMarker.setLatLng([lat, lng]);
                    } else {
                        var icon = L.divIcon({
                            className: 'user-marker',
                            html: '<div class="user-pulse"></div><div class="user-dot"></div>',
                            iconSize: [24, 24],
                            iconAnchor: [12, 12]
                        });
                        userMarker = L.marker([lat, lng], { icon: icon, zIndexOffset: 2000 }).addTo(map)
                            .bindPopup('<b>Vaše aktuální GPS poloha</b>');
                    }
                }

                function updateVehicles(list) {
                    var currentIds = {};
                    for (var i = 0; i < list.length; i++) {
                        var v = list[i];
                        currentIds[v.id] = true;
                        var colorClass = v.delay <= 30 ? 'bus-green' : (v.delay <= 120 ? 'bus-yellow' : 'bus-red');
                        var iconHtml = '<div class="bus-marker ' + colorClass + '">' + v.line + '</div>';
                        var icon = L.divIcon({
                            className: 'leaflet-bus-icon',
                            html: iconHtml,
                            iconSize: [38, 22],
                            iconAnchor: [19, 11]
                        });

                        var popupContent = '<div style="min-width:140px;">' +
                            '<b style="font-size:14px;color:#38bdf8;">Linka ' + v.line + '</b><br>' +
                            '<span style="color:#e2e8f0;">' + v.headsign + '</span><br>' +
                            '<div style="margin-top:4px;font-size:11px;">' +
                            'Zpoždění: <b style="color:' + (v.delay > 60 ? '#f87171' : '#4ade80') + '">+' + v.delay + ' s</b><br>' +
                            'Rychlost: ' + Math.round(v.speed) + ' km/h' +
                            '</div></div>';

                        if (markers[v.id]) {
                            markers[v.id].setLatLng([v.lat, v.lng]);
                            markers[v.id].setIcon(icon);
                        } else {
                            markers[v.id] = L.marker([v.lat, v.lng], { icon: icon })
                                .addTo(map)
                                .bindPopup(popupContent);
                        }
                    }
                    for (var id in markers) {
                        if (!currentIds[id]) {
                            map.removeLayer(markers[id]);
                            delete markers[id];
                        }
                    }
                }

                function updateParkings(show, list) {
                    parkingsLayer.clearLayers();
                    if (!show || !list) return;
                    for (var i = 0; i < list.length; i++) {
                        var p = list[i];
                        var icon = L.divIcon({
                            className: 'leaflet-p-icon',
                            html: '<div class="parking-marker">P ' + p.free + '</div>',
                            iconSize: [42, 18],
                            iconAnchor: [21, 9]
                        });
                        L.marker([p.lat, p.lng], { icon: icon })
                            .addTo(parkingsLayer)
                            .bindPopup('<b>P+R ' + p.name + '</b><br>Volno: ' + p.free + ' / ' + p.total);
                    }
                }

                function updateBikes(show, list) {
                    bikesLayer.clearLayers();
                    if (!show || !list) return;
                    for (var i = 0; i < list.length; i++) {
                        var b = list[i];
                        var icon = L.divIcon({
                            className: 'leaflet-bike-icon',
                            html: '<div class="bike-marker">🚲 ' + b.free + '</div>',
                            iconSize: [40, 18],
                            iconAnchor: [20, 9]
                        });
                        L.marker([b.lat, b.lng], { icon: icon })
                            .addTo(bikesLayer)
                            .bindPopup('<b>' + b.provider + ': ' + b.name + '</b><br>Volných kol: ' + b.free);
                    }
                }

                function panToCoords(lat, lng, z) {
                    map.flyTo([lat, lng], z || 14, { duration: 0.8 });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    LaunchedEffect(centerLat, centerLon, zoom) {
        webViewInstance?.evaluateJavascript("if (typeof panToCoords === 'function') panToCoords($centerLat, $centerLon, ${zoom.toInt()});", null)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isReady = true
                    }
                }
                loadDataWithBaseURL("https://pid-golemio-map.internal", html, "text/html", "UTF-8", null)
                webViewInstance = this
            }
        },
        update = { webView ->
            if (isReady) {
                // Aktualizace GPS polohy uživatele
                if (userLat != null && userLon != null) {
                    webView.evaluateJavascript("if (typeof updateUserLocation === 'function') updateUserLocation($userLat, $userLon);", null)
                } else {
                    webView.evaluateJavascript("if (typeof updateUserLocation === 'function') updateUserLocation(null, null);", null)
                }

                // Aktualizace vozidel PID
                val vehiclesJson = vehicles.take(120).map { v ->
                    val cleanLine = v.routeShortName.replace("\"", "").replace("'", "")
                    val cleanHeadsign = v.headsign.replace("\"", "").replace("'", "")
                    """{"id":"${v.tripId.replace("\"", "")}","line":"$cleanLine","headsign":"$cleanHeadsign","lat":${v.lat},"lng":${v.lon},"delay":${v.delaySeconds},"speed":${v.speedKmh}}"""
                }.joinToString(",", "[", "]")
                webView.evaluateJavascript("if (typeof updateVehicles === 'function') updateVehicles($vehiclesJson);", null)

                // Aktualizace P+R parkovišť
                if (showParkings) {
                    val parkingsJson = parkings.take(80).map { p ->
                        val cleanName = p.name.replace("\"", "").replace("'", "")
                        """{"name":"$cleanName","lat":${p.lat},"lng":${p.lon},"free":${p.freePlaces},"total":${p.totalPlaces}}"""
                    }.joinToString(",", "[", "]")
                    webView.evaluateJavascript("if (typeof updateParkings === 'function') updateParkings(true, $parkingsJson);", null)
                } else {
                    webView.evaluateJavascript("if (typeof updateParkings === 'function') updateParkings(false, []);", null)
                }

                // Aktualizace sdílených kol
                if (showBikes) {
                    val bikesJson = bikes.take(80).map { b ->
                        val cleanName = b.name.replace("\"", "").replace("'", "")
                        val cleanProvider = b.provider.replace("\"", "").replace("'", "")
                        """{"name":"$cleanName","provider":"$cleanProvider","lat":${b.lat},"lng":${b.lon},"free":${b.freeBikes}}"""
                    }.joinToString(",", "[", "]")
                    webView.evaluateJavascript("if (typeof updateBikes === 'function') updateBikes(true, $bikesJson);", null)
                } else {
                    webView.evaluateJavascript("if (typeof updateBikes === 'function') updateBikes(false, []);", null)
                }
            }
        }
    )
}