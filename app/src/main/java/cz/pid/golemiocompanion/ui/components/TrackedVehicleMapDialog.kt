package cz.pid.golemiocompanion.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.pid.golemiocompanion.domain.model.DepartureInfo
import cz.pid.golemiocompanion.domain.model.PidStop
import cz.pid.golemiocompanion.domain.model.VehicleTrackingTelemetry
import cz.pid.golemiocompanion.ui.map.createCartoTileSource
import cz.pid.golemiocompanion.ui.map.createLitackaStopDrawable
import cz.pid.golemiocompanion.ui.map.createLitackaVehicleDrawable
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import cz.pid.golemiocompanion.ui.map.RoadRouteHelper
import kotlin.math.roundToInt

@Composable
fun TrackedVehicleMapDialog(
    departure: DepartureInfo,
    stop: PidStop,
    telemetry: VehicleTrackingTelemetry?,
    cartoApiKey: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var roadRoutePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    val veh = telemetry?.vehicle
    LaunchedEffect(veh?.latitude, veh?.longitude, stop.latitude, stop.longitude) {
        if (veh != null) {
            val waypoints = listOf(
                GeoPoint(veh.latitude, veh.longitude),
                GeoPoint(stop.latitude, stop.longitude)
            )
            RoadRouteHelper.getCachedRoute(waypoints)?.let { cached ->
                roadRoutePoints = cached
                mapViewInstance?.invalidate()
            }
            val fetched = RoadRouteHelper.fetchRoadRoute(waypoints)
            roadRoutePoints = fetched
            mapViewInstance?.invalidate()
        } else {
            roadRoutePoints = emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Pozadí se ztmavením (kliknutí mimo okno zavře mapu)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Plovoucí okno v dolní části displeje – maximálně 50 % výšky obrazovky
            Column(
                modifier = Modifier
                    .offset { IntOffset(0, dragOffsetPx.coerceAtLeast(0f).roundToInt()) }
                    .fillMaxWidth()
                    .fillMaxHeight(0.50f)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Color(0xFF0F172A))
                    .clickable(enabled = false, onClick = {}) // Zamezí zavření při kliknutí dovnitř
                    .navigationBarsPadding()
            ) {
                // Horní sekce s táhlem a hlavičkou (podpora potažení dolů pro zavření)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetPx > 60.dp.toPx()) {
                                        onDismissRequest()
                                    } else {
                                        dragOffsetPx = 0f
                                    }
                                },
                                onDragCancel = { dragOffsetPx = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                                }
                            )
                        }
                ) {
                    // Lišta s táhlem pro přehlednost
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF475569))
                        )
                    }

                    // Kompaktní hlavička okna
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = Color(0xFF0284C7),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = departure.routeShortName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Linka ${departure.routeShortName} → ${departure.headsign}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Zastávka: ${stop.displayName}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF334155))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Zavřít",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Interaktivní mapa osmdroid (vyplňuje prostor mezi hlavičkou a telemetrií)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            Configuration.getInstance().load(
                                ctx,
                                ctx.getSharedPreferences("osm_telemetry_prefs", Context.MODE_PRIVATE)
                            )
                            Configuration.getInstance().userAgentValue = ctx.packageName

                            MapView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setMultiTouchControls(true)
                                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                                val effectiveTileSource = if (cartoApiKey.isNotBlank()) {
                                    createCartoTileSource(cartoApiKey)
                                } else {
                                    TileSourceFactory.MAPNIK
                                }
                                setTileSource(effectiveTileSource)

                                val stopPoint = GeoPoint(stop.latitude, stop.longitude)
                                controller.setZoom(16.5)
                                controller.setCenter(stopPoint)

                                mapViewInstance = this
                            }
                        },
                        update = { mapView ->
                            mapView.overlays.clear()

                            val stopGeo = GeoPoint(stop.latitude, stop.longitude)
                            val currentVeh = telemetry?.vehicle
                            if (currentVeh != null) {
                                val vehGeo = GeoPoint(currentVeh.latitude, currentVeh.longitude)

                                // Silniční spojnice mezi vozem a zastávkou (kopíruje reálné silnice)
                                val connectingLine = Polyline().apply {
                                    outlinePaint.color = AndroidColor.parseColor("#0284C7")
                                    outlinePaint.strokeWidth = 8f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                    outlinePaint.style = Paint.Style.STROKE
                                    val pts = if (roadRoutePoints.size >= 2) {
                                        roadRoutePoints
                                    } else {
                                        listOf(vehGeo, stopGeo)
                                    }
                                    setPoints(pts)
                                }
                                mapView.overlays.add(connectingLine)

                                val vehMarker = Marker(mapView).apply {
                                    position = vehGeo
                                    title = "Vůz linky ${departure.routeShortName}"
                                    snippet = "Rychlost: ${currentVeh.speedKmh} km/h • Zpoždění: ${currentVeh.delaySeconds} s"
                                    icon = createLitackaVehicleDrawable(
                                        context = context,
                                        routeShortName = departure.routeShortName,
                                        bearing = currentVeh.bearing,
                                        delaySeconds = currentVeh.delaySeconds,
                                        vehicleType = departure.vehicleType,
                                        showExtendedDelay = true
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                }
                                mapView.overlays.add(vehMarker)
                            }

                            val stopMarker = Marker(mapView).apply {
                                position = stopGeo
                                title = "Zastávka: ${stop.displayName}"
                                snippet = "Cílová stanice pro sledovaný odjezd"
                                icon = createLitackaStopDrawable(context, departure.vehicleType.name == "TRAIN")
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            mapView.overlays.add(stopMarker)

                            mapView.invalidate()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Plovoucí tlačítko na vycentrování
                    IconButton(
                        onClick = {
                            val mv = mapViewInstance ?: return@IconButton
                            val veh = telemetry?.vehicle
                            if (veh != null) {
                                val minLat = min(veh.latitude, stop.latitude) - 0.0010
                                val maxLat = max(veh.latitude, stop.latitude) + 0.0010
                                val minLon = min(veh.longitude, stop.longitude) - 0.0012
                                val maxLon = max(veh.longitude, stop.longitude) + 0.0012
                                mv.zoomToBoundingBox(BoundingBox(maxLat, maxLon, minLat, minLon), true)
                            } else {
                                mv.controller.animateTo(GeoPoint(stop.latitude, stop.longitude), 16.5, 400L)
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Vycentrovat na vůz a zastávku",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Kompaktní telemetrický proužek pod mapou (styl PID Lítačka)
                Surface(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (telemetry != null) {
                            // 4 mini ukazatele v jedné řadě
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TelemetryCompactPill(
                                    title = "Zpoždění",
                                    value = formatDelayLabel(telemetry.delaySeconds, true),
                                    valueColor = when {
                                        telemetry.delaySeconds <= 60 -> Color(0xFF4ADE80)
                                        telemetry.delaySeconds <= 180 -> Color(0xFFFBBF24)
                                        else -> Color(0xFFF87171)
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                TelemetryCompactPill(
                                    title = "Poloha před",
                                    value = "${telemetry.pingAgeSeconds}s",
                                    valueColor = Color(0xFF38BDF8),
                                    modifier = Modifier.weight(1f)
                                )

                                TelemetryCompactPill(
                                    title = "Vzdálenost",
                                    value = "${telemetry.distanceMeters} m",
                                    valueColor = Color.White,
                                    modifier = Modifier.weight(1f)
                                )

                                TelemetryCompactPill(
                                    title = "Rychlost",
                                    value = "${telemetry.speedKmh} km/h",
                                    valueColor = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Stavový řádek s informací o odjezdu a aplikované korekci
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = telemetry.formattedStatus,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                val estTimeFormatted = formatTimeFromEpoch(telemetry.calculatedDepartureEpochMillis)
                                Text(
                                    text = "Odjezd v $estTimeFormatted",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Text(
                                text = "📡 Čekám na poziční GPS ping z palubního počítače vozu PID…",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCompactPill(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
                maxLines = 1
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

private fun formatTimeFromEpoch(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.of("Europe/Prague"))
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}
