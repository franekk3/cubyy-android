package com.cubyy.timer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import com.cubyy.timer.data.SettingsRepository
import com.cubyy.timer.data.SolveRepository
import com.cubyy.timer.smartcube.SmartCubeManager
import com.cubyy.timer.timer.TimerEngine
import com.cubyy.timer.timer.TimerStatus
import com.cubyy.timer.ui.screens.HistoryScreen
import com.cubyy.timer.ui.screens.SettingsScreen
import com.cubyy.timer.ui.screens.TimerScreen
import com.cubyy.timer.ui.theme.CubyyTheme

class MainActivity : ComponentActivity() {

    private lateinit var solveRepository: SolveRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var smartCubeManager: SmartCubeManager
    private lateinit var timerEngine: TimerEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize core dependencies
        solveRepository = SolveRepository(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        smartCubeManager = SmartCubeManager(applicationContext)
        timerEngine = TimerEngine(applicationContext, solveRepository, settingsRepository, smartCubeManager)
        smartCubeManager.reconnectToLastCubeIfPermitted()

        setContent {
            val settings by settingsRepository.settings.collectAsState()

            val isDark = when (settings.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            CubyyTheme(
                darkTheme = isDark,
                dynamicColor = settings.colorTheme == "DYNAMIC",
                colorTheme = settings.colorTheme
            ) {
                MainAppContainer(
                    timerEngine = timerEngine,
                    solveRepository = solveRepository,
                    settingsRepository = settingsRepository,
                    smartCubeManager = smartCubeManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smartCubeManager.disconnect()
    }
}

sealed class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Timer : NavigationTab("Timer", Icons.Filled.Timer, Icons.Outlined.Timer)
    data object Stats : NavigationTab("Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart)
    data object Settings : NavigationTab("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainAppContainer(
    timerEngine: TimerEngine,
    solveRepository: SolveRepository,
    settingsRepository: SettingsRepository,
    smartCubeManager: SmartCubeManager
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val userSettings by settingsRepository.settings.collectAsState()
    val connectedDevice by smartCubeManager.connectedDevice.collectAsState()
    val timerStatus by timerEngine.status.collectAsState()
    val useNavigationRail = LocalConfiguration.current.screenWidthDp >= 600
    val tabs = remember {
        listOf(
            NavigationTab.Timer,
            NavigationTab.Stats,
            NavigationTab.Settings
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!useNavigationRail) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (useNavigationRail) {
                NavigationRail {
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxHeight()) {
                when (selectedTab) {
                    0 -> TimerScreen(
                        timerEngine = timerEngine,
                        solveRepository = solveRepository,
                        smartCubeManager = smartCubeManager
                    )
                    1 -> HistoryScreen(
                        solveRepository = solveRepository,
                        cubeAppearance = userSettings.cubeAppearance,
                        smartCubeConnected = connectedDevice?.isTimer == false,
                        timerRunning = timerStatus == TimerStatus.RUNNING
                    )
                    2 -> SettingsScreen(
                        settingsRepository = settingsRepository,
                        solveRepository = solveRepository
                    )
                }
            }
        }
    }
}

