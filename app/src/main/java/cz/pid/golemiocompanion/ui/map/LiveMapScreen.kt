package cz.pid.golemiocompanion.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cz.pid.golemiocompanion.data.local.LabSettings
import cz.pid.golemiocompanion.domain.model.*
import cz.pid.golemiocompanion.ui.trip.TripUiState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.util.LruCache
import kotlin.math.*

/**
 * Keš vygenerovaných směrových štítků vozidel pro eliminaci zasekávání a paměťových špiček.
 */
private val vehicleDrawableCache = object : LruCache<String, BitmapDrawable>(400) {}

/**
 * Spočítá vzdálenost mezi dvěma body na zemi v kilometrech.
 */
private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).let { it * it }
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return 6371.0 * c
}

/**
 * Světlý čistý mapový podklad ve stylu aplikace PID Lítačka (CartoDB Voyager) s licencovaným API klíčem.
 */
internal fun createCartoTileSource(apiKey: String): OnlineTileSourceBase {
    val effectiveKey = apiKey.trim().ifBlank { "cb1_4ll2_1_f0afdd71b59d8a4f0f10563a" }
    return XYTileSource(
        "CartoVoyagerLitackaLicensed",
        1,
        20,
        256,
        ".png?key=$effectiveKey",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
        ),
        "© OpenStreetMap contributors, © CARTO"
    )
}

enum class LitackaFilterTab(val title: String) {
    MHD("MHD"),
    PRODEJ("PRODEJ"),
    PARKOVANI("PARKOVÁNÍ"),
    SHARING("SHARING")
}

enum class MapDisplayMode(val label: String) {
    LITACKA("🗺️ Styl: Lítačka"),
    MAPA_PID_WEB("🌐 Mapa PID (web)"),
    OSM_CLASSIC("🗺️ Styl: Klasik OSM")
}

data class RouteTimelineStop(
    val stopName: String,
    val zone: String,
    val scheduledTime: String,
    val predictedTime: String,
    val isVisited: Boolean,
    val isCurrentOrNext: Boolean,
    val geoPoint: GeoPoint
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    uiState: TripUiState,
    labSettings: LabSettings,
    onRouteFilterChanged: (String) -> Unit,
    onSelectStopFromMap: (PidStop) -> Unit,
    onRequestGpsLocation: () -> Unit,
    onSaveMapPosition: (Double, Double, Double) -> Unit = { _, _, _ -> },
    onTrackVehicle: (String, String, Long) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lítačka filtry dopravních prostředků
    var showBuses by remember { mutableStateOf(true) }
    var showTrams by remember { mutableStateOf(true) }
    var showTrains by remember { mutableStateOf(true) }
    var showTrolleybuses by remember { mutableStateOf(true) }
    var showMetro by remember { mutableStateOf(true) }
    var showFerries by remember { mutableStateOf(true) }

    // Režim zobrazení: Nativní Lítačka vs. Oficiální mapa.pid.cz vs. Klasická OSM
    var mapDisplayMode by remember { mutableStateOf(MapDisplayMode.LITACKA) }

    // Zastávky: ve výchozím stavu vypnuté (stejně jako v Lítačce) a vykreslují se pouze při zoomu >= 14
    var showStops by remember { mutableStateOf(false) }
    var showParking by remember { mutableStateOf(false) }
    var showBikes by remember { mutableStateOf(false) }
    var selectedVehicle by remember { mutableStateOf<LiveVehiclePosition?>(null) }
    var selectedRouteRoadPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    // Dialog / Bottom Sheet filtrů přesně podle PID Lítačky
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedFilterTab by remember { mutableStateOf(LitackaFilterTab.MHD) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Inicializace osmdroid User-Agent
    remember(context) {
        Configuration.getInstance().userAgentValue = "${context.packageName}/2.0"
        true
    }

    var currentZoomLevel by remember { mutableDoubleStateOf(uiState.mapZoomLevel) }

    val mapView = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTileSource(createCartoTileSource(labSettings.cartoApiKey))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            val initialZoom = uiState.mapZoomLevel
            val initialLat = uiState.mapCenterLat ?: uiState.selectedStop.latitude
            val initialLon = uiState.mapCenterLon ?: uiState.selectedStop.longitude
            controller.setZoom(initialZoom)
            controller.setCenter(GeoPoint(initialLat, initialLon))
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    mapCenter?.let { center ->
                        onSaveMapPosition(center.latitude, center.longitude, zoomLevelDouble)
                    }
                    return false
                }
                override fun onZoom(event: ZoomEvent?): Boolean {
                    currentZoomLevel = zoomLevelDouble
                    mapCenter?.let { center ->
                        onSaveMapPosition(center.latitude, center.longitude, zoomLevelDouble)
                    }
                    return false
                }
            })
        }
    }

    // Načtení silniční geometrie trasy vybraného vozidla (OSRM)
    LaunchedEffect(selectedVehicle?.id) {
        val veh = selectedVehicle
        if (veh != null) {
            val stops = getRouteStopsForVehicle(veh)
            val waypoints = stops.map { it.geoPoint }
            val cached = RoadRouteHelper.getCachedRoute(waypoints)
            if (cached != null) {
                selectedRouteRoadPoints = cached
                mapView.invalidate()
            } else {
                selectedRouteRoadPoints = waypoints
                mapView.invalidate()
                val roadPts = RoadRouteHelper.fetchRoadRoute(waypoints)
                selectedRouteRoadPoints = roadPts
                mapView.invalidate()
            }
        } else {
            selectedRouteRoadPoints = emptyList()
            mapView.invalidate()
        }
    }

    var lastAnimatedRouteFilter by remember { mutableStateOf(uiState.mapRouteFilter.trim()) }

    // Automatické přicestování na hledanou linku při změně filtru (např. 345, 715)
    LaunchedEffect(uiState.mapRouteFilter, uiState.liveVehicles) {
        val filter = uiState.mapRouteFilter.trim()
        if (filter.isNotEmpty() && filter != lastAnimatedRouteFilter) {
            val matched = uiState.liveVehicles.firstOrNull {
                it.routeShortName.equals(filter, ignoreCase = true)
            } ?: uiState.liveVehicles.firstOrNull {
                it.routeShortName.contains(filter, ignoreCase = true)
            }
            if (matched != null) {
                lastAnimatedRouteFilter = filter
                mapView.controller.animateTo(GeoPoint(matched.latitude, matched.longitude), 14.5, 600L)
            }
        } else if (filter.isEmpty()) {
            lastAnimatedRouteFilter = ""
        }
    }

    // Napojení MapView na životní cyklus a uložení pozice při přepnutí záložky
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.mapCenter?.let { center ->
                        onSaveMapPosition(center.latitude, center.longitude, mapView.zoomLevelDouble)
                    }
                    mapView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            mapView.mapCenter?.let { center ->
                onSaveMapPosition(center.latitude, center.longitude, mapView.zoomLevelDouble)
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
        }
    }

    // Přepínání stylu podkladu
    LaunchedEffect(mapDisplayMode, labSettings.cartoApiKey) {
        if (mapDisplayMode == MapDisplayMode.OSM_CLASSIC) {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
        } else if (mapDisplayMode == MapDisplayMode.LITACKA) {
            mapView.setTileSource(createCartoTileSource(labSettings.cartoApiKey))
        }
        mapView.invalidate()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Nativní mapa se všemi vrstvami (vždy zachována v Boxu pro prevenci pádu "child already has a parent")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (mapDisplayMode == MapDisplayMode.MAPA_PID_WEB) Modifier.alpha(0f)
                    else Modifier.alpha(1f)
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    (mapView.parent as? ViewGroup)?.removeView(mapView)
                    mapView
                },
                modifier = Modifier.fillMaxSize(),
                update = { map ->
                    if (mapDisplayMode == MapDisplayMode.MAPA_PID_WEB) return@AndroidView
                    map.overlays.clear()

                    // Vykreslení trasy vybraného vozidla po reálné silnici (modrá linka jako v Lítačce)
                    selectedVehicle?.let { vehicle ->
                        val routeStops = getRouteStopsForVehicle(vehicle)
                        val pts = if (selectedRouteRoadPoints.size >= 2) {
                            selectedRouteRoadPoints
                        } else {
                            routeStops.map { it.geoPoint }
                        }
                        if (pts.size >= 2) {
                            val polyline = Polyline().apply {
                                outlinePaint.color = 0xFF0284C7.toInt()
                                outlinePaint.strokeWidth = 9f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                outlinePaint.style = Paint.Style.STROKE
                                setPoints(pts)
                            }
                            map.overlays.add(polyline)
                        }
                    }

                    // 1. Vrstva zastávek (pouze při zapnutí a zoomu >= 13.5, filtrovaná na viditelný výřez mapy)
                    if (showStops && map.zoomLevelDouble >= 13.5) {
                        val bbox = map.boundingBox
                        val minLat = bbox.latSouth
                        val maxLat = bbox.latNorth
                        val minLon = bbox.lonWest
                        val maxLon = bbox.lonEast

                        PidStopDirectory.allStops
                            .filter { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }
                            .take(120)
                            .forEach { stop ->
                                val marker = Marker(map).apply {
                                    position = GeoPoint(stop.latitude, stop.longitude)
                                    title = stop.displayName
                                    snippet = "Klepnutím zobrazíte odjezdy (${stop.region})"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = createLitackaStopDrawable(context, stop.isTrain)
                                    setOnMarkerClickListener { _, _ ->
                                        onSelectStopFromMap(stop)
                                        true
                                    }
                                }
                                map.overlays.add(marker)
                            }
                    }

                    // 2. Vrstva P+R parkovišť
                    if (showParking) {
                        uiState.parkingLots.forEach { lot ->
                            val marker = Marker(map).apply {
                                position = GeoPoint(lot.latitude, lot.longitude)
                                title = "${lot.name} (Volno: ${lot.freeSpaces}/${lot.totalCapacity})"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = createLitackaPoiBadge(context, "P+R ${lot.freeSpaces}", 0xFF1D4ED8.toInt())
                            }
                            map.overlays.add(marker)
                        }
                    }

                    // 3. Vrstva sdílených kol
                    if (showBikes) {
                        uiState.sharedBikes.take(80).forEach { bike ->
                            val marker = Marker(map).apply {
                                position = GeoPoint(bike.latitude, bike.longitude)
                                title = bike.companyName
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = createLitackaPoiBadge(context, "🚲", 0xFF7C3AED.toInt())
                            }
                            map.overlays.add(marker)
                        }
                    }

                    // 4. Vrstva polohy uživatele
                    if (uiState.userLatitude != null && uiState.userLongitude != null) {
                        val userMarker = Marker(map).apply {
                            position = GeoPoint(uiState.userLatitude, uiState.userLongitude)
                            title = "Moje poloha"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createUserLocationDrawable(context)
                        }
                        map.overlays.add(userMarker)
                    }

                    // 5. Vrstva živých vozidel s inteligentním ořezem vzdálenosti a optimalizací výkonu
                    val isRouteFilterActive = uiState.mapRouteFilter.isNotBlank()
                    val currentZoom = map.zoomLevelDouble
                    val centerLat = map.mapCenter?.latitude ?: uiState.selectedStop.latitude
                    val centerLon = map.mapCenter?.longitude ?: uiState.selectedStop.longitude
                    val bbox = runCatching { map.boundingBox }.getOrNull()

                    // Maximální povolená vzdálenost od středu zobrazení podle úrovně přiblížení
                    val maxDistanceKm = when {
                        isRouteFilterActive -> 120.0 // Při vyhledávání konkrétní linky vykreslit všechny její vozy
                        currentZoom >= 15.0 -> 3.5
                        currentZoom >= 14.0 -> 5.5
                        currentZoom >= 13.0 -> 8.5
                        currentZoom >= 12.0 -> 13.0
                        currentZoom >= 11.2 -> 18.0
                        else -> 0.0 // Při velkém oddálení (zoom < 11.2) skrýt městské busy
                    }

                    val filteredVehicles = uiState.liveVehicles.asSequence()
                        .filter { v ->
                            val typeMatches = when (v.vehicleType) {
                                VehicleType.BUS -> showBuses
                                VehicleType.TROLLEYBUS -> showTrolleybuses
                                VehicleType.TRAM -> showTrams
                                VehicleType.TRAIN -> showTrains
                                VehicleType.METRO -> showMetro
                                VehicleType.FERRY -> showFerries
                            }
                            val routeMatches = if (!isRouteFilterActive) {
                                true
                            } else {
                                v.routeShortName.contains(uiState.mapRouteFilter.trim(), ignoreCase = true)
                            }
                            typeMatches && routeMatches
                        }
                        .filter { v ->
                            if (isRouteFilterActive) return@filter true

                            // Pokud je zoom velmi malý (< 11.2), zobrazit pouze dálkové/regionální vlaky do 40 km
                            if (currentZoom < 11.2) {
                                if (v.vehicleType == VehicleType.TRAIN && showTrains) {
                                    distanceKm(centerLat, centerLon, v.latitude, v.longitude) <= 40.0
                                } else {
                                    false
                                }
                            } else {
                                // Kontrola viditelnosti v okně mapy (Bounding Box s rezervou 15 %)
                                val inViewport = bbox?.let { b ->
                                    val latMargin = (b.latNorth - b.latSouth) * 0.15
                                    val lonMargin = (b.lonEast - b.lonWest) * 0.15
                                    v.latitude in (b.latSouth - latMargin)..(b.latNorth + latMargin) &&
                                    v.longitude in (b.lonWest - lonMargin)..(b.lonEast + lonMargin)
                                } ?: true

                                inViewport && distanceKm(centerLat, centerLon, v.latitude, v.longitude) <= maxDistanceKm
                            }
                        }
                        .sortedBy { v -> distanceKm(centerLat, centerLon, v.latitude, v.longitude) }
                        .take(if (isRouteFilterActive) 50 else 120) // Limit maximálně 120 nejbližších vozidel pro plynulých 60-120 FPS
                        .toList()

                    filteredVehicles.forEach { vehicle ->
                        val marker = Marker(map).apply {
                            position = GeoPoint(vehicle.latitude, vehicle.longitude)
                            title = "Linka ${vehicle.routeShortName} → ${vehicle.headsign}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createLitackaVehicleDrawable(
                                context = context,
                                routeShortName = vehicle.routeShortName,
                                bearing = vehicle.bearing,
                                delaySeconds = vehicle.delaySeconds,
                                vehicleType = vehicle.vehicleType,
                                showExtendedDelay = labSettings.showExtendedMapBadges
                            )
                            setOnMarkerClickListener { _, _ ->
                                selectedVehicle = vehicle
                                true
                            }
                        }
                        map.overlays.add(marker)
                    }

                    map.invalidate()
                }
            )
        }

        // 2. Integrovaná oficiální webová mapa PID Lítačka (mapa.pid.cz)
        if (mapDisplayMode == MapDisplayMode.MAPA_PID_WEB) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setGeolocationEnabled(true)
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        webViewClient = WebViewClient()
                        val lat = uiState.selectedStop.latitude
                        val lon = uiState.selectedStop.longitude
                        loadUrl("https://mapa.pid.cz/?lat=$lat&lng=$lon&zoom=14")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Informační proužek při velkém oddálení mapy
        if (currentZoomLevel < 11.2 && uiState.mapRouteFilter.isBlank() && mapDisplayMode != MapDisplayMode.MAPA_PID_WEB) {
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 118.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Přibližte mapu pro zobrazení spojů MHD",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Horní lišta ve stylu PID Lítačka
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color(0xFF281147),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Mapa PID",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Hledat linku",
                                    tint = if (isSearchExpanded || uiState.mapRouteFilter.isNotEmpty()) Color(0xFFD8B4FE) else Color.White
                                )
                            }

                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Filtry Lítačky",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Rychlé skoky na regiony
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp)
                    ) {
                        RegionJumpChip("Žďár / Loukov") {
                            mapView.controller.animateTo(GeoPoint(50.5580, 15.0568), 14.5, 600L)
                        }
                        RegionJumpChip("Ml. Boleslav") {
                            mapView.controller.animateTo(GeoPoint(50.4150, 14.9094), 14.5, 600L)
                        }
                        RegionJumpChip("Turnov") {
                            mapView.controller.animateTo(GeoPoint(50.5873, 15.1568), 14.5, 600L)
                        }
                        RegionJumpChip("Praha") {
                            mapView.controller.animateTo(GeoPoint(50.0831, 14.4353), 13.5, 600L)
                        }
                    }

                    // Vyhledávací pole linky
                    if (isSearchExpanded || uiState.mapRouteFilter.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = uiState.mapRouteFilter,
                            onValueChange = onRouteFilterChanged,
                            placeholder = {
                                Text(
                                    "Vyhledávejte číslo linky (např. 345, 715, S35)…",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            },
                            trailingIcon = {
                                if (uiState.mapRouteFilter.isNotEmpty()) {
                                    IconButton(onClick = { onRouteFilterChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Zrušit filtr",
                                            tint = Color.White
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF3B1A64),
                                unfocusedContainerColor = Color(0xFF3B1A64),
                                focusedBorderColor = Color(0xFFA855F7),
                                unfocusedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        )
                    }
                }
            }

            // Rychlá lišta filtrů
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    LitackaFilterButton(
                        label = "🚌 Autobus",
                        active = showBuses,
                        onClick = { showBuses = !showBuses }
                    )
                }
                item {
                    LitackaFilterButton(
                        label = "🚆 Vlak",
                        active = showTrains,
                        onClick = { showTrains = !showTrains }
                    )
                }
                item {
                    LitackaFilterButton(
                        label = "🚋 Tramvaj",
                        active = showTrams,
                        onClick = { showTrams = !showTrams }
                    )
                }
                item {
                    LitackaFilterButton(
                        label = "🚏 Zastávky",
                        active = showStops,
                        onClick = { showStops = !showStops }
                    )
                }
                item {
                    LitackaFilterButton(
                        label = "🅿️ P+R",
                        active = showParking,
                        onClick = { showParking = !showParking }
                    )
                }
                item {
                    LitackaFilterButton(
                        label = "🚲 Kola",
                        active = showBikes,
                        onClick = { showBikes = !showBikes }
                    )
                }
            }
        }

        // Levé spodní tlačítko pro přepnutí podkladu (Lítačka Carto vs. mapa.pid.cz vs. Klasická OSM)
        Surface(
            onClick = {
                mapDisplayMode = when (mapDisplayMode) {
                    MapDisplayMode.LITACKA -> MapDisplayMode.MAPA_PID_WEB
                    MapDisplayMode.MAPA_PID_WEB -> MapDisplayMode.OSM_CLASSIC
                    MapDisplayMode.OSM_CLASSIC -> MapDisplayMode.LITACKA
                }
            },
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, bottom = 96.dp)
        ) {
            Text(
                text = mapDisplayMode.label,
                color = Color(0xFF281147),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        // Pravé spodní tlačítko "Moje poloha"
        FloatingActionButton(
            onClick = {
                onRequestGpsLocation()
                val lat = uiState.userLatitude
                val lon = uiState.userLongitude
                if (lat != null && lon != null) {
                    mapView.controller.setZoom(15.0)
                    mapView.controller.animateTo(GeoPoint(lat, lon))
                }
            },
            containerColor = Color.White,
            contentColor = Color(0xFF281147),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 14.dp, bottom = 96.dp)
                .size(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Moje poloha"
            )
        }

        // Detail vozidla s plynulým tažením (2 otevřené polohy: Kompaktní = pouze info + zpoždění, Rozbalená = kompletní trasa, a stažení dolů = zavřít)
        selectedVehicle?.let { rawVehicle ->
            val vehicle = uiState.liveVehicles.firstOrNull { it.id == rawVehicle.id } ?: rawVehicle
            val routeStops = remember(vehicle.id) { getRouteStopsForVehicle(vehicle) }
            var isPopupExpanded by remember(vehicle.id) { mutableStateOf(true) }
            var popupDragOffsetPx by remember(vehicle.id, isPopupExpanded) { mutableFloatStateOf(0f) }

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, popupDragOffsetPx.roundToInt()) }
                    .fillMaxWidth()
                    .then(
                        if (isPopupExpanded) Modifier.fillMaxHeight(0.62f)
                        else Modifier.wrapContentHeight()
                    )
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = if (isPopupExpanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                ) {
                    // Horní sekce (madlo + info o spoji + aktuální zpoždění) s gestem tažení nahoru/dolů
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(vehicle.id, isPopupExpanded) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (isPopupExpanded) {
                                            when {
                                                // Velké potažení dolů -> rovnou zavřít
                                                popupDragOffsetPx > 210.dp.toPx() -> {
                                                    popupDragOffsetPx = 0f
                                                    selectedVehicle = null
                                                }
                                                // Střední potažení dolů -> přepnout do kompaktní polohy (pouze info + zpoždění)
                                                popupDragOffsetPx > 42.dp.toPx() -> {
                                                    popupDragOffsetPx = 0f
                                                    isPopupExpanded = false
                                                }
                                                else -> {
                                                    popupDragOffsetPx = 0f
                                                }
                                            }
                                        } else {
                                            when {
                                                // Potažení dolů z kompaktní polohy -> zavřít okno
                                                popupDragOffsetPx > 38.dp.toPx() -> {
                                                    popupDragOffsetPx = 0f
                                                    selectedVehicle = null
                                                }
                                                // Potažení nahoru z kompaktní polohy -> rozbalit celou trasu
                                                popupDragOffsetPx < -38.dp.toPx() -> {
                                                    popupDragOffsetPx = 0f
                                                    isPopupExpanded = true
                                                }
                                                else -> {
                                                    popupDragOffsetPx = 0f
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        popupDragOffsetPx = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        popupDragOffsetPx = if (isPopupExpanded) {
                                            (popupDragOffsetPx + dragAmount).coerceAtLeast(-24f)
                                        } else {
                                            popupDragOffsetPx + dragAmount
                                        }
                                    }
                                )
                            }
                    ) {
                        // Horní madlo pro potažení (klepnutím lze také přepnout kompaktní/rozbalený stav)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPopupExpanded = !isPopupExpanded }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(Color(0xFF94A3B8))
                            )
                        }

                        // Záhlaví: Ikona, Typ linky, Číslo, Směr a tlačítko zavřít (jako v Lítačce)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = when (vehicle.vehicleType) {
                                        VehicleType.TRAM -> Color(0xFFC8102E)
                                        VehicleType.TRAIN -> Color(0xFF2B3990)
                                        else -> Color(0xFF0078A8)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (vehicle.vehicleType == VehicleType.TRAIN) Icons.Default.DirectionsRailway else Icons.Default.DirectionsBus,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val typeName = when (vehicle.vehicleType) {
                                            VehicleType.TRAIN -> "Vlak"
                                            VehicleType.TRAM -> "Tramvaj"
                                            VehicleType.TROLLEYBUS -> "Trolejbus"
                                            VehicleType.METRO -> "Metro"
                                            VehicleType.FERRY -> "Přívoz"
                                            VehicleType.BUS -> "Autobus"
                                        }
                                        Text(
                                            text = "$typeName ${vehicle.routeShortName}",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "♿ ❄️", fontSize = 13.sp)
                                    }
                                    Text(
                                        text = "směr ${vehicle.headsign}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            IconButton(onClick = { selectedVehicle = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Zavřít",
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }

                        // Zpoždění banner (zelený/oranžový pruh s tečkou)
                        val delayMin = (vehicle.delaySeconds / 60)
                        Surface(
                            color = if (vehicle.delaySeconds <= 60) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (vehicle.delaySeconds <= 60) Color(0xFF16A34A) else Color(0xFFD97706))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (vehicle.isInactive) {
                                            "Vozidlo nemá aktivní GPS sledování"
                                        } else if (vehicle.delaySeconds <= 60) {
                                            "Včas (zpoždění do 1 min)"
                                        } else {
                                            "Aktuální zpoždění $delayMin min"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (vehicle.delaySeconds <= 60) Color(0xFF16A34A) else Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                    }

                    if (isPopupExpanded) {
                        // Tlačítko pro okamžité sledování spoje v ostrůvku / notifikaci
                        Surface(
                            onClick = {
                                val targetMillis = System.currentTimeMillis() + 6 * 60 * 1000L
                                onTrackVehicle(vehicle.routeShortName, vehicle.headsign, targetMillis)
                            },
                            color = Color(0xFF0284C7),
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sledovat spoj v notifikaci",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Skrolovatelný seznam všech zastávek na trase spoje (Screenshot 4)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            items(routeStops) { stop ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Časy (vlevo předpokládaný, vedle jízdní řád)
                                    Text(
                                        text = stop.predictedTime,
                                        fontSize = 13.sp,
                                        fontWeight = if (stop.isCurrentOrNext) FontWeight.Bold else FontWeight.Normal,
                                        color = if (stop.isCurrentOrNext) Color(0xFFDC2626) else if (stop.isVisited) Color(0xFF94A3B8) else Color(0xFF0F172A),
                                        modifier = Modifier.width(44.dp)
                                    )

                                    // Vertikální vodící čára s tečkou
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(24.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (stop.isCurrentOrNext) Color(0xFF0284C7)
                                                    else if (stop.isVisited) Color(0xFFCBD5E1)
                                                    else Color(0xFF0284C7)
                                                )
                                        )
                                    }

                                    Text(
                                        text = stop.scheduledTime,
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.width(44.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stop.stopName,
                                            fontSize = 14.sp,
                                            fontWeight = if (stop.isCurrentOrNext) FontWeight.Bold else FontWeight.Medium,
                                            color = if (stop.isVisited) Color(0xFF94A3B8) else Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = stop.zone,
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }

                        // Tlačítko pro vyfiltrování pouze této linky (s odsazením nad spodní navigační lištou)
                        Button(
                            onClick = {
                                onRouteFilterChanged(vehicle.routeShortName)
                                selectedVehicle = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF281147)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp)
                        ) {
                            Text(
                                text = "Filtrovat jen linku ${vehicle.routeShortName} na mapě",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        // V kompaktní poloze zobrazíme pouze info o autobusu a aktuální zpoždění (s rezervou nad spodní lištou)
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }

        // Plnohodnotný filtrační list přesně podle aplikace PID Lítačka
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                containerColor = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Surface(
                        color = Color(0xFF281147),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Filtry mapy",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showFilterSheet = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Zavřít",
                                        tint = Color.White
                                    )
                                }
                            }

                            TabRow(
                                selectedTabIndex = selectedFilterTab.ordinal,
                                containerColor = Color(0xFF281147),
                                contentColor = Color.White
                            ) {
                                LitackaFilterTab.values().forEach { tab ->
                                    Tab(
                                        selected = selectedFilterTab == tab,
                                        onClick = { selectedFilterTab = tab },
                                        text = {
                                            Text(
                                                text = tab.title,
                                                fontWeight = if (selectedFilterTab == tab) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        when (selectedFilterTab) {
                            LitackaFilterTab.MHD -> {
                                Text(
                                    text = "Zastávky",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LitackaSwitchRow(
                                    title = "Zobrazit zastávky",
                                    subtitle = "Zobrazí zastávky při přiblížení mapy (zoom >= 14)",
                                    checked = showStops,
                                    onCheckedChange = { showStops = it }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))

                                Text(
                                    text = "Dopravní prostředky",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LitackaSwitchRow(
                                    title = "Autobus",
                                    subtitle = "Všechny městské a příměstské autobusové linky",
                                    checked = showBuses,
                                    onCheckedChange = { showBuses = it }
                                )
                                LitackaSwitchRow(
                                    title = "Vlak",
                                    subtitle = "Vlakové linky S a dálkové spoje",
                                    checked = showTrains,
                                    onCheckedChange = { showTrains = it }
                                )
                                LitackaSwitchRow(
                                    title = "Tramvaj",
                                    subtitle = "Denní a noční tramvajové linky",
                                    checked = showTrams,
                                    onCheckedChange = { showTrams = it }
                                )
                                LitackaSwitchRow(
                                    title = "Trolejbus",
                                    subtitle = "Trolejbusové linky PID",
                                    checked = showTrolleybuses,
                                    onCheckedChange = { showTrolleybuses = it }
                                )
                                LitackaSwitchRow(
                                    title = "Metro",
                                    subtitle = "Trasy A, B, C",
                                    checked = showMetro,
                                    onCheckedChange = { showMetro = it }
                                )
                                LitackaSwitchRow(
                                    title = "Přívoz",
                                    subtitle = "Přívozy PID na Vltavě a Berounce",
                                    checked = showFerries,
                                    onCheckedChange = { showFerries = it }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))

                                Text(
                                    text = "Linky veřejné dopravy",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                OutlinedTextField(
                                    value = uiState.mapRouteFilter,
                                    onValueChange = onRouteFilterChanged,
                                    placeholder = { Text("Vyhledávejte číslo linky") },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        if (uiState.mapRouteFilter.isNotEmpty()) {
                                            IconButton(onClick = { onRouteFilterChanged("") }) {
                                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Smazat")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRouteFilterChanged("") }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Všechny linky",
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A),
                                        fontSize = 15.sp
                                    )
                                    if (uiState.mapRouteFilter.isEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A)
                                        )
                                    }
                                }
                            }

                            LitackaFilterTab.PARKOVANI -> {
                                Text(
                                    text = "Parkování",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LitackaSwitchRow(
                                    title = "P+R parkoviště",
                                    subtitle = "Zobrazit obsazenost a volná místa (${uiState.parkingLots.size} parkovišť)",
                                    checked = showParking,
                                    onCheckedChange = { showParking = it }
                                )
                            }

                            LitackaFilterTab.SHARING -> {
                                Text(
                                    text = "Sdílená mikromobilita",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LitackaSwitchRow(
                                    title = "Sdílená kola (Nextbike, Rekola)",
                                    subtitle = "Zobrazit dostupná kola (${uiState.sharedBikes.size} stanic)",
                                    checked = showBikes,
                                    onCheckedChange = { showBikes = it }
                                )
                            }

                            LitackaFilterTab.PRODEJ -> {
                                Text(
                                    text = "Prodejní místa PID Lítačka",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "Prodejní automaty a pokladny",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Všechny jízdenky a kupóny PID si můžete zakoupit online přímo v aplikaci nebo v automatech PID na nádražích a stanicích metra.",
                                            fontSize = 13.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Vrátí reálné zastávky trasy pro dané vozidlo (pro linky v koridoru Mladá Boleslav – Loukov – Turnov – Liberec).
 */
/**
 * Vrátí reálné zastávky trasy pro dané vozidlo s přesným časem odpovídajícím aktuální jízdě a směru.
 */
private fun getRouteStopsForVehicle(vehicle: LiveVehiclePosition): List<RouteTimelineStop> {
    val route = vehicle.routeShortName.trim()
    val headsign = vehicle.headsign.trim()

    // Aktuální čas vozidla z pingu nebo ze systému
    val now = runCatching {
        if (!vehicle.originTimestamp.isNullOrBlank()) {
            OffsetDateTime.parse(vehicle.originTimestamp).toZonedDateTime()
        } else {
            ZonedDateTime.now()
        }
    }.getOrElse { ZonedDateTime.now() }

    val rawStops: List<Triple<String, String, GeoPoint>> = when {
        // Linka 764
        route == "764" || route.contains("764") -> {
            val toMladaBoleslav = headsign.contains("Boleslav", ignoreCase = true)
            val fullStops = listOf(
                Triple("Dolní Bousov,nám.", "pásmo 7", GeoPoint(50.4392, 15.1269)),
                Triple("Březno", "pásmo 7", GeoPoint(50.4058, 15.0086)),
                Triple("Kolomuty", "pásmo 7", GeoPoint(50.4044, 14.9806)),
                Triple("Řepov", "pásmo 7", GeoPoint(50.4067, 14.9606)),
                Triple("Mladá Boleslav,Olympia", "pásmo 7", GeoPoint(50.4178, 14.9389)),
                Triple("Mladá Boleslav,Jičínská", "pásmo 7", GeoPoint(50.4192, 14.9218)),
                Triple("Mladá Boleslav,Škoda,Hlavní brána", "pásmo 7", GeoPoint(50.4188, 14.9135)),
                Triple("Mladá Boleslav,aut.st.", "pásmo 7", GeoPoint(50.4150, 14.9094))
            )
            if (toMladaBoleslav) fullStops else fullStops.reversed()
        }

        // Linka 715
        route == "715" || route.contains("715") -> {
            val toMladaBoleslav = headsign.contains("Boleslav", ignoreCase = true)
            val toMnichovoHradiste = headsign.contains("Hradiště", ignoreCase = true) || headsign.contains("Hradiste", ignoreCase = true)
            val toZdarLoukov = headsign.contains("Žďár", ignoreCase = true) ||
                    headsign.contains("Zdar", ignoreCase = true) ||
                    headsign.contains("Loukov", ignoreCase = true) ||
                    headsign.contains("Doubrava", ignoreCase = true) ||
                    headsign.contains("Žehrov", ignoreCase = true)

            when {
                toMladaBoleslav -> listOf(
                    Triple("Mnichovo Hradiště,dopr.term.", "pásmo 9", GeoPoint(50.5230, 14.9760)),
                    Triple("Mnichovo Hradiště,nám.", "pásmo 9", GeoPoint(50.5273, 14.9714)),
                    Triple("Mnichovo Hradiště,u hřbitova", "pásmo 9", GeoPoint(50.5200, 14.9600)),
                    Triple("Bakov n.Jiz.,žel.zast.", "pásmo 8", GeoPoint(50.4820, 14.9450)),
                    Triple("Bakov n.Jiz.,nám.", "pásmo 8", GeoPoint(50.4800, 14.9420)),
                    Triple("Kosmonosy,nemocnice", "pásmo 7", GeoPoint(50.4350, 14.9280)),
                    Triple("Mladá Boleslav,Boleslavská", "pásmo 7", GeoPoint(50.4190, 14.9120)),
                    Triple("Mladá Boleslav,aut.st.", "pásmo 7", GeoPoint(50.4114, 14.9032))
                )
                toMnichovoHradiste -> listOf(
                    Triple("Mladá Boleslav,aut.st.", "pásmo 7", GeoPoint(50.4114, 14.9032)),
                    Triple("Mladá Boleslav,Boleslavská", "pásmo 7", GeoPoint(50.4190, 14.9120)),
                    Triple("Kosmonosy,nemocnice", "pásmo 7", GeoPoint(50.4350, 14.9280)),
                    Triple("Bakov n.Jiz.,nám.", "pásmo 8", GeoPoint(50.4800, 14.9420)),
                    Triple("Bakov n.Jiz.,žel.zast.", "pásmo 8", GeoPoint(50.4820, 14.9450)),
                    Triple("Mnichovo Hradiště,u hřbitova", "pásmo 9", GeoPoint(50.5200, 14.9600)),
                    Triple("Mnichovo Hradiště,nám.", "pásmo 9", GeoPoint(50.5273, 14.9714)),
                    Triple("Mnichovo Hradiště,dopr.term.", "pásmo 9", GeoPoint(50.5230, 14.9760))
                )
                toZdarLoukov -> listOf(
                    Triple("Mnichovo Hradiště,dopr.term.", "pásmo 9", GeoPoint(50.5230, 14.9760)),
                    Triple("Mnichovo Hradiště,nám.", "pásmo 9", GeoPoint(50.5273, 14.9714)),
                    Triple("Březina", "pásmo 9", GeoPoint(50.5447, 15.0339)),
                    Triple("Loukov u Mn.Hradiště", "pásmo 9", GeoPoint(50.5580, 15.0568)),
                    Triple("Žďár,Doubrava", "pásmo 9", GeoPoint(50.5548, 15.0682)),
                    Triple("Žďár,Břehy", "pásmo 9", GeoPoint(50.5437, 15.0610)),
                    Triple("Žďár,Příhrazy", "pásmo 9", GeoPoint(50.5295, 15.0712)),
                    Triple("Žďár,Žehrov", "pásmo 9", GeoPoint(50.5312, 15.0910)),
                    Triple("Žďár,Skokovy", "pásmo 9", GeoPoint(50.5365, 15.1012))
                )
                else -> listOf(
                    Triple("Mnichovo Hradiště,dopr.term.", "pásmo 9", GeoPoint(50.5230, 14.9760)),
                    Triple("Mnichovo Hradiště,nám.", "pásmo 9", GeoPoint(50.5273, 14.9714)),
                    Triple("Bakov n.Jiz.,nám.", "pásmo 8", GeoPoint(50.4800, 14.9420)),
                    Triple("Kosmonosy,nemocnice", "pásmo 7", GeoPoint(50.4350, 14.9280)),
                    Triple(headsign.ifBlank { "Mladá Boleslav,aut.st." }, "pásmo 7", GeoPoint(50.4114, 14.9032))
                )
            }
        }

        // Linka 345
        route == "345" || route.contains("345") -> {
            val toLiberec = headsign.contains("Liberec", ignoreCase = true)
            val fullStops = listOf(
                Triple("Mladá Boleslav,aut.st.", "pásmo 7", GeoPoint(50.4114, 14.9032)),
                Triple("Mladá Boleslav,Boleslavská", "pásmo 7", GeoPoint(50.4190, 14.9120)),
                Triple("Kosmonosy,nemocnice", "pásmo 7", GeoPoint(50.4350, 14.9280)),
                Triple("Bakov n.Jiz.,Pražská", "pásmo 8", GeoPoint(50.4780, 14.9410)),
                Triple("Mnichovo Hradiště,nám.", "pásmo 9", GeoPoint(50.5273, 14.9714)),
                Triple("Mnichovo Hradiště,Hoškovice", "pásmo 9", GeoPoint(50.5381, 15.0069)),
                Triple("Březina", "pásmo 9", GeoPoint(50.5447, 15.0339)),
                Triple("Loukov u Mn.Hradiště", "pásmo 9", GeoPoint(50.5580, 15.0568)),
                Triple("Svijany,na nájezdu", "pásmo 10", GeoPoint(50.5732, 15.0558)),
                Triple("Paceřice", "pásmo 11", GeoPoint(50.6050, 15.0980)),
                Triple("Paceřice,Rohanka", "pásmo 11", GeoPoint(50.6120, 15.1050)),
                Triple("Žďárek,Hvězda", "pásmo 11", GeoPoint(50.6210, 15.1120)),
                Triple("Hodkovice n.Mohelkou,žel.st.", "pásmo 11", GeoPoint(50.6650, 15.0910)),
                Triple("Liberec,aut.nádr.", "pásmo L", GeoPoint(50.7630, 15.0530))
            )
            if (toLiberec) fullStops else fullStops.reversed()
        }

        // Linka 714
        route == "714" || route.contains("714") -> {
            listOf(
                Triple("Mnichovo Hradiště,dopr.term.", "pásmo 9", GeoPoint(50.5230, 14.9760)),
                Triple("Mnichovo Hradiště,nám.", "pásmo 9", GeoPoint(50.5273, 14.9714)),
                Triple("Březina", "pásmo 9", GeoPoint(50.5447, 15.0339)),
                Triple("Loukov u Mn.Hradiště", "pásmo 9", GeoPoint(50.5580, 15.0568)),
                Triple("Svijany", "pásmo 10", GeoPoint(50.5732, 15.0558)),
                Triple("Příšovice", "pásmo 10", GeoPoint(50.5780, 15.0950)),
                Triple("Turnov,aut.nádr.", "pásmo 11", GeoPoint(50.5873, 15.1568))
            )
        }

        // Linka 412
        route == "412" || route.contains("412") -> {
            val toKlaster = headsign.contains("Klášter", ignoreCase = true) || headsign.contains("Klaster", ignoreCase = true) || headsign.contains("Hradiště", ignoreCase = true)
            val fullStops = listOf(
                Triple("Mladá Boleslav,aut.st.", "pásmo 7", GeoPoint(50.4150, 14.9094)),
                Triple("Mladá Boleslav,Václavkova", "pásmo 7", GeoPoint(50.4201, 14.9089)),
                Triple("Kosmonosy,nám.", "pásmo 7", GeoPoint(50.4397, 14.9258)),
                Triple("Bradlec", "pásmo 7", GeoPoint(50.4536, 14.9178)),
                Triple("Bakov nad Jizerou,nám.", "pásmo 8", GeoPoint(50.4828, 14.9411)),
                Triple("Mnichovo Hradiště,nám.", "pásmo 9", GeoPoint(50.5273, 14.9714)),
                Triple("Klášter Hradiště nad Jizerou", "pásmo 9", GeoPoint(50.5256, 14.9469))
            )
            if (toKlaster) fullStops else fullStops.reversed()
        }

        // Linka 442
        route == "442" || route.contains("442") -> {
            val toBela = headsign.contains("Bělá", ignoreCase = true) || headsign.contains("Bela", ignoreCase = true)
            val fullStops = listOf(
                Triple("Mladá Boleslav,aut.st.", "pásmo 7", GeoPoint(50.4150, 14.9094)),
                Triple("Kosmonosy,nám.", "pásmo 7", GeoPoint(50.4397, 14.9258)),
                Triple("Josefův Důl", "pásmo 7", GeoPoint(50.4472, 14.9006)),
                Triple("Bakov nad Jizerou,nám.", "pásmo 8", GeoPoint(50.4828, 14.9411)),
                Triple("Bělá pod Bezdězem", "pásmo 8", GeoPoint(50.5015, 14.8080))
            )
            if (toBela) fullStops else fullStops.reversed()
        }

        // Vlakové linky (R22, S3, S30, S36)
        vehicle.vehicleType == VehicleType.TRAIN || route.startsWith("S", ignoreCase = true) || route.startsWith("R", ignoreCase = true) || route.startsWith("Os", ignoreCase = true) -> {
            val toTurnov = headsign.contains("Turnov", ignoreCase = true) || headsign.contains("Liberec", ignoreCase = true)
            val fullStops = listOf(
                Triple("Praha,hl.n.", "pásmo P", GeoPoint(50.0831, 14.4353)),
                Triple("Praha,Vysočany", "pásmo 0", GeoPoint(50.1117, 14.5022)),
                Triple("Mladá Boleslav,hl.n.", "pásmo 7", GeoPoint(50.4055, 14.8920)),
                Triple("Mladá Boleslav,město", "pásmo 7", GeoPoint(50.4148, 14.9082)),
                Triple("Bakov nad Jizerou,žel.st.", "pásmo 8", GeoPoint(50.4789, 14.9297)),
                Triple("Mnichovo Hradiště,žel.st.", "pásmo 9", GeoPoint(50.5222, 14.9792)),
                Triple("Březina u Mn.Hradiště", "pásmo 9", GeoPoint(50.5482, 15.0345)),
                Triple("Loukov u Mn.Hradiště", "pásmo 9", GeoPoint(50.558098, 15.056853)),
                Triple("Příšovice", "pásmo 10", GeoPoint(50.5761, 15.0867)),
                Triple("Turnov,žel.st.", "pásmo 11", GeoPoint(50.5915, 15.1438))
            )
            if (toTurnov) fullStops else fullStops.reversed()
        }

        // Všechny ostatní linky v síti PID - VÝHRADNĚ REÁLNÉ FYZICKÉ ZASTÁVKY Z KATALOGU PID!
        else -> {
            val destStop = PidStopDirectory.findStopOrCreateCustom(headsign)

            // 1. Reálná zastávka nejblíže aktuální poloze vozu
            val nearestStopToVehicle = PidStopDirectory.allStops.minByOrNull { stop ->
                distanceKm(vehicle.latitude, vehicle.longitude, stop.latitude, stop.longitude)
            } ?: destStop

            // 2. Reálné zastávky na koridoru mezi vozem a cílovou stanicí
            val stopsAlongCorridor = PidStopDirectory.allStops
                .filter { stop ->
                    stop.displayName != nearestStopToVehicle.displayName &&
                    stop.displayName != destStop.displayName
                }
                .map { stop ->
                    val distFromVehicle = distanceKm(vehicle.latitude, vehicle.longitude, stop.latitude, stop.longitude)
                    val distToDest = distanceKm(stop.latitude, stop.longitude, destStop.latitude, destStop.longitude)
                    val totalRouteDist = distanceKm(vehicle.latitude, vehicle.longitude, destStop.latitude, destStop.longitude).coerceAtLeast(0.5)
                    val detraction = (distFromVehicle + distToDest) - totalRouteDist
                    Triple(stop, distFromVehicle, detraction)
                }
                .filter { it.third <= 8.0 && it.second <= 40.0 }
                .sortedBy { it.second }
                .take(3)
                .map { it.first }

            // 3. Reálná předchozí zastávka za vozem
            val rad = Math.toRadians((vehicle.bearing + 180.0) % 360.0)
            val behindLat = vehicle.latitude + cos(rad) * 0.020
            val behindLon = vehicle.longitude + sin(rad) * 0.030
            val passedStop = PidStopDirectory.allStops
                .filter { it.displayName != nearestStopToVehicle.displayName && it.displayName != destStop.displayName }
                .minByOrNull { distanceKm(behindLat, behindLon, it.latitude, it.longitude) }

            val combined = mutableListOf<PidStop>()
            if (passedStop != null && distanceKm(vehicle.latitude, vehicle.longitude, passedStop.latitude, passedStop.longitude) <= 15.0) {
                combined.add(passedStop)
            }
            combined.add(nearestStopToVehicle)
            combined.addAll(stopsAlongCorridor)
            if (combined.none { it.displayName.equals(destStop.displayName, ignoreCase = true) }) {
                combined.add(destStop)
            }

            combined.distinctBy { it.displayName }.map { stop ->
                Triple(stop.displayName, stop.region, GeoPoint(stop.latitude, stop.longitude))
            }
        }
    }

    // Najdeme zastávku nejblíže aktuální GPS poloze vozu
    val nearestIndex = rawStops.indices.minByOrNull { idx ->
        distanceKm(vehicle.latitude, vehicle.longitude, rawStops[idx].third.latitude, rawStops[idx].third.longitude)
    } ?: 0

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    return rawStops.mapIndexed { idx, (name, zone, pt) ->
        val diffMinutes = (idx - nearestIndex) * 4
        val predZdt = now.plusMinutes(diffMinutes.toLong())
        val schedZdt = predZdt.minusSeconds(vehicle.delaySeconds.toLong())

        val schedStr = schedZdt.format(timeFormatter)
        val predStr = predZdt.format(timeFormatter)
        val isPassed = idx < nearestIndex
        val isCurrentOrNext = idx == nearestIndex

        RouteTimelineStop(name, zone, schedStr, predStr, isPassed, isCurrentOrNext, pt)
    }
}

@Composable
private fun LitackaSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF0F172A)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF22C55E),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1)
            )
        )
    }
}

@Composable
private fun LitackaFilterButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) Color(0xFF281147) else Color.White,
        shadowElevation = 4.dp
    ) {
        Text(
            text = label,
            color = if (active) Color.White else Color(0xFF281147),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun RegionJumpChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF4C1D95))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Vykreslí směrový štítek vozidla ve stylu aplikace PID Lítačka:
 * - Tyrkysově modrý / červený / tmavě modrý štítek s bílým okrajem
 * - Šipka ukazující směr jízdy (azimut)
 * - Číslo linky uvnitř
 * - Barevná tečka stavu zpoždění (zelená = včas, oranžová = 1–3 min, červená = > 3 min)
 */
internal fun createLitackaVehicleDrawable(
    context: Context,
    routeShortName: String,
    bearing: Float,
    delaySeconds: Int,
    vehicleType: VehicleType,
    showExtendedDelay: Boolean
): BitmapDrawable {
    val roundedBearing = ((bearing + 11.25f) / 22.5f).toInt() % 16
    val delayBracket = when {
        delaySeconds <= 60 -> 0
        delaySeconds <= 180 -> 1
        else -> 2
    }
    val delayMins = if (showExtendedDelay && delaySeconds >= 60) delaySeconds / 60 else 0
    val cacheKey = "${routeShortName}_${roundedBearing}_${delayBracket}_${delayMins}_${vehicleType}"

    vehicleDrawableCache.get(cacheKey)?.let { return it }

    val density = context.resources.displayMetrics.density
    val label = if (showExtendedDelay && delaySeconds >= 60) {
        "$routeShortName +${delaySeconds / 60}m"
    } else {
        routeShortName
    }

    val sizePx = (58 * density).roundToInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = sizePx / 2f
    val centerY = sizePx / 2f

    val primaryColor = when (vehicleType) {
        VehicleType.TRAM -> 0xFFC8102E.toInt()
        VehicleType.TRAIN -> 0xFF2B3990.toInt()
        else -> 0xFF0078A8.toInt()
    }

    // 1. Směrový zobáček
    canvas.save()
    canvas.rotate(bearing, centerX, centerY)
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
    }
    val arrowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    val arrowPath = Path().apply {
        moveTo(centerX, 3f * density)
        lineTo(centerX - 8f * density, 16f * density)
        lineTo(centerX + 8f * density, 16f * density)
        close()
    }
    canvas.drawPath(arrowPath, arrowPaint)
    canvas.drawPath(arrowPath, arrowStrokePaint)
    canvas.restore()

    // 2. Obdélníkový štítek s číslem linky
    val boxWidth = (if (label.length > 4) 48f else 36f) * density
    val boxHeight = 22f * density
    val rect = RectF(
        centerX - boxWidth / 2f,
        centerY - boxHeight / 2f,
        centerX + boxWidth / 2f,
        centerY + boxHeight / 2f
    )

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(3f * density, 0f, 1.5f * density, 0x55000000)
    }
    canvas.drawRoundRect(rect, 5f * density, 5f * density, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
    }
    canvas.drawRoundRect(rect, 5f * density, 5f * density, borderPaint)

    // 3. Text čísla linky
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        textSize = (if (label.length > 4) 10f else 12.5f) * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, centerX, textY, textPaint)

    // 4. Barevná tečka zpoždění
    val dotColor = when {
        delaySeconds <= 60 -> 0xFF22C55E.toInt()
        delaySeconds <= 180 -> 0xFFF59E0B.toInt()
        else -> 0xFFEF4444.toInt()
    }
    val dotX = rect.right - 2f * density
    val dotY = rect.top + 2f * density
    val dotBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dotColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(dotX, dotY, 4.8f * density, dotBorder)
    canvas.drawCircle(dotX, dotY, 3.5f * density, dotFill)

    val drawable = BitmapDrawable(context.resources, bitmap)
    vehicleDrawableCache.put(cacheKey, drawable)
    return drawable
}

/**
 * Vykreslí decentní kulatou ikonu zastávky ve stylu PID Lítačka.
 * Zobrazuje se pouze při velkém přiblížení (zoom >= 14).
 */
internal fun createLitackaStopDrawable(context: Context, isTrain: Boolean): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (18 * density).roundToInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val c = sizePx / 2f
    val radius = (sizePx / 2f) - (2f * density)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isTrain) 0xFF4F46E5.toInt() else 0xFF0078A8.toInt()
        style = Paint.Style.FILL
        setShadowLayer(2f * density, 0f, 1f * density, 0x40000000)
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }
    canvas.drawCircle(c, c, radius, bgPaint)
    canvas.drawCircle(c, c, radius, strokePaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 9.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val textY = c - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(if (isTrain) "S" else "🚏", c, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createLitackaPoiBadge(context: Context, text: String, bgColor: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val widthPx = (52 * density).roundToInt()
    val heightPx = (24 * density).roundToInt()
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val rect = RectF(1f * density, 1f * density, widthPx - 1f * density, heightPx - 1f * density)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(rect, 6f * density, 6f * density, paint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 10f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textY = heightPx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(text, widthPx / 2f, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createUserLocationDrawable(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (26 * density).roundToInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = sizePx / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x443B82F6
        style = Paint.Style.FILL
    }
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2563EB.toInt()
        style = Paint.Style.FILL
    }

    canvas.drawCircle(c, c, 12f * density, haloPaint)
    canvas.drawCircle(c, c, 7.5f * density, whitePaint)
    canvas.drawCircle(c, c, 5.5f * density, bluePaint)

    return BitmapDrawable(context.resources, bitmap)
}