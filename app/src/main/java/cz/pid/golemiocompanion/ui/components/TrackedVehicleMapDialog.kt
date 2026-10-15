package cz.pid.golemiocompanion.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun TrackedVehicleMapDialog(
    departure: DepartureInfo,
    stop: PidStop,
    telemetry: VehicleTrackingTelemetry?,
    cartoApiKey: String,
    isLabDistanceEta: Boolean,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A).copy(alpha = 0.96f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E293B))
            ) {
                // Hlavička dialogu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = Color(0xFF0284C7),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = departure.routeShortName,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Živá poloha vozu PID",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Směr ${departure.headsign}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zavřít",
                            tint = Color.White
                        )
                    }
                }

                // Interaktivní mapa osmdroid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.3f)
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
                            val stopMarker = Marker(mapView).apply {
                                position = stopGeo
                                title = "Zastávka: ${stop.displayName}"
                                snippet = "Cílová stanice pro sledovaný odjezd"
                                icon = createLitackaStopDrawable(context, departure.vehicleType.name == "TRAIN")
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            mapView.overlays.add(stopMarker)

                            val veh = telemetry?.vehicle
                            if (veh != null) {
                                val vehGeo = GeoPoint(veh.latitude, veh.longitude)
                                val vehMarker = Marker(mapView).apply {
                                    position = vehGeo
                                    title = "Vůz linky ${departure.routeShortName}"
                                    snippet = "Rychlost: ${veh.speedKmh} km/h • Zpoždění: ${veh.delaySeconds} s"
                                    icon = createLitackaVehicleDrawable(
                                        context = context,
                                        routeShortName = departure.routeShortName,
                                        bearing = veh.bearing,
                                        delaySeconds = veh.delaySeconds,
                                        vehicleType = departure.vehicleType,
                                        showExtendedDelay = true
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                }
                                mapView.overlays.add(vehMarker)

                                // Spojnice mezi vozem a zastávkou
                                val connectingLine = Polyline().apply {
                                    outlinePaint.color = AndroidColor.parseColor("#0284C7")
                                    outlinePaint.strokeWidth = 6f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.style = Paint.Style.STROKE
                                    addPoint(vehGeo)
                                    addPoint(stopGeo)
                                }
                                mapView.overlays.add(connectingLine)
                            }

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
                                val minLat = min(veh.latitude, stop.latitude) - 0.0012
                                val maxLat = max(veh.latitude, stop.latitude) + 0.0012
                                val minLon = min(veh.longitude, stop.longitude) - 0.0015
                                val maxLon = max(veh.longitude, stop.longitude) + 0.0015
                                mv.zoomToBoundingBox(BoundingBox(maxLat, maxLon, minLat, minLon), true)
                            } else {
                                mv.controller.animateTo(GeoPoint(stop.latitude, stop.longitude), 16.5, 500L)
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Vycentrovat na vůz a zastávku",
                            tint = Color.White
                        )
                    }
                }

                // Spodní telemetrický panel (styl PID Lítačka)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0F172A))
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (telemetry != null) {
                        // Karta stavu vozu
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = telemetry.formattedStatus,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Cílová zastávka: ${stop.displayName}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Mřížka 4 telemetrických ukazatelů
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryMiniStat(
                                title = "Zpoždění",
                                value = formatDelayLabel(telemetry.delaySeconds, true),
                                valueColor = when {
                                    telemetry.delaySeconds <= 60 -> Color(0xFF4ADE80)
                                    telemetry.delaySeconds <= 180 -> Color(0xFFFBBF24)
                                    else -> Color(0xFFF87171)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            TelemetryMiniStat(
                                title = "Poloha před",
                                value = "${telemetry.pingAgeSeconds} s",
                                valueColor = Color(0xFF38BDF8),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryMiniStat(
                                title = "Vzdálenost",
                                value = "${telemetry.distanceMeters} m",
                                valueColor = Color.White,
                                modifier = Modifier.weight(1f)
                            )

                            TelemetryMiniStat(
                                title = "Rychlost",
                                value = "${telemetry.speedKmh} km/h",
                                valueColor = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Matematický odhad dojezdu dle GPS
                        Surface(
                            color = Color(0xFF0369A1).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "🧪 Matematický odpočet z mapy",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isLabDistanceEta) {
                                        Surface(
                                            color = Color(0xFF16A34A).copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Aktivní",
                                                color = Color(0xFF4ADE80),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                val estTimeFormatted = formatTimeFromEpoch(telemetry.calculatedDepartureEpochMillis)
                                val estMinutes = telemetry.estimatedArrivalSeconds / 60
                                val estSeconds = telemetry.estimatedArrivalSeconds % 60
                                val etaLabel = if (telemetry.isAtStop) {
                                    "Právě odbavuje v zastávce"
                                } else {
                                    "Odjezd v $estTimeFormatted (za ${estMinutes} min ${estSeconds} s)"
                                }

                                Text(
                                    text = etaLabel,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Odpočet nezmizí, dokud autobus neopustí zastávku.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        // Čekání na polohu
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📡 Čekám na první GPS polohu vozu…",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Z palubního počítače vozu ještě nedorazil poziční ping pro tento spoj. Na mapě je zobrazena poloha cílové zastávky ${stop.displayName}.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniStat(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatTimeFromEpoch(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.of("Europe/Prague"))
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}

