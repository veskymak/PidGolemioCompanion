package cz.pid.golemiocompanion

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

        labPreferencesRepository = LabPreferencesRepository(applicationContext)
        tripViewModel = TripViewModel(
            repository = GolemioRepositoryImpl(),
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

                        // Spodní navigační lišta
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .height(74.dp)
                                .liquidGlassCard(cornerRadius = 28.dp, borderAlpha = 0.25f)
                        ) {
                            NavigationBarItem(
                                selected = currentTab == AppTab.DEPARTURES,
                                onClick = { currentTab = AppTab.DEPARTURES },
                                icon = { Icon(Icons.Default.DirectionsBus, contentDescription = null) },
                                label = { Text("Odjezdy") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF38BDF8),
                                    selectedTextColor = Color(0xFF38BDF8),
                                    indicatorColor = Color(0xFF0C4A6E).copy(alpha = 0.65f),
                                    unselectedIconColor = Color(0xFF94A3B8),
                                    unselectedTextColor = Color(0xFF94A3B8)
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == AppTab.LIVE_MAP,
                                onClick = { currentTab = AppTab.LIVE_MAP },
                                icon = { Icon(Icons.Default.Map, contentDescription = null) },
                                label = { Text("Živá Mapa") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF38BDF8),
                                    selectedTextColor = Color(0xFF38BDF8),
                                    indicatorColor = Color(0xFF0C4A6E).copy(alpha = 0.65f),
                                    unselectedIconColor = Color(0xFF94A3B8),
                                    unselectedTextColor = Color(0xFF94A3B8)
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == AppTab.LAB,
                                onClick = { currentTab = AppTab.LAB },
                                icon = { Icon(Icons.Default.Science, contentDescription = null) },
                                label = { Text("Laboratoř") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF38BDF8),
                                    selectedTextColor = Color(0xFF38BDF8),
                                    indicatorColor = Color(0xFF0C4A6E).copy(alpha = 0.65f),
                                    unselectedIconColor = Color(0xFF94A3B8),
                                    unselectedTextColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val hasTracked = tripViewModel.uiState.value.trackedDeparture != null
        val pipEnabled = labPreferencesRepository.settings.value.enablePipCountdown
        if (hasTracked && pipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
    }
}