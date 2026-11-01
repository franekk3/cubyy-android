package com.cubyy.timer.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubyy.timer.data.SessionStats
import com.cubyy.timer.data.Penalty
import com.cubyy.timer.data.Solve
import com.cubyy.timer.data.SolveRepository
import com.cubyy.timer.data.StatisticsCalculator
import com.cubyy.timer.smartcube.SmartCubeManager
import com.cubyy.timer.smartcube.model.SmartCubeDevice
import com.cubyy.timer.timer.TimerEngine
import com.cubyy.timer.timer.ScrambleMoveProgress
import com.cubyy.timer.timer.TimerStatus
import com.cubyy.timer.ui.components.VirtualCubeView
import com.cubyy.timer.ui.theme.TimerGreen
import com.cubyy.timer.ui.theme.TimerOrange
import com.cubyy.timer.ui.theme.TimerRed

@Composable
fun TimerScreen(
    timerEngine: TimerEngine,
    solveRepository: SolveRepository,
    smartCubeManager: SmartCubeManager
) {
    val status by timerEngine.status.collectAsState()
    val elapsedMillis by timerEngine.elapsedMillis.collectAsState()
    val scramble by timerEngine.currentScramble.collectAsState()
    val virtualCube by timerEngine.virtualCube.collectAsState()
    val cubeTurnAnimation by timerEngine.cubeTurnAnimation.collectAsState()
    val userSettings by timerEngine.userSettings.collectAsState()
    val scrambleProgress by timerEngine.scrambleProgress.collectAsState()
    val showTouchPenaltyEditor by timerEngine.showTouchPenaltyEditor.collectAsState()
    val lastSolve by timerEngine.lastSolve.collectAsState()
    val connectedDevice by smartCubeManager.connectedDevice.collectAsState()
    val hasGyroscope by smartCubeManager.hasGyroscope.collectAsState()
    val displayOrientation by timerEngine.displayOrientation.collectAsState()
    val discoveredDevices by smartCubeManager.discoveredDevices.collectAsState()
    val isScanning by smartCubeManager.isScanning.collectAsState()
    val isConnecting by smartCubeManager.isConnecting.collectAsState()
    val sessions by solveRepository.sessions.collectAsState()

    val currentSession by solveRepository.currentSession.collectAsState()
    val solves by solveRepository.solves.collectAsState()
    val sessionSolves = remember(solves, currentSession) {
        solves.filter { it.sessionName == currentSession }
    }
    val sessionStats = remember(sessionSolves) {
        StatisticsCalculator.calculateSessionStats(sessionSolves)
    }

    var showCubeDialog by remember { mutableStateOf(false) }
    var showSessionDialog by remember { mutableStateOf(false) }
    var showCreateSessionDialog by remember { mutableStateOf(false) }
    var showDeleteSolveDialog by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) smartCubeManager.startScan()
    }

    fun requestCubeScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ))
        }
    }

    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val effectiveCubeScale = userSettings.cubeScale * if (isTablet) 0.75f else 1f

    // Dynamic color for timer text based on state
    val timerTextColor by animateColorAsState(
        targetValue = when (status) {
            TimerStatus.HOLDING -> TimerRed
            TimerStatus.READY -> TimerGreen
            TimerStatus.INSPECTION -> if (elapsedMillis < 3000L) TimerRed else TimerOrange
            TimerStatus.RUNNING -> MaterialTheme.colorScheme.primary
            TimerStatus.STOPPED, TimerStatus.IDLE -> MaterialTheme.colorScheme.onBackground
        },
        label = "TimerColorAnimation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 1120.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Session & Smart Cube Status Bar
            TopBarSection(
                currentSession = currentSession,
                connectedDeviceName = connectedDevice?.name,
                batteryLevel = connectedDevice?.batteryPercent ?: -1,
                onSessionClick = { showSessionDialog = true },
                onSmartCubeClick = { showCubeDialog = true },
                onNewScrambleClick = { timerEngine.newScramble() }
            )

            // Scramble Card (Hidden when running for distraction-free solving)
            if (status != TimerStatus.RUNNING) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (scramble.isBlank()) {
                            Text(
                                text = "Target restored · make the first solve move",
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = MaterialTheme.typography.titleSmall.fontSize * userSettings.scrambleFontScale,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            val tokens = scramble.trim().split("\\s+".toRegex())
                            val coloredScramble = buildAnnotatedString {
                                tokens.forEachIndexed { index, token ->
                                    val color = when (scrambleProgress.getOrNull(index) ?: ScrambleMoveProgress.PENDING) {
                                        ScrambleMoveProgress.CORRECT -> TimerGreen
                                        ScrambleMoveProgress.WRONG -> TimerRed
                                        ScrambleMoveProgress.PARTIAL -> TimerOrange
                                        ScrambleMoveProgress.PENDING -> MaterialTheme.colorScheme.onSurface
                                    }
                                    pushStyle(SpanStyle(color = color))
                                    append(token)
                                    pop()
                                    if (index != tokens.lastIndex) append("  ")
                                }
                            }
                            Text(
                                text = coloredScramble,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize * userSettings.scrambleFontScale,
                                ),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            // Timer and connected-cube preview share the available center area.
            if (isLandscape) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimerReadout(
                        modifier = Modifier.weight(1f),
                        status = status,
                        elapsedMillis = elapsedMillis,
                        timerTextColor = timerTextColor,
                        penalty = lastSolve?.penalty ?: Penalty.NONE,
                        timerEngine = timerEngine,
                        fontScale = userSettings.timerFontScale
                    )
                    if (connectedDevice != null) {
                        VirtualCubeView(
                            virtualCube = virtualCube,
                            animation = cubeTurnAnimation,
                            orientation = displayOrientation,
                            modifier = Modifier.weight(0.9f).aspectRatio(1.2f).scale(effectiveCubeScale)
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    TimerReadout(
                        modifier = Modifier.fillMaxWidth(),
                        status = status,
                        elapsedMillis = elapsedMillis,
                        timerTextColor = timerTextColor,
                        penalty = lastSolve?.penalty ?: Penalty.NONE,
                        timerEngine = timerEngine,
                        fontScale = userSettings.timerFontScale
                    )
                    if (connectedDevice != null) {
                        VirtualCubeView(
                            virtualCube = virtualCube,
                            animation = cubeTurnAnimation,
                            orientation = displayOrientation,
                            modifier = Modifier.fillMaxWidth(if (isTablet) 0.40f else 0.58f).aspectRatio(1.2f).scale(effectiveCubeScale)
                        )
                    }
                }
            }

            // Bottom Section: Session Averages & Quick Stats Bar
            BottomStatsSection(sessionStats = sessionStats, isTablet = isTablet)
        }

        if (showTouchPenaltyEditor && connectedDevice == null && lastSolve != null) {
            Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = timerEngine::dismissTouchPenaltyEditor
                        )
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 155.dp)
                        .zIndex(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = lastSolve?.penalty == Penalty.PLUS_TWO,
                            onClick = {
                                timerEngine.setLastSolvePenalty(
                                    if (lastSolve?.penalty == Penalty.PLUS_TWO) Penalty.NONE else Penalty.PLUS_TWO
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = Color(0xFF777777),
                                selectedLabelColor = Color.White
                            ),
                            label = { Text("+2") }
                        )
                        FilterChip(
                            selected = lastSolve?.penalty == Penalty.DNF,
                            onClick = {
                                timerEngine.setLastSolvePenalty(
                                    if (lastSolve?.penalty == Penalty.DNF) Penalty.NONE else Penalty.DNF
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = Color(0xFF777777),
                                selectedLabelColor = Color.White
                            ),
                            label = { Text("DNF") }
                        )
                        IconButton(onClick = { showDeleteSolveDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete last solve")
                        }
                    }
                }
            }
        }
    }

    if (showCubeDialog) {
        SmartCubeConnectionDialog(
            connectedDeviceName = connectedDevice?.name,
            connectedDeviceAddress = connectedDevice?.address,
            connectedBatteryPercent = connectedDevice?.batteryPercent ?: -1,
            devices = discoveredDevices,
            isScanning = isScanning,
            isConnecting = isConnecting,
            onScan = { if (isScanning) smartCubeManager.stopScan() else requestCubeScan() },
            onConnect = smartCubeManager::connect,
            onResetCube = timerEngine::calibrateVirtualCubeSolved,
            onResetGyro = timerEngine::resetGyroscopeOrientation,
            gyroSupported = hasGyroscope,
            onDisconnect = smartCubeManager::disconnect,
            onDismiss = { showCubeDialog = false }
        )
    }

    if (showSessionDialog) {
        AlertDialog(
            onDismissRequest = { showSessionDialog = false },
            title = { Text("Timer session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sessions.forEach { session ->
                        TextButton(
                            onClick = {
                                solveRepository.switchSession(session)
                                showSessionDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (session == currentSession) "$session  ✓" else session,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSessionDialog = false
                    showCreateSessionDialog = true
                }) { Text("New session") }
            },
            dismissButton = {
                TextButton(onClick = { showSessionDialog = false }) { Text("Done") }
            }
        )
    }

    if (showCreateSessionDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSessionDialog = false },
            title = { Text("New session") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newSessionName,
                    onValueChange = { newSessionName = it },
                    label = { Text("Session name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newSessionName.isNotBlank(),
                    onClick = {
                        solveRepository.addSession(newSessionName.trim())
                        newSessionName = ""
                        showCreateSessionDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    newSessionName = ""
                    showCreateSessionDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteSolveDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSolveDialog = false },
            title = { Text("Delete last solve?") },
            text = { Text("This will permanently remove the most recent solve.") },
            confirmButton = {
                TextButton(onClick = {
                    timerEngine.deleteLastSolve()
                    showDeleteSolveDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSolveDialog = false }) { Text("Cancel") }
            }
        )
    }

}

@Composable
private fun TimerReadout(
    modifier: Modifier,
    status: TimerStatus,
    elapsedMillis: Long,
    timerTextColor: Color,
    penalty: Penalty,
    timerEngine: TimerEngine,
    fontScale: Float
) {
    val displayText = when (status) {
        TimerStatus.INSPECTION -> {
            val seconds = (elapsedMillis / 1000).toInt()
            when {
                elapsedMillis <= -2000L -> "DNF"
                elapsedMillis < 0L -> "+2"
                else -> "$seconds"
            }
        }
        TimerStatus.READY -> "Ready"
        TimerStatus.HOLDING -> "Hold"
        TimerStatus.RUNNING, TimerStatus.IDLE -> Solve.formatMillis(elapsedMillis)
        TimerStatus.STOPPED -> when (penalty) {
            Penalty.NONE -> Solve.formatMillis(elapsedMillis)
            Penalty.PLUS_TWO -> "${Solve.formatMillis(elapsedMillis + 2000)}+"
            Penalty.DNF -> "DNF"
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        timerEngine.onTouchDown()
                        if (tryAwaitRelease()) timerEngine.onTouchUp() else timerEngine.onTouchCancel()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                fontSize = (if (status == TimerStatus.READY || status == TimerStatus.HOLDING) 54.sp else 74.sp) * fontScale,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = timerTextColor,
                textAlign = TextAlign.Center
            )
        }

    }
}

@Composable
private fun TopBarSection(
    currentSession: String,
    connectedDeviceName: String?,
    batteryLevel: Int,
    onSessionClick: () -> Unit,
    onSmartCubeClick: () -> Unit,
    onNewScrambleClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Session Chip
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(end = 8.dp).clickable(onClick = onSessionClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Select session",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = currentSession,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Right side: Smart Cube status chip and new scramble button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Smart Cube Pill
            Surface(
                shape = CircleShape,
                color = if (connectedDeviceName != null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.clickable { onSmartCubeClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (connectedDeviceName != null) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = "Smart Cube",
                        modifier = Modifier.size(16.dp),
                        tint = if (connectedDeviceName != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = connectedDeviceName ?: "Connect cube",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 132.dp),
                        color = if (connectedDeviceName != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (batteryLevel >= 0) {
                        Text(
                            text = "$batteryLevel%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (connectedDeviceName != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Refresh scramble button
            IconButton(
                onClick = onNewScrambleClick,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "New Scramble",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomStatsSection(
    sessionStats: SessionStats,
    isTablet: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = if (isTablet) Modifier else Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .then(if (isTablet) Modifier else Modifier.fillMaxWidth())
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = if (isTablet) Arrangement.spacedBy(32.dp) else Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    label = "Ao5",
                    value = sessionStats.currentAo5?.let { Solve.formatMillis(it) } ?: "-"
                )
                StatItem(
                    label = "Ao12",
                    value = sessionStats.currentAo12?.let { Solve.formatMillis(it) } ?: "-"
                )
                StatItem(
                    label = "Best",
                    value = sessionStats.bestSingle?.let { Solve.formatMillis(it) } ?: "-"
                )
                StatItem(
                    label = "Solves",
                    value = "${sessionStats.solveCount}"
                )
            }
        }
    }
}

@Composable
private fun SmartCubeConnectionDialog(
    connectedDeviceName: String?,
    connectedDeviceAddress: String?,
    connectedBatteryPercent: Int,
    gyroSupported: Boolean,
    devices: List<SmartCubeDevice>,
    isScanning: Boolean,
    isConnecting: Boolean,
    onScan: () -> Unit,
    onConnect: (SmartCubeDevice) -> Unit,
    onResetCube: () -> Unit,
    onResetGyro: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Smart cube") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (connectedDeviceName != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(connectedDeviceName, fontWeight = FontWeight.SemiBold)
                                }
                                if (connectedBatteryPercent >= 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.BatteryFull,
                                            contentDescription = "Battery",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "$connectedBatteryPercent% battery",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onResetCube, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset cube state")
                        }
                        TextButton(onClick = onDisconnect) { Text("Disconnect") }
                    }
                    if (gyroSupported) {
                        OutlinedButton(
                            onClick = onResetGyro,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset gyro orientation")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nearby devices", style = MaterialTheme.typography.titleSmall)
                    FilledTonalButton(onClick = onScan, enabled = !isConnecting) {
                        if (isScanning) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop")
                        } else {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan")
                        }
                    }
                }

                if (devices.isEmpty()) {
                    Text(
                        if (isScanning) "Searching for smart cubes…" else "Scan to find a nearby smart cube.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                devices.forEach { device ->
                    val alreadyConnected = device.address == connectedDeviceAddress
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(device.brand.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { onConnect(device) },
                            enabled = !alreadyConnected && !isConnecting
                        ) {
                            if (alreadyConnected) Text("Connected")
                            else if (isConnecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Connect")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

