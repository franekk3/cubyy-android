package com.cubyy.timer.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.cubyy.timer.ui.components.PuzzleSessionPickerDialog
import com.cubyy.timer.ui.components.WcaScrambleImages
import com.cubyy.timer.scramble.WcaEvent
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
    val isGeneratingScramble by timerEngine.isGeneratingScramble.collectAsState()
    val scrambleGenerationError by timerEngine.scrambleGenerationError.collectAsState()
    val virtualCube by timerEngine.virtualCube.collectAsState()
    val cubeTurnAnimation by timerEngine.cubeTurnAnimation.collectAsState()
    val userSettings by timerEngine.userSettings.collectAsState()
    val scrambleProgress by timerEngine.scrambleProgress.collectAsState()
    val showTouchPenaltyEditor by timerEngine.showTouchPenaltyEditor.collectAsState()
    val lastSolve by timerEngine.lastSolve.collectAsState()
    val smartCubeSolveSummary by timerEngine.smartCubeSolveSummary.collectAsState()
    val multiBlindAttempted by timerEngine.multiBlindAttempted.collectAsState()
    val pendingMultiBlindResult by timerEngine.pendingMultiBlindResult.collectAsState()
    val connectedDevice by smartCubeManager.connectedDevice.collectAsState()
    val hasGyroscope by smartCubeManager.hasGyroscope.collectAsState()
    val displayOrientation by timerEngine.displayOrientation.collectAsState()
    val discoveredDevices by smartCubeManager.discoveredDevices.collectAsState()
    val isScanning by smartCubeManager.isScanning.collectAsState()
    val isConnecting by smartCubeManager.isConnecting.collectAsState()
    val sessions by solveRepository.sessions.collectAsState()

    val currentSession by solveRepository.currentSession.collectAsState()
    val solves by solveRepository.solves.collectAsState()
    val sessionSolves = remember(solves, currentSession.id) {
        solves.filter { it.sessionId == currentSession.id }
    }
    val sessionStats = remember(sessionSolves) {
        StatisticsCalculator.calculateSessionStats(sessionSolves)
    }

    var showCubeDialog by remember { mutableStateOf(false) }
    var showSessionDialog by remember { mutableStateOf(false) }
    var showCreateSessionDialog by remember { mutableStateOf(false) }
    var createSessionEventId by remember { mutableStateOf(currentSession.eventId) }
    var showDeleteSolveDialog by remember { mutableStateOf(false) }
    var showExpandedScramble by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf("") }
    var multiBlindSolvedInput by remember { mutableStateOf("") }
    LaunchedEffect(pendingMultiBlindResult) {
        if (pendingMultiBlindResult != null) multiBlindSolvedInput = ""
    }
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isTablet = configuration.screenWidthDp >= 600
    val quickStatsScale = if (!isTablet && configuration.screenHeightDp < 700) 0.94f else 1f
    val context = LocalContext.current
    // Keep the touch pad active after a solve so a new attempt can start while
    // the post-solve penalty controls are still visible.
    val touchTimerModifier = if (connectedDevice == null) {
        Modifier.pointerInput(timerEngine) {
            detectTapGestures(onPress = {
                timerEngine.onTouchDown()
                if (tryAwaitRelease()) timerEngine.onTouchUp() else timerEngine.onTouchCancel()
            })
        }
    } else Modifier
    val effectiveCubeScale = userSettings.cubeScale * when {
        isTablet -> 0.70f
        isLandscape -> 0.90f
        else -> 1f
    }

    // Dynamic color for timer text based on state
    val timerTextColor by animateColorAsState(
        targetValue = when (status) {
            TimerStatus.HOLDING -> TimerRed
            TimerStatus.BLOCKED -> TimerOrange
            TimerStatus.READY -> TimerGreen
            TimerStatus.INSPECTION -> if (elapsedMillis < 3000L) TimerRed else TimerOrange
            TimerStatus.RUNNING -> MaterialTheme.colorScheme.primary
            TimerStatus.STOPPED, TimerStatus.IDLE -> MaterialTheme.colorScheme.onBackground
        },
        label = "TimerColorAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) {
                        timerEngine.dismissSmartCubeSolveSummary()
                    }
                }
            }
    ) {
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
                currentSession = currentSession.name,
                connectedDeviceName = connectedDevice?.name,
                batteryLevel = connectedDevice?.batteryPercent ?: -1,
                onSessionClick = { if (status != TimerStatus.RUNNING) showSessionDialog = true },
                onSmartCubeClick = { showCubeDialog = true },
                onNewScrambleClick = { timerEngine.newScramble() }
            )

            // Scramble Card (Hidden when running for distraction-free solving)
            if (status != TimerStatus.RUNNING) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Scramble", scramble))
                            Toast.makeText(context, "Scramble copied", Toast.LENGTH_SHORT).show()
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (currentSession.eventId == "333mbf" && status != TimerStatus.RUNNING) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Cubes in this attempt", style = MaterialTheme.typography.labelLarge)
                                TextButton(
                                    enabled = multiBlindAttempted > 1 && !isGeneratingScramble,
                                    onClick = { timerEngine.setMultiBlindAttempted(multiBlindAttempted - 1) }
                                ) { Text("−") }
                                Text("$multiBlindAttempted", style = MaterialTheme.typography.titleMedium)
                                TextButton(
                                    enabled = multiBlindAttempted < 99 && !isGeneratingScramble,
                                    onClick = { timerEngine.setMultiBlindAttempted(multiBlindAttempted + 1) }
                                ) { Text("+") }
                            }
                            Text(
                                "Each line is a separate 3×3 scramble. The attempt has one timer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (isGeneratingScramble) {
                            Text(
                                text = "Generating official scramble…",
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (scrambleGenerationError != null) {
                            Text(
                                text = "Scramble generation failed: $scrambleGenerationError",
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (scramble.isBlank()) {
                            Text(
                                text = "Target restored · make the first solve move",
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = MaterialTheme.typography.titleSmall.fontSize * userSettings.scrambleFontScale,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            val isMultiBlind = currentSession.eventId == "333mbf"
                            var scrambleLineCount by remember(scramble) { mutableStateOf(0) }
                            val tokens = scramble.trim().split("\\s+".toRegex())
                            val coloredScramble = buildAnnotatedString {
                                if (isMultiBlind) {
                                    scramble.lines().forEachIndexed { index, line ->
                                        append("${index + 1}.  $line")
                                        if (index != scramble.lines().lastIndex) append("\n")
                                    }
                                } else {
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
                            }
                            Text(
                                text = coloredScramble,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (!isTablet) Modifier.heightIn(max = if (isMultiBlind) 72.dp else 84.dp)
                                            .verticalScroll(rememberScrollState())
                                        else if (isMultiBlind) Modifier.heightIn(max = 320.dp)
                                            .verticalScroll(rememberScrollState())
                                        else Modifier
                                    ),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = if (isMultiBlind) 20.sp else 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = (if (isMultiBlind) 14.sp else MaterialTheme.typography.titleMedium.fontSize) * userSettings.scrambleFontScale,
                                ),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                onTextLayout = { layout ->
                                    if (scrambleLineCount != layout.lineCount) scrambleLineCount = layout.lineCount
                                }
                            )
                            if (!isTablet && scrambleLineCount > 3) {
                                TextButton(
                                    onClick = { showExpandedScramble = true },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Expand scramble")
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            // Timer and connected-cube preview share the available center area.
            if (isLandscape) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().then(touchTimerModifier),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TimerReadout(
                            modifier = Modifier.fillMaxWidth(),
                            status = status,
                            elapsedMillis = elapsedMillis,
                            timerTextColor = timerTextColor,
                            penalty = lastSolve?.penalty ?: Penalty.NONE,
                            hideTimeWhileSolving = userSettings.hideTimerDuringSolve,
                            fontScale = userSettings.timerFontScale,
                            showPenaltyEditor = showTouchPenaltyEditor && connectedDevice?.isTimer != false && lastSolve != null,
                            onPenaltyChange = timerEngine::setLastSolvePenalty,
                            onDeleteSolve = { showDeleteSolveDialog = true }
                        )
                        if (connectedDevice?.isTimer == false && status == TimerStatus.STOPPED && smartCubeSolveSummary != null) {
                            SmartCubeSolveSummaryBar(smartCubeSolveSummary!!.moveCount, smartCubeSolveSummary!!.turnsPerSecond)
                        }
                        if (connectedDevice?.isTimer == true) GanTimerGraphic(status, Modifier.widthIn(max = 420.dp).fillMaxWidth(0.72f).aspectRatio(1000f / 280f))
                    }
                    if (connectedDevice?.isTimer == false && currentSession.event.threeByThreeHardware) {
                        VirtualCubeView(
                            virtualCube = virtualCube,
                            animation = cubeTurnAnimation,
                            orientation = displayOrientation,
                            appearance = userSettings.cubeAppearance,
                            modifier = Modifier.weight(0.9f).aspectRatio(1.2f).scale(effectiveCubeScale)
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(touchTimerModifier)
                ) {
                    TimerReadout(
                        modifier = Modifier.fillMaxWidth(),
                        status = status,
                        elapsedMillis = elapsedMillis,
                        timerTextColor = timerTextColor,
                        penalty = lastSolve?.penalty ?: Penalty.NONE,
                        hideTimeWhileSolving = userSettings.hideTimerDuringSolve,
                        fontScale = userSettings.timerFontScale,
                        showPenaltyEditor = showTouchPenaltyEditor && connectedDevice?.isTimer != false && lastSolve != null,
                        onPenaltyChange = timerEngine::setLastSolvePenalty,
                        onDeleteSolve = { showDeleteSolveDialog = true }
                    )
                    if (connectedDevice?.isTimer == false && status == TimerStatus.STOPPED && smartCubeSolveSummary != null) {
                        SmartCubeSolveSummaryBar(smartCubeSolveSummary!!.moveCount, smartCubeSolveSummary!!.turnsPerSecond)
                    }
                    if (connectedDevice?.isTimer == true) {
                        GanTimerGraphic(status, Modifier.widthIn(max = 420.dp).fillMaxWidth(if (isTablet) 0.48f else 0.78f).aspectRatio(1000f / 280f))
                    } else if (connectedDevice != null && currentSession.event.threeByThreeHardware) {
                        VirtualCubeView(
                            virtualCube = virtualCube,
                            animation = cubeTurnAnimation,
                            orientation = displayOrientation,
                            appearance = userSettings.cubeAppearance,
                            modifier = Modifier.fillMaxWidth(if (isTablet) 0.40f else 0.58f).aspectRatio(1.2f).scale(effectiveCubeScale)
                        )
                    }
                }
            }

            // Bottom Section: Session Averages & Quick Stats Bar
            BottomStatsSection(
                sessionStats = sessionStats,
                isTablet = isTablet,
                eventId = currentSession.eventId,
                scramble = scramble,
                showScrambleImage = connectedDevice?.isTimer != false && scramble.isNotBlank(),
                touchTimerModifier = touchTimerModifier,
                quickStatsScale = quickStatsScale
            )
        }

        if (showExpandedScramble) {
            Dialog(
                onDismissRequest = { showExpandedScramble = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Full scramble", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { showExpandedScramble = false }) { Text("Close") }
                        }
                        Text(
                            text = scramble,
                            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

    }

    if (showCubeDialog) {
        SmartCubeConnectionDialog(
            connectedDeviceName = connectedDevice?.name,
            connectedDeviceAddress = connectedDevice?.address,
            connectedDeviceIsTimer = connectedDevice?.isTimer == true,
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
        PuzzleSessionPickerDialog(
            sessions = sessions,
            currentSession = currentSession,
            restrictToThreeByThree = connectedDevice?.isTimer == false,
            title = "Timer session",
            onSelect = { session -> solveRepository.switchSession(session); showSessionDialog = false },
            onCreate = { eventId ->
                createSessionEventId = eventId
                showSessionDialog = false
                showCreateSessionDialog = true
            },
            onDismiss = { showSessionDialog = false }
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
                        solveRepository.addSession(newSessionName.trim(), createSessionEventId)
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

    pendingMultiBlindResult?.let { pending ->
        val solved = multiBlindSolvedInput.toIntOrNull()
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Multi-Blind result") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("You attempted ${pending.attempted} cubes in ${Solve.formatMillis(pending.timeMillis)}. How many did you solve?")
                    androidx.compose.material3.OutlinedTextField(
                        value = multiBlindSolvedInput,
                        onValueChange = { value ->
                            if (value.length <= 2 && (value.isEmpty() || value.all(Char::isDigit))) multiBlindSolvedInput = value
                        },
                        label = { Text("Solved (0–${pending.attempted})") },
                        singleLine = true
                    )
                    Text("WCA result: solved minus unsolved; fewer cubes attempted breaks ties.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = solved != null && solved in 0..pending.attempted,
                    onClick = { timerEngine.submitMultiBlindSolved(solved ?: return@TextButton) }
                ) { Text("Save attempt") }
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
    hideTimeWhileSolving: Boolean,
    fontScale: Float,
    showPenaltyEditor: Boolean,
    onPenaltyChange: (Penalty) -> Unit,
    onDeleteSolve: () -> Unit
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
        TimerStatus.BLOCKED -> Solve.formatMillis(elapsedMillis)
        TimerStatus.RUNNING -> if (hideTimeWhileSolving) "Solving..." else Solve.formatMillis(elapsedMillis)
        TimerStatus.IDLE -> Solve.formatMillis(elapsedMillis)
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
                .heightIn(min = 132.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                fontSize = (when {
                    status == TimerStatus.RUNNING && hideTimeWhileSolving -> 32.sp
                    status == TimerStatus.READY || status == TimerStatus.HOLDING -> 54.sp
                    else -> 74.sp
                }) * fontScale,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = timerTextColor,
                textAlign = TextAlign.Center
            )
        }
        if (showPenaltyEditor) {
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = penalty == Penalty.PLUS_TWO,
                    onClick = { onPenaltyChange(if (penalty == Penalty.PLUS_TWO) Penalty.NONE else Penalty.PLUS_TWO) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = Color(0xFF777777),
                        selectedLabelColor = Color.White
                    ),
                    label = { Text("+2") }
                )
                FilterChip(
                    selected = penalty == Penalty.DNF,
                    onClick = { onPenaltyChange(if (penalty == Penalty.DNF) Penalty.NONE else Penalty.DNF) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = Color(0xFF777777),
                        selectedLabelColor = Color.White
                    ),
                    label = { Text("DNF") }
                )
                IconButton(onClick = onDeleteSolve, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete last solve")
                }
            }
        }

    }
}

@Composable
private fun SmartCubeSolveSummaryBar(moveCount: Int, turnsPerSecond: Double) {
    Surface(
        modifier = Modifier.padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Text(
            text = String.format(java.util.Locale.ROOT, "%d moves  ·  %.2f TPS", moveCount, turnsPerSecond),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GanTimerGraphic(status: TimerStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        TimerStatus.READY -> TimerGreen
        TimerStatus.HOLDING -> TimerRed
        TimerStatus.BLOCKED -> TimerOrange
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }
    Canvas(modifier = modifier.padding(vertical = 6.dp)) {
        val sx = size.width / 1000f
        val sy = size.height / 280f
        val stroke = size.height * 0.036f
        fun rectangle(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left * sx, top * sy),
                size = Size((right - left) * sx, (bottom - top) * sy),
                cornerRadius = CornerRadius(radius * sx, radius * sy),
                style = Stroke(width = stroke)
            )
        }
        rectangle(50f, 27f, 950f, 252f, 65f)
        rectangle(72f, 50f, 322f, 230f, 60f)
        rectangle(678f, 50f, 928f, 230f, 60f)
        rectangle(400f, 62f, 600f, 137f, 10f)
        drawCircle(color, radius = 25f * sx, center = Offset(500f * sx, 192f * sy), style = Stroke(width = stroke))
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
                        contentDescription = "Bluetooth device",
                        modifier = Modifier.size(16.dp),
                        tint = if (connectedDeviceName != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = connectedDeviceName ?: "Connect",
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
    isTablet: Boolean,
    eventId: String,
    scramble: String,
    showScrambleImage: Boolean,
    touchTimerModifier: Modifier = Modifier,
    quickStatsScale: Float = 1f
) {
    val statItems = listOf(
        "Ao5" to (sessionStats.currentAo5?.let { Solve.formatMillis(it) } ?: "-"),
        "Ao12" to (sessionStats.currentAo12?.let { Solve.formatMillis(it) } ?: "-"),
        "Best" to (sessionStats.bestSingle?.let { Solve.formatMillis(it) } ?: "-"),
        "Solves" to "${sessionStats.solveCount}"
    )
    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1120.dp)
                .height(128.dp)
                .padding(vertical = 8.dp)
                .then(touchTimerModifier),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                statItems.forEach { (label, value) -> StatItem(label, value) }
            }
            if (showScrambleImage) {
                WcaScrambleImages(
                    eventId = eventId,
                    scramble = scramble,
                    imageScale = 1f,
                    modifier = Modifier.width(176.dp).fillMaxHeight().padding(6.dp)
                )
            }
        }
    } else if (showScrambleImage) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1000.dp)
                .aspectRatio(2f)
                .padding(vertical = 8.dp)
                .then(touchTimerModifier),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickStatsGrid(statItems, Modifier.weight(1f).fillMaxHeight().scale(quickStatsScale))
            WcaScrambleImages(
                eventId = eventId,
                scramble = scramble,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)
            )
        }
    } else {
        QuickStatsGrid(
            items = statItems,
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .widthIn(max = 260.dp)
                .aspectRatio(1f)
                .padding(vertical = 8.dp)
                .then(touchTimerModifier)
                .scale(quickStatsScale)
        )
    }
}

@Composable
private fun QuickStatsGrid(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { (label, value) ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        StatItem(label = label, value = value)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartCubeConnectionDialog(
    connectedDeviceName: String?,
    connectedDeviceAddress: String?,
    connectedDeviceIsTimer: Boolean,
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
        title = { Text("Bluetooth device") },
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
                        if (!connectedDeviceIsTimer) {
                            OutlinedButton(onClick = onResetCube, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reset cube state")
                            }
                        }
                        TextButton(onClick = onDisconnect) { Text("Disconnect") }
                    }
                    if (gyroSupported && !connectedDeviceIsTimer) {
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
                    FilledTonalButton(onClick = onScan) {
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
                        if (isScanning) "Searching for Bluetooth devices…" else "Scan to find a nearby Bluetooth device.",
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
                            Text(if (device.isTimer) "GAN Timer" else device.brand.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { onConnect(device) },
                            enabled = !alreadyConnected
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

