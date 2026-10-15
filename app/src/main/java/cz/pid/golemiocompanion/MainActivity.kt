package cz.pid.golemiocompanion

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import cz.pid.golemiocompanion.service.DepartureTrackerService
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cz.pid.golemiocompanion.data.local.LabPreferencesRepository
import cz.pid.golemiocompanion.data.repository.GolemioRepositoryImpl
import cz.pid.golemiocompanion.ui.components.ActiveCountdownBanner
import cz.pid.golemiocompanion.ui.components.liquidGlassCard
import cz.pid.golemiocompanion.ui.map.LiveMapScreen
import cz.pid.golemiocompanion.ui.settings.TestingSettingsScreen
import cz.pid.golemiocompanion.ui.theme.GolemioCompanionTheme
import cz.pid.golemiocompanion.ui.trip.TripDashboardScreen
import cz.pid.golemiocompanion.ui.trip.TripViewModel

enum class AppTab {
    DEPARTURES,
    LIVE_MAP,
    LAB
}

class MainActivity : ComponentActivity() {

    private lateinit var labPreferencesRepository: LabPreferencesRepository
    private lateinit var tripViewModel: TripViewModel
    private var isInPipModeState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cz.pid.golemiocompanion.domain.model.PidStopDirectory.initialize(this)

        labPreferencesRepository = LabPreferencesRepository(applicationContext)
        tripViewModel = TripViewModel(
            repository = GolemioRepositoryImpl(
                apiKeyProvider = { labPreferencesRepository.settings.value.golemioApiKey }
            ),
            labPreferencesRepository = labPreferencesRepository
        )

        setContent {
            GolemioCompanionTheme {
                val uiState by tripViewModel.uiState.collectAsState()
                val labSettings by tripViewModel.labSettings.collectAsState()
                var currentTab by remember { mutableStateOf(AppTab.DEPARTURES) }

                // Automatické vyžádání polohy při startu aplikace a doporučení nejbližších zastávek
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (granted) {
                        tripViewModel.fetchDeviceLocationAndRecommendStops(
                            context = this@MainActivity,
                            forceSelectNearest = false
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    val hasFine = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasFine || hasCoarse) {
                        tripViewModel.fetchDeviceLocationAndRecommendStops(
                            context = this@MainActivity,
                            forceSelectNearest = false
                        )
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }

                if (isInPipModeState && uiState.trackedDeparture != null) {
                    ActiveCountdownBanner(
                        departure = uiState.trackedDeparture!!,
                        telemetry = uiState.trackedVehicleTelemetry,
                        isLabDistanceEta = labSettings.enableDistanceBasedEta,
                        isPipMode = true,
                        onStopTracking = { tripViewModel.clearTrackedDeparture() }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            AppTab.DEPARTURES -> {
                                TripDashboardScreen(
                                    uiState = uiState,
                                    labSettings = labSettings,
                                    onSearchQueryChanged = tripViewModel::onSearchQueryChanged,
                                    onSelectStop = tripViewModel::selectStop,
                                    onSubmitCustomStop = tripViewModel::submitCustomStopQuery,
                                    onToggleTrackDeparture = tripViewModel::toggleTrackDeparture,
                                    onClearTrackedDeparture = tripViewModel::clearTrackedDeparture,
                                    onRefreshDepartures = tripViewModel::refreshDeparturesNow,
                                    onRequestGpsLocation = {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                        tripViewModel.fetchDeviceLocationAndRecommendStops(
                                            context = this@MainActivity,
                                            forceSelectNearest = true
                                        )
                                    },
                                    onCheckHapticAlert = { remSec ->
                                        tripViewModel.checkAndTriggerHapticAlert(this@MainActivity, remSec)
                                    }
                                )
                            }

                            AppTab.LIVE_MAP -> {
                                LiveMapScreen(
                                    uiState = uiState,
                                    labSettings = labSettings,
                                    onRouteFilterChanged = tripViewModel::setMapRouteFilter,
                                    onSelectStopFromMap = { stop ->
                                        tripViewModel.selectStop(stop)
                                        currentTab = AppTab.DEPARTURES
                                    },
                                    onRequestGpsLocation = {
                                        tripViewModel.fetchDeviceLocationAndRecommendStops(
                                            context = this@MainActivity,
                                            forceSelectNearest = false
                                        )
                                    },
                                    onTrackVehicle = { line, dest, targetMillis ->
                                        val intent = Intent(this@MainActivity, DepartureTrackerService::class.java).apply {
                                            action = DepartureTrackerService.ACTION_START
                                            putExtra("LINE", line)
                                            putExtra("DESTINATION", dest)
                                            putExtra("TARGET_TIME", targetMillis)
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            ContextCompat.startForegroundService(this@MainActivity, intent)
                                        } else {
                                            startService(intent)
                                        }
                                    }
                                )
                            }

                            AppTab.LAB -> {
                                TestingSettingsScreen(
                                    settings = labSettings,
                                    repository = labPreferencesRepository
                                )
                            }
                        }

                        // Dokonale vycentrovaná plovoucí navigační lišta
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(30.dp),
                                color = Color(0xF20F172A),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                shadowElevation = 16.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .liquidGlassCard(cornerRadius = 30.dp, borderAlpha = 0.22f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LiquidDockItem(
                                        selected = currentTab == AppTab.DEPARTURES,
                                        onClick = { currentTab = AppTab.DEPARTURES },
                                        icon = Icons.Default.DirectionsBus,
                                        label = "Odjezdy"
                                    )
                                    LiquidDockItem(
                                        selected = currentTab == AppTab.LIVE_MAP,
                                        onClick = { currentTab = AppTab.LIVE_MAP },
                                        icon = Icons.Default.Map,
                                        label = "Živá Mapa"
                                    )
                                    LiquidDockItem(
                                        selected = currentTab == AppTab.LAB,
                                        onClick = { currentTab = AppTab.LAB },
                                        icon = Icons.Default.Science,
                                        label = "Laboratoř"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val tracked = tripViewModel.uiState.value.trackedDeparture
        val settings = labPreferencesRepository.settings.value

        // 1. HyperOS Dynamic Island / Živá aktivita u kamery:
        if (tracked != null && settings.enableIslandNotification) {
            val intent = Intent(this, DepartureTrackerService::class.java).apply {
                action = DepartureTrackerService.ACTION_START
                putExtra("LINE", tracked.routeShortName)
                putExtra("DESTINATION", tracked.headsign)
                putExtra("TARGET_TIME", tracked.exactDepartureEpochMillis)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        }

        // 2. PiP režim (pokud je zapnutý a uživatel minimalizuje aplikaci):
        if (tracked != null && settings.enablePipCountdown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }

    override fun onStart() {
        super.onStart()
        // Vrátil se zpět do aplikace – zastavíme foreground service ostrůvku:
        val intent = Intent(this, DepartureTrackerService::class.java).apply {
            action = DepartureTrackerService.ACTION_STOP
        }
        startService(intent)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
    }
}

@Composable
private fun RowScope.LiquidDockItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val activeColor = Color(0xFF38BDF8)
    val inactiveColor = Color(0xFF94A3B8)
    val animatedBg by animateColorAsState(
        targetValue = if (selected) Color(0xFF0284C7).copy(alpha = 0.32f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "dockItemBg"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dockItemScale"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(animatedBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeColor else inactiveColor,
                modifier = Modifier.size(23.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (selected) activeColor else inactiveColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}