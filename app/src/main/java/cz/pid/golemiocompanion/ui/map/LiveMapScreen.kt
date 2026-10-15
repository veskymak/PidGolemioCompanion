package cz.pid.golemiocompanion.ui.map

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

/**
 * Světlý čistý mapový podklad ve stylu aplikace PID Lítačka (CartoDB Voyager) s licencovaným API klíčem.
 */
private fun createCartoTileSource(apiKey: String): OnlineTileSourceBase {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    uiState: TripUiState,
    labSettings: LabSettings,
    onRouteFilterChanged: (String) -> Unit,
    onSelectStopFromMap: (PidStop) -> Unit,
    onRequestGpsLocation: () -> Unit,
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

    // Vrstva zastávek: ve stylu Lítačky vypnuta ve výchozím stavu a vykresluje se pouze při dostatečném přiblížení
    var showStops by remember { mutableStateOf(false) }
    var showParking by remember { mutableStateOf(false) }
    var showBikes by remember { mutableStateOf(false) }
    var useClassicOsmTiles by remember { mutableStateOf(false) }
    var selectedVehicle by remember { mutableStateOf<LiveVehiclePosition?>(null) }

    // Dialog / Bottom Sheet filtrů přesně podle PID Lítačky
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedFilterTab by remember { mutableStateOf(LitackaFilterTab.MHD) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Kritická inicializace osmdroid User-Agent před vytvořením MapView
    remember(context) {
        Configuration.getInstance().userAgentValue = "${context.packageName}/2.0"
        true
    }

    val mapView = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTileSource(createCartoTileSource(labSettings.cartoApiKey))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(14.5)
            // Výchozí střed: aktuálně vybraná zastávka
            controller.setCenter(
                GeoPoint(uiState.selectedStop.latitude, uiState.selectedStop.longitude)
            )
        }
    }

    // Napojení MapView na životní cyklus (bez onResume() osmdroid nestahuje dlaždice!)
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
        }
    }

    // Přepínání stylu mapy (Lítačka Světlá s CARTO klíčem vs. Klasická OSM)
    LaunchedEffect(useClassicOsmTiles, labSettings.cartoApiKey) {
        mapView.setTileSource(
            if (useClassicOsmTiles) TileSourceFactory.MAPNIK else createCartoTileSource(labSettings.cartoApiKey)
        )
        mapView.invalidate()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Samotná mapa přes celou obrazovku
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()

                // 1. Vrstva zastávek (pouze pokud je zapnutá A mapa je dostatečně přiblížená, aby nezasahovala do polí)
                if (showStops && map.zoomLevelDouble >= 13.5) {
                    PidStopDirectory.allStops.forEach { stop ->
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

                // 5. Vrstva živých vozidel ve stylu PID Lítačka (směrové šipky + tečka zpoždění)
                val filteredVehicles = uiState.liveVehicles.filter { v ->
                    val typeMatches = when (v.vehicleType) {
                        VehicleType.BUS -> showBuses
                        VehicleType.TROLLEYBUS -> showTrolleybuses
                        VehicleType.TRAM -> showTrams
                        VehicleType.TRAIN -> showTrains
                        VehicleType.METRO -> showMetro
                        VehicleType.FERRY -> showFerries
                    }
                    val routeMatches = if (uiState.mapRouteFilter.isBlank()) {
                        true
                    } else {
                        v.routeShortName.contains(uiState.mapRouteFilter.trim(), ignoreCase = true)
                    }
                    typeMatches && routeMatches
                }

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

        // Horní lišta ve stylu PID Lítačka (fialová hlavička s ikonou Tune pro filtry)
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
                            // Tlačítko hledání linky
                            IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Hledat linku",
                                    tint = if (isSearchExpanded || uiState.mapRouteFilter.isNotEmpty()) Color(0xFFD8B4FE) else Color.White
                                )
                            }

                            // Tlačítko filtrů přesně jako v Lítačce
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
                        RegionJumpChip("Žďár / MH") {
                            mapView.controller.animateTo(GeoPoint(50.5350, 15.0250), 13.5, 600L)
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

                    // Vyhledávací pole linky (zobrazí se po klepnutí na lupu nebo pokud je zadaný filtr)
                    if (isSearchExpanded || uiState.mapRouteFilter.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = uiState.mapRouteFilter,
                            onValueChange = onRouteFilterChanged,
                            placeholder = {
                                Text(
                                    "Vyhledávejte číslo linky (např. 315, 715, 720)…",
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

            // Rychlé tlačítka filtrů na mapě (s možností rozkliknutí plného menu)
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
                item {
                    Surface(
                        onClick = { showFilterSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF281147),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Filtry",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Levé spodní tlačítko pro přepnutí podkladu (Lítačka Carto vs. Klasická OSM)
        Surface(
            onClick = { useClassicOsmTiles = !useClassicOsmTiles },
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, bottom = 96.dp)
        ) {
            Text(
                text = if (useClassicOsmTiles) "🗺️ Styl: Klasik OSM" else "🗺️ Styl: Lítačka",
                color = Color(0xFF281147),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        // Pravé spodní plovoucí tlačítko "Moje poloha"
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

        // Detail vybraného vozidla při klepnutí na štítek na mapě
        selectedVehicle?.let { v ->
            Surface(
                color = Color(0xFF151C2C),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 96.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = when (v.vehicleType) {
                                    VehicleType.TRAM -> Color(0xFFC8102E)
                                    VehicleType.TRAIN -> Color(0xFF2B3990)
                                    else -> Color(0xFF0078A8)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = v.routeShortName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "→ ${v.headsign}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { selectedVehicle = null }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Zavřít",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (v.isInactive) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Vozidlo má neaktivní tracker. Poloha nemusí být v reálném čase.",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        val delayTxt = if (v.delaySeconds <= 0) "Včas (+0 s)" else "+${v.delaySeconds / 60} min (${v.delaySeconds} s)"
                        val delayColor = if (v.delaySeconds <= 60) Color(0xFF4ADE80) else Color(0xFFFBBF24)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = delayColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Zpoždění: $delayTxt",
                                    color = delayColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "• Rychlost ${v.speedKmh} km/h • Směr ${v.bearing.roundToInt()}°",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⏱️ Dojezd na příští zastávky: počítán v reálném čase podle GPS",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onRouteFilterChanged(v.routeShortName)
                            selectedVehicle = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Filtrovat jen linku ${v.routeShortName} na mapě",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Plnohodnotný filtrační list přesně podle aplikace PID Lítačka (Screenshot)
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
                    // Horní fialový pruh dialogu
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

                            // Záložky MHD, PRODEJ, PARKOVÁNÍ, SHARING přesně podle Lítačky
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

                    // Obsah konkrétní vybrané záložky
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        when (selectedFilterTab) {
                            LitackaFilterTab.MHD -> {
                                // Sekce Zastávky
                                Text(
                                    text = "Zastávky",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LitackaSwitchRow(
                                    title = "Zobrazit zastávky",
                                    subtitle = "Zobrazí zastávky při přiblížení mapy",
                                    checked = showStops,
                                    onCheckedChange = { showStops = it }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))

                                // Sekce Dopravní prostředky
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

                                // Sekce Linky veřejné dopravy
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
private fun createLitackaVehicleDrawable(
    context: Context,
    routeShortName: String,
    bearing: Float,
    delaySeconds: Int,
    vehicleType: VehicleType,
    showExtendedDelay: Boolean
): BitmapDrawable {
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
        else -> 0xFF0078A8.toInt() // PID Lítačka autobusová modrá
    }

    // 1. Směrový zobáček (natočený podle azimutu jízdy)
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

    // 4. Barevná tečka zpoždění v rohu štítku (jako v Lítačce)
    val dotColor = when {
        delaySeconds <= 60 -> 0xFF22C55E.toInt() // Zelená (včas)
        delaySeconds <= 180 -> 0xFFF59E0B.toInt() // Oranžová (1-3 min)
        else -> 0xFFEF4444.toInt() // Červená (> 3 min)
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

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Vykreslí decentní kulatou ikonu zastávky ve stylu PID Lítačka.
 * Velikost 18dp, neruší mapový podklad a zobrazuje se pouze při přiblížení.
 */
private fun createLitackaStopDrawable(context: Context, isTrain: Boolean): BitmapDrawable {
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