package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.signOut
import com.example.data.cloud.FirestoreSessionRepository
import com.example.data.prefs.TelemetryPreferencesRepository
import com.example.telemetry.model.ConnectionState
import com.example.telemetry.model.TelemetrySourceMode
import com.example.ui.components.AlertsCenterDialog
import com.example.ui.components.LiveAlertTickerBanner
import com.example.ui.components.UdpSettingsDialog
import com.example.ui.screens.HistoryAndCompareScreen
import com.example.ui.screens.SetupAdvisorScreen
import com.example.ui.screens.TelemetryDashboardScreen
import com.example.ui.screens.TrackAndGridScreen
import com.example.ui.screens.VehicleConditionScreen
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.AsphaltElevated
import com.example.ui.theme.AsphaltSurface
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorYellow
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SaveRaceUiState
import com.example.ui.viewmodel.SessionsViewModel
import com.example.ui.viewmodel.TelemetryViewModel
import com.google.firebase.firestore.FirebaseFirestore

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val appTabs = listOf(
    NavTab("telemetry", "Telemetria", Icons.Rounded.Analytics, Icons.Outlined.Analytics),
    NavTab("vehicle", "Veículo", Icons.Rounded.DirectionsCar, Icons.Outlined.DirectionsCar),
    NavTab("track_grid", "Pista/Grid", Icons.Rounded.Map, Icons.Outlined.Map),
    NavTab("setup", "Setup", Icons.Rounded.Tune, Icons.Outlined.Tune),
    NavTab("history", "Histórico", Icons.Rounded.History, Icons.Outlined.History),
)

@Composable
fun TelemetryAppShell(
    currentUserId: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val telemetryViewModel: TelemetryViewModel = viewModel(
        key = "telemetry_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                TelemetryViewModel(
                    sessionRepository = FirestoreSessionRepository(db),
                    prefsRepository = TelemetryPreferencesRepository(app),
                    currentUserId = currentUserId,
                )
            }
        },
    )

    val sessionsViewModel: SessionsViewModel = viewModel(
        key = "sessions_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                SessionsViewModel(
                    repository = FirestoreSessionRepository(db),
                    currentUserId = currentUserId,
                )
            }
        },
    )

    val liveState by telemetryViewModel.telemetryState.collectAsStateWithLifecycle()
    val thresholds by telemetryViewModel.thresholds.collectAsStateWithLifecycle()
    val stressTest by telemetryViewModel.stressTestEnabled.collectAsStateWithLifecycle()
    val cloudSaveState by telemetryViewModel.saveState.collectAsStateWithLifecycle()

    val sessionsState by sessionsViewModel.sessionsState.collectAsStateWithLifecycle()
    val selectedA by sessionsViewModel.selectedSessionAId.collectAsStateWithLifecycle()
    val selectedB by sessionsViewModel.selectedSessionBId.collectAsStateWithLifecycle()
    val lapsA by sessionsViewModel.lapsAState.collectAsStateWithLifecycle()
    val lapsB by sessionsViewModel.lapsBState.collectAsStateWithLifecycle()
    val actionMessage by sessionsViewModel.actionMessage.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "telemetry"

    val snackbarHostState = remember { SnackbarHostState() }
    var showAlertsDialog by remember { mutableStateOf(false) }
    var showUdpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(cloudSaveState) {
        when (val s = cloudSaveState) {
            is SaveRaceUiState.Saved -> {
                snackbarHostState.showSnackbar("Corrida sincronizada na nuvem com sucesso!")
                telemetryViewModel.clearSaveState()
            }

            is SaveRaceUiState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                telemetryViewModel.clearSaveState()
            }

            else -> Unit
        }
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            sessionsViewModel.clearActionMessage()
        }
    }

    val unackedCount = liveState.activeAlerts.count { !it.acknowledged }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack)
    ) {
        val isExpandedWidth = maxWidth >= 720.dp

        Scaffold(
            containerColor = CarbonBlack,
            contentWindowInsets = WindowInsets.navigationBars,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                PitWallTopHeader(
                    connectionState = liveState.connectionState,
                    sourceMode = liveState.sourceMode,
                    udpPort = liveState.udpPort,
                    unackedAlerts = unackedCount,
                    onOpenUdpSettings = { showUdpDialog = true },
                    onOpenAlerts = { showAlertsDialog = true },
                    onSignOut = {
                        signOut(credentialManager = credentialManager, scope = scope)
                    },
                )
            },
            bottomBar = {
                if (!isExpandedWidth) {
                    NavigationBar(
                        containerColor = AsphaltSurface,
                        contentColor = TextPrimary,
                    ) {
                        appTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = F1Red,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = F1Red.copy(alpha = 0.16f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.route}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isExpandedWidth) {
                    NavigationRail(
                        containerColor = AsphaltSurface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        appTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = F1Red,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = F1Red.copy(alpha = 0.18f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                ),
                                modifier = Modifier.testTag("rail_tab_${tab.route}"),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    LiveAlertTickerBanner(
                        alerts = liveState.activeAlerts,
                        onOpenAlertCenter = { showAlertsDialog = true },
                        onAcknowledge = telemetryViewModel::acknowledgeAlert,
                    )

                    NavHost(
                        navController = navController,
                        startDestination = "telemetry",
                        modifier = Modifier.weight(1f),
                    ) {
                        composable("telemetry") {
                            TelemetryDashboardScreen(
                                state = liveState,
                                saveState = cloudSaveState,
                                onSaveRaceClick = { telemetryViewModel.saveCurrentRaceToCloud("") },
                                onSwitchToSimulator = {
                                    telemetryViewModel.switchSourceMode(TelemetrySourceMode.SIMULATOR)
                                },
                            )
                        }
                        composable("vehicle") {
                            VehicleConditionScreen(state = liveState)
                        }
                        composable("track_grid") {
                            TrackAndGridScreen(
                                state = liveState,
                                onSelectSimTrack = telemetryViewModel::selectSimulatorTrack,
                                onSelectGridCar = telemetryViewModel::selectGridCar,
                            )
                        }
                        composable("setup") {
                            SetupAdvisorScreen(state = liveState)
                        }
                        composable("history") {
                            HistoryAndCompareScreen(
                                sessionsState = sessionsState,
                                selectedSessionAId = selectedA,
                                selectedSessionBId = selectedB,
                                lapsA = lapsA,
                                lapsB = lapsB,
                                saveState = cloudSaveState,
                                currentLapsCount = liveState.completedLaps.size,
                                onSaveCurrentRace = telemetryViewModel::saveCurrentRaceToCloud,
                                onSelectSessionA = sessionsViewModel::selectSessionA,
                                onSelectSessionB = sessionsViewModel::selectSessionB,
                                onDeleteSession = sessionsViewModel::deleteSession,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAlertsDialog) {
        AlertsCenterDialog(
            alerts = liveState.activeAlerts,
            thresholds = thresholds,
            stressTestEnabled = stressTest,
            isSimulatorMode = liveState.sourceMode == TelemetrySourceMode.SIMULATOR,
            onToggleStressTest = telemetryViewModel::toggleStressTest,
            onAcknowledge = telemetryViewModel::acknowledgeAlert,
            onSaveThresholds = telemetryViewModel::updateAlertThresholds,
            onDismiss = { showAlertsDialog = false },
        )
    }

    if (showUdpDialog) {
        UdpSettingsDialog(
            currentMode = liveState.sourceMode,
            currentPort = liveState.udpPort,
            localIp = liveState.localIpAddress,
            packetCount = liveState.packetCount,
            onSwitchMode = telemetryViewModel::switchSourceMode,
            onSavePort = telemetryViewModel::updateUdpPort,
            onDismiss = { showUdpDialog = false },
        )
    }
}

@Composable
private fun PitWallTopHeader(
    connectionState: ConnectionState,
    sourceMode: TelemetrySourceMode,
    udpPort: Int,
    unackedAlerts: Int,
    onOpenUdpSettings: () -> Unit,
    onOpenAlerts: () -> Unit,
    onSignOut: () -> Unit,
) {
    val dotColor = when (connectionState) {
        ConnectionState.CONNECTED_UDP -> SectorGreen
        ConnectionState.SIMULATOR_ACTIVE -> TelemetryCyan
        ConnectionState.WAITING_UDP -> SectorYellow
        ConnectionState.DISCONNECTED -> F1Red
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsphaltSurface)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_apex_logo),
                contentDescription = "Logo Apex Telemetry",
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "APEX 25",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (sourceMode == TelemetrySourceMode.UDP) "UDP :$udpPort" else "Simulador Ao Vivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Source & Status Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AsphaltElevated)
                    .border(1.dp, AsphaltBorder, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenUdpSettings)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("source_status_chip"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = connectionState.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                )
            }

            IconButton(
                onClick = onOpenAlerts,
                modifier = Modifier.testTag("open_alerts_button"),
            ) {
                BadgedBox(
                    badge = {
                        if (unackedAlerts > 0) {
                            Badge(containerColor = F1Red, contentColor = TextPrimary) {
                                Text("$unackedAlerts")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = "Central de alertas",
                        tint = if (unackedAlerts > 0) SectorYellow else TextSecondary,
                    )
                }
            }

            IconButton(
                onClick = onSignOut,
                modifier = Modifier.testTag("sign_out_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Sair da conta Google",
                    tint = TextSecondary,
                )
            }
        }
    }
}
