package cz.pid.golemiocompanion.ui.map

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import cz.pid.golemiocompanion.BuildConfig
import cz.pid.golemiocompanion.domain.model.LiveVehicleMarker
import cz.pid.golemiocompanion.ui.components.liquidGlass
import cz.pid.golemiocompanion.ui.trip.TripViewModel
import kotlinx.coroutines.launch

enum class MapDisplayMode(val label: String) {
    GOOGLE_WEB("Google Web"),
    GOOGLE_SDK("Google SDK"),
    RADAR("120Hz Radar")
}

@Composable
fun LiveMapScreen(
    viewModel: TripViewModel,
    modifier: Modifier = Modifier
) {
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

    Box(modifier = modifier.fillMaxSize()) {
        when (displayMode) {
            MapDisplayMode.GOOGLE_WEB -> {
                WebGoogleMap(
                    apiKey = effectiveMapsKey,
                    vehicles = uiState.liveVehicles,
                    centerLat = webCenterLat,
                    centerLon = webCenterLon,
                    zoom = webZoom
                )
            }
            MapDisplayMode.GOOGLE_SDK -> {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
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

                // Výběr režimu mapy (Google Web s podporou Demo Key, Google SDK, 120Hz Radar)
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

                // Rychlé skoky: Praha vs. Mladá Boleslav vs. Vycentrovat všechna vozidla
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

                    if (uiState.liveVehicles.isNotEmpty()) {
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
                                text = "Pro webový Demo Key zvolte režim „Google Web“, nebo vložte klíč v Nastavení.",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                        TextButton(
                            onClick = { displayMode = MapDisplayMode.GOOGLE_WEB },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                        ) {
                            Text("Google Web", fontWeight = FontWeight.Bold)
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

        // Dynamická projekce relativně k aktuálnímu středu (Praha / Mladá Boleslav / vlastní pozice)
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
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isReady by remember { mutableStateOf(false) }

    val hasKey = apiKey.isNotBlank() && !apiKey.contains("YOUR_GOOGLE_MAPS_API_KEY")

    val html = remember(apiKey) {
        if (hasKey) {
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <style>
                    html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0f172a; }
                </style>
                <script src="https://maps.googleapis.com/maps/api/js?key=${apiKey}&callback=initMap" async defer></script>
                <script>
                    var map;
                    var markers = {};
                    function initMap() {
                        map = new google.maps.Map(document.getElementById('map'), {
                            center: { lat: $centerLat, lng: $centerLon },
                            zoom: ${zoom.toInt()},
                            mapTypeControl: false,
                            streetViewControl: false,
                            fullscreenControl: false,
                            styles: [
                                { "elementType": "geometry", "stylers": [{ "color": "#1e293b" }] },
                                { "elementType": "labels.text.stroke", "stylers": [{ "color": "#0f172a" }] },
                                { "elementType": "labels.text.fill", "stylers": [{ "color": "#94a3b8" }] },
                                { "featureType": "road", "elementType": "geometry", "stylers": [{ "color": "#334155" }] },
                                { "featureType": "road.highway", "elementType": "geometry", "stylers": [{ "color": "#475569" }] },
                                { "featureType": "transit", "elementType": "geometry", "stylers": [{ "color": "#1e293b" }] },
                                { "featureType": "water", "elementType": "geometry", "stylers": [{ "color": "#0f172a" }] }
                            ]
                        });
                    }
                    function updateVehicles(list) {
                        if (!map) return;
                        var currentIds = {};
                        for (var i = 0; i < list.length; i++) {
                            var v = list[i];
                            currentIds[v.id] = true;
                            if (markers[v.id]) {
                                markers[v.id].setPosition({ lat: v.lat, lng: v.lng });
                            } else {
                                markers[v.id] = new google.maps.Marker({
                                    position: { lat: v.lat, lng: v.lng },
                                    map: map,
                                    title: "Linka " + v.line + " -> " + v.headsign,
                                    label: {
                                        text: v.line,
                                        color: "#ffffff",
                                        fontWeight: "bold",
                                        fontSize: "11px"
                                    }
                                });
                            }
                        }
                        for (var id in markers) {
                            if (!currentIds[id]) {
                                markers[id].setMap(null);
                                delete markers[id];
                            }
                        }
                    }
                    function panToCoords(lat, lng, z) {
                        if (map) {
                            map.panTo({ lat: lat, lng: lng });
                            if (z) map.setZoom(z);
                        }
                    }
                </script>
            </head>
            <body>
                <div id="map"></div>
            </body>
            </html>
            """.trimIndent()
        } else {
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0f172a; }
                    .bus-badge {
                        background: #2563eb;
                        color: #ffffff;
                        border: 2px solid white;
                        border-radius: 8px;
                        padding: 2px 5px;
                        font-weight: bold;
                        font-size: 11px;
                        text-align: center;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', { zoomControl: false }).setView([$centerLat, $centerLon], ${zoom.toInt()});
                    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);
                    var markers = {};
                    function updateVehicles(list) {
                        var currentIds = {};
                        for (var i = 0; i < list.length; i++) {
                            var v = list[i];
                            currentIds[v.id] = true;
                            var icon = L.divIcon({
                                className: 'bus-icon',
                                html: '<div class="bus-badge">' + v.line + '</div>',
                                iconSize: [36, 20]
                            });
                            if (markers[v.id]) {
                                markers[v.id].setLatLng([v.lat, v.lng]);
                            } else {
                                markers[v.id] = L.marker([v.lat, v.lng], { icon: icon }).addTo(map)
                                    .bindPopup('<b>Linka ' + v.line + '</b><br>' + v.headsign + '<br>Zpoždění: ' + v.delay + ' s');
                            }
                        }
                        for (var id in markers) {
                            if (!currentIds[id]) {
                                map.removeLayer(markers[id]);
                                delete markers[id];
                            }
                        }
                    }
                    function panToCoords(lat, lng, z) {
                        map.setView([lat, lng], z || 13);
                    }
                </script>
            </body>
            </html>
            """.trimIndent()
        }
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
                loadDataWithBaseURL("https://maps.googleapis.com", html, "text/html", "UTF-8", null)
                webViewInstance = this
            }
        },
        update = { webView ->
            if (isReady) {
                val vehiclesJson = vehicles.take(75).map { v ->
                    """{"id":"${v.tripId.replace("\"", "")}","line":"${v.routeShortName}","headsign":"${v.headsign.replace("\"", "").replace("'", "")}","lat":${v.lat},"lng":${v.lon},"delay":${v.delaySeconds}}"""
                }.joinToString(",", "[", "]")
                webView.evaluateJavascript("if (typeof updateVehicles === 'function') updateVehicles($vehiclesJson);", null)
            }
        }
    )
}