package cz.pid.golemiocompanion.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
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
import cz.pid.golemiocompanion.domain.model.*
import cz.pid.golemiocompanion.ui.components.liquidGlass
import cz.pid.golemiocompanion.ui.trip.TripViewModel
import kotlinx.coroutines.launch

enum class MapDisplayMode(val label: String) {
    OPEN_STREET_MAP("Mapa (OSM)"),
    RADAR("120Hz Radar"),
    GOOGLE_SDK("Google SDK")
}

@Composable
fun LiveMapScreen(
    viewModel: TripViewModel,
    modifier: Modifier = Modifier,
    onNavigateToLab: () -> Unit = {}
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
        mutableStateOf(MapDisplayMode.OPEN_STREET_MAP)
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

    val animateCameraIfGoogleMap: (LatLng, Float) -> Unit = { latLng, zoom ->
        if (displayMode == MapDisplayMode.GOOGLE_SDK) {
            scope.launch {
                runCatching {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
                }
            }
        }
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
                            animateCameraIfGoogleMap(latLng, 15f)
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
                            animateCameraIfGoogleMap(latLng, 15.5f)
                        } else {
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                if (lastLoc != null) {
                                    val latLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                                    userLocation = latLng
                                    webCenterLat = lastLoc.latitude
                                    webCenterLon = lastLoc.longitude
                                    webZoom = 15.5f
                                    animateCameraIfGoogleMap(latLng, 15.5f)
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
            MapDisplayMode.OPEN_STREET_MAP -> {
                WebOsmMap(
                    vehicles = uiState.liveVehicles,
                    userLat = userLocation?.latitude,
                    userLon = userLocation?.longitude,
                    showParkings = uiState.showParkingsLayer,
                    parkings = uiState.parkingsPR,
                    showBikes = uiState.showBikesLayer,
                    bikes = uiState.mapSharedBikes,
                    centerLat = webCenterLat,
                    centerLon = webCenterLon,
                    zoom = webZoom,
                    onVehicleClick = viewModel::selectVehicleForDetail
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
                            !vehicle.isTracking -> BitmapDescriptorFactory.HUE_VIOLET
                            vehicle.delaySeconds <= 30 -> BitmapDescriptorFactory.HUE_AZURE
                            vehicle.delaySeconds <= 120 -> BitmapDescriptorFactory.HUE_YELLOW
                            else -> BitmapDescriptorFactory.HUE_RED
                        }
                        Marker(
                            state = MarkerState(position = LatLng(vehicle.lat, vehicle.lon)),
                            title = "Linka ${vehicle.routeShortName} → ${vehicle.headsign}",
                            snippet = buildString {
                                if (!vehicle.isTracking) {
                                    append("⚠️ GPS tracker vypnutý (neznámé zpoždění)")
                                } else {
                                    append("Zpoždění: ${vehicle.delaySeconds} s • ${vehicle.speedKmh.toInt()} km/h")
                                    if (vehicle.isDeadReckoningEstimated) append(" [DEAD RECKONING]")
                                    if (vehicle.isFromRawInputGateway) append(" [RAW STREAM]")
                                }
                            },
                            icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                            rotation = vehicle.bearing,
                            onClick = {
                                viewModel.selectVehicleForDetail(vehicle.tripId)
                                true
                            }
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
                    centerTarget = if (displayMode == MapDisplayMode.GOOGLE_SDK) cameraPositionState.position.target else LatLng(webCenterLat, webCenterLon),
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

                // Výběr režimu mapy (Mapa OSM, 120Hz Radar, Google SDK)
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
                                        modifier = Modifier.size(15.dp)
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
                                animateCameraIfGoogleMap(pragueCenter, 12.5f)
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
                                animateCameraIfGoogleMap(mbCenter, 13.5f)
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
                                    if (displayMode == MapDisplayMode.GOOGLE_SDK) {
                                        val bounds = LatLngBounds(
                                            LatLng(minLat, minLon),
                                            LatLng(maxLat, maxLon)
                                        )
                                        scope.launch {
                                            runCatching {
                                                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                                            }
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
                                text = "Nativní Google SDK vyžaduje Maps SDK pro Android klíč",
                                color = Color(0xFFFDE68A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Režimy „Mapa (OSM)“ a „120Hz Radar“ fungují zdarma a bez omezení.",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                        TextButton(
                            onClick = { displayMode = MapDisplayMode.OPEN_STREET_MAP },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                        ) {
                            Text("Mapa (OSM)", fontWeight = FontWeight.Bold)
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
                        webCenterLat = vehicle.lat
                        webCenterLon = vehicle.lon
                        webZoom = 15.5f
                        animateCameraIfGoogleMap(LatLng(vehicle.lat, vehicle.lon), 15.5f)
                        viewModel.selectVehicleForDetail(vehicle.tripId)
                    }
                )
            }
        }

        // Výzva k zadání klíče – zobrazí se POUZE pokud uživatel klíč opravdu nemá zadaný
        if (labSettings.golemioApiKey.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(cornerRadius = 24.dp, tintColor = Color(0xFFF59E0B), surfaceAlphaTop = 0.35f)
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "Zadejte svůj Golemio API klíč",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Pro zobrazení živých autobusů PID na mapě a jejich reálného zpoždění je potřeba bezplatný API klíč. Demo vozidla jsou vypnuta, aby vás nemátla.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onNavigateToLab,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Zadat klíč v Laboratoři", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Detail rozkliknutého vozidla s příjezdem na příští zastávky in real time
        AnimatedVisibility(
            visible = uiState.selectedVehicle != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 76.dp)
        ) {
            uiState.selectedVehicle?.let { vehicle ->
                VehicleLiveDetailBottomSheet(
                    vehicle = vehicle,
                    upcomingStops = uiState.selectedVehicleUpcomingStops,
                    isLoadingStops = uiState.isLoadingVehicleDetail,
                    onDismiss = viewModel::dismissVehicleDetail,
                    onStartCountdownTracking = {
                        val dummyConnection = StopConnection(
                            tripId = vehicle.tripId,
                            routeShortName = vehicle.routeShortName,
                            routeType = 3,
                            headsign = vehicle.headsign,
                            stopName = vehicle.nextStopName ?: "Zastávka na trase",
                            platformCode = "-",
                            stopLat = vehicle.lat,
                            stopLon = vehicle.lon,
                            scheduledDepartureEpochMs = vehicle.nextStopArrivalEpochMs ?: System.currentTimeMillis(),
                            predictedDepartureEpochMs = (vehicle.nextStopArrivalEpochMs ?: System.currentTimeMillis()) + (vehicle.delaySeconds * 1000L),
                            scheduledArrivalDestinationEpochMs = System.currentTimeMillis() + (20 * 60_000L),
                            predictedArrivalDestinationEpochMs = System.currentTimeMillis() + (20 * 60_000L) + (vehicle.delaySeconds * 1000L),
                            destinationName = vehicle.headsign,
                            officialDelaySeconds = vehicle.delaySeconds,
                            isAirConditioned = vehicle.isAirConditioned ?: true,
                            isWheelchairAccessible = vehicle.isWheelchairAccessible ?: true
                        )
                        viewModel.startTrackingConnection(dummyConnection)
                        viewModel.dismissVehicleDetail()
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
private fun WebOsmMap(
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
    modifier: Modifier = Modifier,
    onVehicleClick: (String) -> Unit = {}
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
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css" />
            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js"></script>
            <style>
                html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0b1120; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                .leaflet-tile { filter: brightness(0.7) invert(1) contrast(2.5) hue-rotate(200deg) saturate(0.35); }
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
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.6);
                    white-space: nowrap;
                    cursor: pointer;
                }
                .bus-green { background: #16a34a; }
                .bus-yellow { background: #d97706; }
                .bus-red { background: #dc2626; }
                .bus-grey { background: #475569; border-color: #94a3b8; }
                
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
                    background: rgba(15, 23, 42, 0.95);
                    color: white;
                    border: 1px solid rgba(255, 255, 255, 0.25);
                    backdrop-filter: blur(12px);
                    border-radius: 14px;
                    font-size: 12px;
                }
                .leaflet-popup-tip {
                    background: rgba(15, 23, 42, 0.95);
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                function startMap() {
                    if (typeof L === 'undefined') {
                        setTimeout(startMap, 100);
                        return;
                    }
                    window.map = L.map('map', { zoomControl: false, attributionControl: false })
                        .setView([$centerLat, $centerLon], ${zoom.toInt()});
                    
                    // CARTO Voyager tiles - bezplatné a 100% spolehlivé pro mobilní WebViews
                    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                        subdomains: ['a', 'b', 'c', 'd'],
                        maxZoom: 19
                    }).addTo(window.map);

                    setTimeout(function() {
                        if (window.map) window.map.invalidateSize();
                    }, 250);
                    window.addEventListener('resize', function() {
                        if (window.map) window.map.invalidateSize();
                    });

                    window.markers = {};
                    window.parkingsLayer = L.layerGroup().addTo(window.map);
                    window.bikesLayer = L.layerGroup().addTo(window.map);
                    window.userMarker = null;

                    if (window._pendingUserLoc) updateUserLocation(window._pendingUserLoc[0], window._pendingUserLoc[1]);
                    if (window._pendingVehicles) updateVehicles(window._pendingVehicles);
                    if (window._pendingParkings) updateParkings(window._pendingParkings[0], window._pendingParkings[1]);
                    if (window._pendingBikes) updateBikes(window._pendingBikes[0], window._pendingBikes[1]);
                }

                window.updateUserLocation = function(lat, lng) {
                    if (!window.map) { window._pendingUserLoc = [lat, lng]; return; }
                    if (lat == null || lng == null) {
                        if (window.userMarker) {
                            window.map.removeLayer(window.userMarker);
                            window.userMarker = null;
                        }
                        return;
                    }
                    if (window.userMarker) {
                        window.userMarker.setLatLng([lat, lng]);
                    } else {
                        var icon = L.divIcon({
                            className: 'user-marker',
                            html: '<div class="user-pulse"></div><div class="user-dot"></div>',
                            iconSize: [24, 24],
                            iconAnchor: [12, 12]
                        });
                        window.userMarker = L.marker([lat, lng], { icon: icon, zIndexOffset: 2000 }).addTo(window.map)
                            .bindPopup('<b>Vaše aktuální GPS poloha</b>');
                    }
                };

                window.updateVehicles = function(list) {
                    if (!window.map) { window._pendingVehicles = list; return; }
                    var currentIds = {};
                    for (var i = 0; i < list.length; i++) {
                        var v = list[i];
                        currentIds[v.id] = true;
                        var isTracked = v.tracking !== false;
                        var colorClass = !isTracked ? 'bus-grey' : (v.delay <= 30 ? 'bus-green' : (v.delay <= 120 ? 'bus-yellow' : 'bus-red'));
                        var labelText = (!isTracked ? '⚠️ ' : '') + v.line;
                        var iconHtml = '<div class="bus-marker ' + colorClass + '">' + labelText + '</div>';
                        var icon = L.divIcon({
                            className: 'leaflet-bus-icon',
                            html: iconHtml,
                            iconSize: [42, 22],
                            iconAnchor: [21, 11]
                        });

                        var marker = window.markers[v.id];
                        if (marker) {
                            marker.setLatLng([v.lat, v.lng]);
                            marker.setIcon(icon);
                        } else {
                            marker = L.marker([v.lat, v.lng], { icon: icon }).addTo(window.map);
                            (function(tripId) {
                                marker.on('click', function() {
                                    if (window.AndroidBridge && window.AndroidBridge.onVehicleClicked) {
                                        window.AndroidBridge.onVehicleClicked(tripId);
                                    }
                                });
                            })(v.id);
                            window.markers[v.id] = marker;
                        }
                    }
                    for (var id in window.markers) {
                        if (!currentIds[id]) {
                            window.map.removeLayer(window.markers[id]);
                            delete window.markers[id];
                        }
                    }
                };

                window.updateParkings = function(show, list) {
                    if (!window.map) { window._pendingParkings = [show, list]; return; }
                    window.parkingsLayer.clearLayers();
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
                            .addTo(window.parkingsLayer)
                            .bindPopup('<b>P+R ' + p.name + '</b><br>Volno: ' + p.free + ' / ' + p.total);
                    }
                };

                window.updateBikes = function(show, list) {
                    if (!window.map) { window._pendingBikes = [show, list]; return; }
                    window.bikesLayer.clearLayers();
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
                            .addTo(window.bikesLayer)
                            .bindPopup('<b>' + b.provider + ': ' + b.name + '</b><br>Volných kol: ' + b.free);
                    }
                };

                window.panToCoords = function(lat, lng, z) {
                    if (window.map) window.map.flyTo([lat, lng], z || 14, { duration: 0.8 });
                };

                startMap();
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
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.databaseEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 PidGolemioCompanion/1.0"
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onVehicleClicked(tripId: String) {
                        onVehicleClick(tripId)
                    }
                }, "AndroidBridge")
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isReady = true
                    }
                }
                loadDataWithBaseURL("https://cdnjs.cloudflare.com", html, "text/html", "UTF-8", null)
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
                val vehiclesJson = vehicles.take(200).map { v ->
                    val cleanLine = v.routeShortName.replace("\"", "").replace("'", "")
                    val cleanHeadsign = v.headsign.replace("\"", "").replace("'", "")
                    """{"id":"${v.tripId.replace("\"", "")}","line":"$cleanLine","headsign":"$cleanHeadsign","lat":${v.lat},"lng":${v.lon},"delay":${v.delaySeconds},"speed":${v.speedKmh},"tracking":${v.isTracking}}"""
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

@Composable
private fun VehicleLiveDetailBottomSheet(
    vehicle: LiveVehicleMarker,
    upcomingStops: List<VehicleUpcomingStop>,
    isLoadingStops: Boolean,
    onDismiss: () -> Unit,
    onStartCountdownTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF20F172A),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    cornerRadius = 24.dp,
                    tintColor = Color(0xFF38BDF8),
                    surfaceAlphaTop = 0.35f,
                    surfaceAlphaBottom = 0.18f
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Hlavička s číslem linky, cílem a křížkem zavřít
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (vehicle.routeShortName.startsWith("S") || vehicle.routeShortName.startsWith("R")) {
                            Color(0xFF2563EB)
                        } else {
                            Color(0xFFDC2626)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = vehicle.routeShortName,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "směr ${vehicle.headsign}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        if (vehicle.vehicleRegistrationNumber != null && vehicle.vehicleRegistrationNumber > 0) {
                            Text(
                                text = "Ev. č. vozu: ${vehicle.vehicleRegistrationNumber}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Zavřít detail",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Stav telemetrie a GPS trackeru
            if (!vehicle.isTracking) {
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "⚠️", fontSize = 20.sp)
                        Column {
                            Text(
                                text = "Vypnutý GPS tracker vozidla",
                                color = Color(0xFFFCA5A5),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Vozidlo momentálně nevysílá telemetrii do dispečinku PID. Aplikace si data nevymýšlí – přesné zpoždění a živá poloha nejsou dostupné.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                // Živé telemetrické údaje: Zpoždění, Rychlost, Výbava
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val delayColor = when {
                        vehicle.delaySeconds <= 30 -> Color(0xFF4ADE80)
                        vehicle.delaySeconds <= 120 -> Color(0xFFFACC15)
                        else -> Color(0xFFF87171)
                    }
                    val delayText = when {
                        vehicle.delaySeconds <= 0 -> "Včas (0 s)"
                        vehicle.delaySeconds < 60 -> "+${vehicle.delaySeconds} s"
                        else -> "+${vehicle.delaySeconds / 60} min (${vehicle.delaySeconds} s)"
                    }
                    Surface(
                        color = delayColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, delayColor.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "Zpoždění", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                            Text(text = delayText, color = delayColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Surface(
                        color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "Rychlost", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                            Text(text = "${vehicle.speedKmh.toInt()} km/h", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (vehicle.isAirConditioned == true || vehicle.isWheelchairAccessible == true) {
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "Výbava", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (vehicle.isAirConditioned == true) Text(text = "❄️", fontSize = 13.sp)
                                    if (vehicle.isWheelchairAccessible == true) Text(text = "♿", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Sekce: Příští zastávky v reálném čase
            Text(
                text = "⏱️ Příjezd na příští zastávky (in real time):",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            if (isLoadingStops) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Zjišťuji časy příjezdu ze systému PID...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            } else if (upcomingStops.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    upcomingStops.take(6).forEach { stop ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (stop.isDestination) "🏁" else "🚏",
                                    fontSize = 14.sp
                                )
                                Column {
                                    Text(
                                        text = stop.stopName,
                                        color = Color.White,
                                        fontWeight = if (stop.isDestination) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "JŘ: ${stop.scheduledTime} • Očekáváno: ${stop.predictedTime}",
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Surface(
                                color = if (stop.minutesUntilArrival <= 3) Color(0xFF4ADE80).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (stop.minutesUntilArrival == 0L) "přijíždí" else "za ${stop.minutesUntilArrival} min",
                                    color = if (stop.minutesUntilArrival <= 3) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            } else if (!vehicle.isTracking) {
                Text(
                    text = "Časy na příštích zastávkách nelze živě zobrazit, protože vozidlo nemá zapnutý GPS tracker.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏁 Cílová stanice: ${vehicle.headsign}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "dle jízdního řádu",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp
                    )
                }
            }

            Button(
                onClick = onStartCountdownTracking,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Spustit odpočet pro tuto linku", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}