package cz.pid.golemiocompanion

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import cz.pid.golemiocompanion.data.local.LabPreferencesRepository
import cz.pid.golemiocompanion.data.remote.api.GolemioApiService
import cz.pid.golemiocompanion.data.repository.GolemioRepositoryImpl
import cz.pid.golemiocompanion.ui.components.liquidGlass
import cz.pid.golemiocompanion.ui.map.LiveMapScreen
import cz.pid.golemiocompanion.ui.settings.TestingSettingsScreen
import cz.pid.golemiocompanion.ui.theme.PidGolemioGlassTheme
import cz.pid.golemiocompanion.ui.trip.TripDashboardScreen
import cz.pid.golemiocompanion.ui.trip.TripViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var tripViewModel: TripViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.GOLEMIO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val apiService = retrofit.create(GolemioApiService::class.java)
        val repository = GolemioRepositoryImpl(apiService)
        val prefsRepository = LabPreferencesRepository(applicationContext)

        tripViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TripViewModel(repository, prefsRepository) as T
                }
            }
        )[TripViewModel::class.java]

        setContent {
            val labSettings by tripViewModel.labSettingsFlow.collectAsStateWithLifecycle()

            LaunchedEffect(labSettings.forced120HzAggressiveRender) {
                applyAggressiveHighRefreshRate(labSettings.forced120HzAggressiveRender)
            }

            PidGolemioGlassTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: "trip"

                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(30.dp),
                                color = Color(0xF20B132B), // 95% opacity dark slate glass
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                shadowElevation = 18.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .liquidGlass(
                                        cornerRadius = 30.dp,
                                        tintColor = Color(0xFF38BDF8),
                                        surfaceAlphaTop = 0.35f,
                                        surfaceAlphaBottom = 0.15f
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LiquidDockTab(
                                        selected = currentRoute == "trip",
                                        onClick = {
                                            navController.navigate("trip") {
                                                popUpTo("trip") { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = Icons.Default.DirectionsBus,
                                        label = "Odjezdy"
                                    )
                                    LiquidDockTab(
                                        selected = currentRoute == "map",
                                        onClick = {
                                            navController.navigate("map") {
                                                popUpTo("trip")
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = Icons.Default.Map,
                                        label = "Živá Mapa"
                                    )
                                    LiquidDockTab(
                                        selected = currentRoute == "lab",
                                        onClick = {
                                            navController.navigate("lab") {
                                                popUpTo("trip")
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = Icons.Default.Science,
                                        label = "Laboratoř"
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "trip",
                        modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
                    ) {
                        composable("trip") {
                            TripDashboardScreen(viewModel = tripViewModel)
                        }
                        composable("map") {
                            LiveMapScreen(viewModel = tripViewModel)
                        }
                        composable("lab") {
                            TestingSettingsScreen(
                                viewModel = tripViewModel,
                                onRequestFloatingPip = { enterFloatingPipCountdownMode() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::tripViewModel.isInitialized && tripViewModel.labSettingsFlow.value.floatingPipMiniCountdown) {
            enterFloatingPipCountdownMode()
        }
    }

    private fun enterFloatingPipCountdownMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    private fun applyAggressiveHighRefreshRate(enableAggressive120Hz: Boolean) {
        if (enableAggressive120Hz) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val highestMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
                if (highestMode != null) {
                    val params = window.attributes
                    params.preferredDisplayModeId = highestMode.modeId
                    window.attributes = params
                }
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun RowScope.LiquidDockTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val activeColor = Color(0xFF38BDF8)
    val inactiveColor = Color.White.copy(alpha = 0.6f)
    val animatedBg by animateColorAsState(
        targetValue = if (selected) Color(0xFF0284C7).copy(alpha = 0.28f) else Color.Transparent,
        label = "dockTabBg"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
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
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                color = if (selected) Color.White else inactiveColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}