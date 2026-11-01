package com.cubyy.timer.timer

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cubyy.timer.data.Penalty
import com.cubyy.timer.data.SettingsRepository
import com.cubyy.timer.data.Solve
import com.cubyy.timer.data.SolveRepository
import com.cubyy.timer.scramble.ScrambleGenerator
import com.cubyy.timer.smartcube.SmartCubeManager
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.model.CubeOrientation
import com.cubyy.timer.smartcube.model.VirtualCube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TimerStatus {
    IDLE,
    HOLDING,
    READY,
    INSPECTION,
    RUNNING,
    STOPPED
}

data class CubeTurnAnimation(
    val sequence: Long,
    val before: VirtualCube,
    val move: CubeMove
)

class TimerEngine(
    private val context: Context,
    private val solveRepository: SolveRepository,
    private val settingsRepository: SettingsRepository,
    private val smartCubeManager: SmartCubeManager
) {
    val userSettings = settingsRepository.settings
    private val scope = CoroutineScope(Dispatchers.Main)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val _status = MutableStateFlow(TimerStatus.IDLE)
    val status: StateFlow<TimerStatus> = _status.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _currentScramble = MutableStateFlow(ScrambleGenerator.generate3x3())
    val currentScramble: StateFlow<String> = _currentScramble.asStateFlow()
    private var targetScramble = _currentScramble.value
    private val appliedScrambleMoves = mutableListOf<CubeMove>()
    private var recoveryCheckpointMoves: List<CubeMove> = emptyList()
    private var recoveryProgressSnapshot: List<ScrambleMoveProgress> = emptyList()
    private var recoveryPath: List<CubeMove>? = null
    private var recoveryPathProgress: RecoveryPathProgress? = null
    private var recoveryActive = false
    private val recoverySolver = CubeRecoverySolver()
    private var recoveryJob: Job? = null

    private val _virtualCube = MutableStateFlow(VirtualCube())
    val virtualCube: StateFlow<VirtualCube> = _virtualCube.asStateFlow()
    private val _cubeTurnAnimation = MutableStateFlow<CubeTurnAnimation?>(null)
    val cubeTurnAnimation: StateFlow<CubeTurnAnimation?> = _cubeTurnAnimation.asStateFlow()
    private var cubeTurnSequence = 0L
    private val _displayOrientation = MutableStateFlow(CubeOrientation.IDENTITY)
    val displayOrientation: StateFlow<CubeOrientation> = _displayOrientation.asStateFlow()
    private var gyroHome: CubeOrientation? = null
    private var waitingForGyroHomeSample = false

    private val _lastSolve = MutableStateFlow<Solve?>(null)
    val lastSolve: StateFlow<Solve?> = _lastSolve.asStateFlow()
    private val _showTouchPenaltyEditor = MutableStateFlow(false)
    val showTouchPenaltyEditor: StateFlow<Boolean> = _showTouchPenaltyEditor.asStateFlow()

    private val _reconstructionMoves = MutableStateFlow<List<CubeMove>>(emptyList())
    val reconstructionMoves: StateFlow<List<CubeMove>> = _reconstructionMoves.asStateFlow()

    private val _scrambleProgress = MutableStateFlow<List<ScrambleMoveProgress>>(emptyList())
    val scrambleProgress: StateFlow<List<ScrambleMoveProgress>> = _scrambleProgress.asStateFlow()
    private var scrambleProgressTracker = ScrambleProgressTracker(_currentScramble.value)

    private var startTimeMillis = 0L
    private var timerJob: Job? = null
    private var holdJob: Job? = null
    private var inspectionJob: Job? = null
    private var inspectionStartTime = 0L
    private var beginInspectionOnRelease = false
    private var beginSolveOnRelease = false

    init {
        resetVirtualCubeToSolved()
        resetScrambleProgress()

        // Listen for smart cube moves
        scope.launch {
            smartCubeManager.cubeMoves.collect { move ->
                onSmartCubeMove(move)
            }
        }
        scope.launch {
            smartCubeManager.connectedDevice
                .map { it?.address }
                .distinctUntilChanged()
                .collect { address ->
                    if (address != null) {
                        calibrateVirtualCubeSolved()
                    } else {
                        gyroHome = null
                        waitingForGyroHomeSample = false
                        _displayOrientation.value = CubeOrientation.IDENTITY
                    }
                }
        }
        scope.launch {
            smartCubeManager.cubeOrientation.collect { sensorOrientation ->
                if (sensorOrientation != null) updateDisplayOrientation(sensorOrientation)
            }
        }
        scope.launch {
            smartCubeManager.hasGyroscope.collect { supported ->
                if (!supported) {
                    gyroHome = null
                    waitingForGyroHomeSample = false
                    _displayOrientation.value = CubeOrientation.IDENTITY
                }
            }
        }
    }

    fun newScramble() {
        if (_status.value == TimerStatus.RUNNING) return
        targetScramble = ScrambleGenerator.generate3x3()
        _currentScramble.value = targetScramble
        resetVirtualCubeToSolved()
        resetScrambleTracking()
        _status.value = TimerStatus.IDLE
        _elapsedMillis.value = 0L
    }

    private fun resetVirtualCubeToSolved() {
        _virtualCube.value = VirtualCube()
    }

    private fun resetScrambleProgress() {
        scrambleProgressTracker = ScrambleProgressTracker(_currentScramble.value)
        _scrambleProgress.value = scrambleProgressTracker.currentProgress()
    }

    fun calibrateVirtualCubeSolved() {
        resetVirtualCubeToSolved()
        clearRecoveryState()
        appliedScrambleMoves.clear()
        _currentScramble.value = targetScramble
        resetScrambleProgress()
    }

    fun resetGyroscopeOrientation() {
        val orientation = smartCubeManager.cubeOrientation.value
        if (orientation == null) {
            waitingForGyroHomeSample = true
        } else {
            gyroHome = mapGyroscopeAxes(orientation)
            waitingForGyroHomeSample = false
        }
        _displayOrientation.value = CubeOrientation.IDENTITY
    }

    private fun updateDisplayOrientation(sensor: CubeOrientation) {
        val mapped = mapGyroscopeAxes(sensor)
        if (gyroHome == null || waitingForGyroHomeSample) {
            gyroHome = mapped
            waitingForGyroHomeSample = false
            _displayOrientation.value = CubeOrientation.IDENTITY
            return
        }
        val home = gyroHome!!
        _displayOrientation.value = (mapped * home.inverse()).normalized()
    }

    private fun mapGyroscopeAxes(orientation: CubeOrientation): CubeOrientation =
        CubeOrientation(orientation.x, orientation.z, -orientation.y, orientation.w).normalized()

    private fun resetScrambleTracking() {
        appliedScrambleMoves.clear()
        clearRecoveryState()
        resetScrambleProgress()
    }

    private fun clearRecoveryState() {
        recoveryJob?.cancel()
        recoveryJob = null
        recoveryCheckpointMoves = emptyList()
        recoveryProgressSnapshot = emptyList()
        recoveryPath = null
        recoveryPathProgress = null
        recoveryActive = false
    }

    /**
     * Called when the user presses down on the timer touch surface.
     */
    fun onTouchDown() {
        val settings = settingsRepository.settings.value

        when (_status.value) {
            TimerStatus.RUNNING -> {
                stopTimer()
            }
            TimerStatus.IDLE -> {
                if (settings.inspectionEnabled) {
                    beginInspectionOnRelease = true
                } else {
                    startHolding()
                }
            }
            TimerStatus.INSPECTION -> {
                beginSolveOnRelease = true
            }
            TimerStatus.STOPPED -> {
                _showTouchPenaltyEditor.value = false
                if (settings.inspectionEnabled) beginInspectionOnRelease = true else startHolding()
            }
            else -> {}
        }
    }

    /**
     * Called when the user lifts their finger from the touch surface.
     */
    fun onTouchUp() {
        holdJob?.cancel()
        holdJob = null

        if (beginInspectionOnRelease) {
            beginInspectionOnRelease = false
            startInspection()
            return
        }
        if (beginSolveOnRelease) {
            beginSolveOnRelease = false
            inspectionJob?.cancel()
            inspectionJob = null
            startTimer()
            return
        }

        when (_status.value) {
            TimerStatus.READY -> {
                inspectionJob?.cancel()
                inspectionJob = null
                startTimer()
            }
            TimerStatus.HOLDING -> {
                // Released too early before becoming READY
                if (inspectionStartTime > 0) {
                    _status.value = TimerStatus.INSPECTION
                } else {
                    _status.value = TimerStatus.IDLE
                }
            }
            else -> {}
        }
    }

    fun onTouchCancel() {
        beginInspectionOnRelease = false
        beginSolveOnRelease = false
        holdJob?.cancel()
        holdJob = null
        if (_status.value == TimerStatus.HOLDING) {
            _status.value = if (inspectionStartTime > 0) TimerStatus.INSPECTION else TimerStatus.IDLE
        }
    }

    private fun startHolding() {
        _status.value = TimerStatus.HOLDING
        val holdDuration = settingsRepository.settings.value.holdToStartMillis

        holdJob = scope.launch {
            delay(holdDuration)
            _status.value = TimerStatus.READY
            vibrate(50)
        }
    }

    private fun startInspection() {
        _showTouchPenaltyEditor.value = false
        _status.value = TimerStatus.INSPECTION
        inspectionStartTime = SystemClock.elapsedRealtime()
        _elapsedMillis.value = 15000L

        inspectionJob = scope.launch {
            while (isActive && _status.value == TimerStatus.INSPECTION) {
                val elapsed = SystemClock.elapsedRealtime() - inspectionStartTime
                val remaining = 15000L - elapsed
                _elapsedMillis.value = remaining

                if (elapsed in 8000..8100) {
                    vibrate(70)
                } else if (elapsed in 12000..12100) {
                    vibrate(100)
                }

                delay(50)
            }
        }
    }

    private fun startTimer() {
        _showTouchPenaltyEditor.value = false
        _status.value = TimerStatus.RUNNING
        startTimeMillis = SystemClock.elapsedRealtime()
        _reconstructionMoves.value = emptyList()

        timerJob = scope.launch {
            while (isActive && _status.value == TimerStatus.RUNNING) {
                _elapsedMillis.value = SystemClock.elapsedRealtime() - startTimeMillis
                delay(16) // ~60fps UI update
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null

        val finalTime = SystemClock.elapsedRealtime() - startTimeMillis
        _elapsedMillis.value = finalTime
        _status.value = TimerStatus.STOPPED
        _showTouchPenaltyEditor.value = smartCubeManager.connectedDevice.value == null
        vibrate(80)

        var penalty = Penalty.NONE
        if (inspectionStartTime > 0) {
            val totalInspection = startTimeMillis - inspectionStartTime
            if (totalInspection >= 17000) {
                penalty = Penalty.DNF
            } else if (totalInspection >= 15000) {
                penalty = Penalty.PLUS_TWO
            }
            inspectionStartTime = 0L
        }

        recordSolve(finalTime, penalty)
        prepareNextScrambleAfterSolve()
    }

    private fun recordSolve(timeMillis: Long, penalty: Penalty) {
        val connected = smartCubeManager.connectedDevice.value
        val moves = _reconstructionMoves.value

        val solve = Solve(
            timeMillis = timeMillis,
            scramble = targetScramble,
            penalty = penalty,
            moveCount = if (connected != null) moves.size else null,
            reconstruction = if (moves.isNotEmpty()) moves.joinToString(" ") { it.notation } else null,
            cubeName = connected?.name,
            sessionName = solveRepository.currentSession.value
        )

        _lastSolve.value = solve
        solveRepository.addSolve(solve)
    }

    fun setLastSolvePenalty(penalty: Penalty) {
        val solve = _lastSolve.value ?: return
        if (solve.cubeName != null) return
        val updated = solve.copy(penalty = penalty)
        _lastSolve.value = updated
        solveRepository.updatePenalty(solve.id, penalty)
    }

    fun dismissTouchPenaltyEditor() {
        _showTouchPenaltyEditor.value = false
    }

    fun deleteLastSolve() {
        val solve = _lastSolve.value ?: return
        solveRepository.deleteSolve(solve.id)
        _lastSolve.value = null
        _elapsedMillis.value = 0L
        _showTouchPenaltyEditor.value = false
    }

    private fun onSmartCubeMove(move: CubeMove) {
        val settings = settingsRepository.settings.value
        val statusBeforeMove = _status.value
        val scrambleWasComplete = scrambleProgressTracker.isComplete()
        val preparingSolve = statusBeforeMove == TimerStatus.IDLE ||
            statusBeforeMove == TimerStatus.INSPECTION ||
            statusBeforeMove == TimerStatus.STOPPED
        if (preparingSolve) {
            if (recoveryActive) {
                appliedScrambleMoves.add(move)
                processRecoveryMove(move)
            } else {
                val progressBeforeMove = scrambleProgressTracker.currentProgress()
                val movesBeforeMove = appliedScrambleMoves.toList()
                _scrambleProgress.value = scrambleProgressTracker.onMove(move)
                appliedScrambleMoves.add(move)
                if (_scrambleProgress.value.any { it == ScrambleMoveProgress.WRONG }) {
                    recoveryCheckpointMoves = movesBeforeMove
                    recoveryProgressSnapshot = progressBeforeMove
                    recoveryPath = null
                    recoveryPathProgress = null
                    recoveryActive = true
                    scheduleScrambleRecovery()
                }
            }
        }

        // Update virtual cube state
        val updatedCube = _virtualCube.value.clone()
        _cubeTurnAnimation.value = CubeTurnAnimation(++cubeTurnSequence, updatedCube.clone(), move)
        updatedCube.applyMove(move)
        _virtualCube.value = updatedCube

        // Auto-start timer on first move if idle or in inspection
        if (settings.smartCubeAutoStart &&
            preparingSolve &&
            scrambleWasComplete
        ) {
            inspectionJob?.cancel()
            inspectionJob = null
            startTimer()
        }

        // If timer is running, record move into reconstruction
        if (_status.value == TimerStatus.RUNNING) {
            _reconstructionMoves.value = _reconstructionMoves.value + move

            // Auto-stop when solved
            if (settings.smartCubeAutoStop && updatedCube.isSolved()) {
                stopTimer()
            }
        }
    }

    private fun prepareNextScrambleAfterSolve() {
        clearRecoveryState()
        targetScramble = ScrambleGenerator.generate3x3()
        _currentScramble.value = targetScramble
        appliedScrambleMoves.clear()
        resetScrambleProgress()
    }

    private fun scheduleScrambleRecovery() {
        if (!recoveryActive) return
        if (recoveryJob?.isActive == true) return
        recoveryPath = null
        recoveryPathProgress = null
        recoveryJob = scope.launch {
            while (isActive) {
                val historySize = appliedScrambleMoves.size
                val moveSnapshot = appliedScrambleMoves.toList()
                val recoveryPath = withContext(Dispatchers.Default) {
                    recoverySolver.findPathToMoveState(recoveryCheckpointMoves, moveSnapshot)
                }
                if (historySize != appliedScrambleMoves.size || !recoveryActive) continue
                if (recoveryPath == null) return@launch

                this@TimerEngine.recoveryPath = recoveryPath
                recoveryPathProgress = RecoveryPathProgress(recoveryPath)
                if (recoveryPath.isEmpty()) {
                    restoreScrambleAfterRecovery()
                    return@launch
                }
                _currentScramble.value = recoveryPath.joinToString(" ") { it.notation }
                scrambleProgressTracker = ScrambleProgressTracker(_currentScramble.value)
                _scrambleProgress.value = scrambleProgressTracker.currentProgress()
                return@launch
            }
        }
    }

    private fun processRecoveryMove(move: CubeMove) {
        if (recoveryPath == null) return
        _scrambleProgress.value = scrambleProgressTracker.onMove(move)
        when (recoveryPathProgress?.consume(move)) {
            RecoveryMoveResult.COMPLETE -> restoreScrambleAfterRecovery()
            RecoveryMoveResult.ADVANCED -> Unit
            RecoveryMoveResult.DEVIATED, null -> scheduleScrambleRecovery()
        }
    }

    private fun restoreScrambleAfterRecovery() {
        appliedScrambleMoves.clear()
        appliedScrambleMoves.addAll(recoveryCheckpointMoves)
        recoveryJob?.cancel()
        recoveryJob = null
        recoveryPath = null
        recoveryPathProgress = null
        _currentScramble.value = targetScramble
        scrambleProgressTracker = ScrambleProgressTracker(targetScramble)
        scrambleProgressTracker.restoreProgress(recoveryProgressSnapshot)
        _scrambleProgress.value = scrambleProgressTracker.currentProgress()
        recoveryCheckpointMoves = emptyList()
        recoveryProgressSnapshot = emptyList()
        recoveryActive = false
    }

    private fun vibrate(millis: Long) {
        if (!settingsRepository.settings.value.hapticFeedback) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(millis)
            }
        } catch (e: Exception) {
            // Ignore vibration exceptions
        }
    }
}

