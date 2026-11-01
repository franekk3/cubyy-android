package com.cubyy.timer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.model.VirtualCube
import com.cubyy.timer.data.Penalty
import com.cubyy.timer.data.SessionStats
import com.cubyy.timer.data.Solve
import com.cubyy.timer.data.SolveRepository
import com.cubyy.timer.data.StatisticsCalculator
import com.cubyy.timer.timer.CubeTurnAnimation
import com.cubyy.timer.ui.components.VirtualCubeView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(solveRepository: SolveRepository) {
    val context = LocalContext.current
    val currentSession by solveRepository.currentSession.collectAsState()
    val sessions by solveRepository.sessions.collectAsState()
    val allSolves by solveRepository.solves.collectAsState()

    val sessionSolves = remember(allSolves, currentSession) {
        allSolves.filter { it.sessionName == currentSession }
    }
    val sessionStats = remember(sessionSolves) {
        StatisticsCalculator.calculateSessionStats(sessionSolves)
    }

    var selectedSolve by remember { mutableStateOf<Solve?>(null) }
    var selectedStatsPage by remember { mutableIntStateOf(0) }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showSessionPicker by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var sessionPendingDelete by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .widthIn(max = 1320.dp)
            .fillMaxSize()
            .align(Alignment.TopCenter)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showSessionPicker = true }) {
                        Text("$currentSession  ·  ${sessionSolves.size} solves")
                    }
                    if (currentSession != "Default") {
                        IconButton(onClick = { sessionPendingDelete = currentSession }) {
                            Icon(Icons.Default.FolderDelete, contentDescription = "Delete session", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Row {
                IconButton(onClick = { showNewSessionDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Session")
                }
                IconButton(
                    onClick = { showClearConfirmDialog = true },
                    enabled = sessionSolves.isNotEmpty()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Session")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Charts & Stats", "Solves").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedStatsPage == index,
                    onClick = { selectedStatsPage = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 2)
                ) { Text(label) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (selectedStatsPage == 0) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SessionOverviewCard(stats = sessionStats)
                SolveTimeTrendChart(sessionSolves)
                SessionDetailStats(sessionStats, sessionSolves)
            }
        } else if (sessionSolves.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No solves in this session yet.\nStart solving on the Timer tab!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(sessionSolves.reversed()) { revIndex, solve ->
                    SolveHistoryItem(
                        index = sessionSolves.size - revIndex,
                        solve = solve,
                        onClick = { selectedSolve = solve }
                    )
                }
            }
        }
    }

    if (showSessionPicker) {
        AlertDialog(
            onDismissRequest = { showSessionPicker = false },
            title = { Text("Select session") },
            text = {
                Column {
                    sessions.forEach { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                solveRepository.switchSession(session)
                                showSessionPicker = false
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = session == currentSession, onClick = {
                                solveRepository.switchSession(session)
                                showSessionPicker = false
                            })
                            Text(session)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSessionPicker = false }) { Text("Done") } }
        )
    }

    sessionPendingDelete?.let { session ->
        val solveCount = allSolves.count { it.sessionName == session }
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = { Text("Delete session?") },
            text = { Text("Delete '$session' and all $solveCount solve(s) in it? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    solveRepository.deleteSession(session)
                    sessionPendingDelete = null
                }) { Text("Delete session") }
            },
            dismissButton = { TextButton(onClick = { sessionPendingDelete = null }) { Text("Cancel") } }
        )
    }

    // Solve Detail Bottom Sheet
    selectedSolve?.let { solve ->
        ModalBottomSheet(
            onDismissRequest = { selectedSolve = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            SolveDetailContent(
                solve = solve,
                onUpdatePenalty = { p ->
                    solveRepository.updatePenalty(solve.id, p)
                    selectedSolve = solve.copy(penalty = p)
                },
                onDelete = {
                    solveRepository.deleteSolve(solve.id)
                    selectedSolve = null
                },
                onShare = {
                    shareSolve(context, solve)
                }
            )
        }
    }

    // New Session Dialog
    if (showNewSessionDialog) {
        var sessionNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("New Session") },
            text = {
                OutlinedTextField(
                    value = sessionNameInput,
                    onValueChange = { sessionNameInput = it },
                    label = { Text("Session Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sessionNameInput.isNotBlank()) {
                            solveRepository.addSession(sessionNameInput.trim())
                            showNewSessionDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Session Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Session") },
            text = { Text("Are you sure you want to delete all ${sessionSolves.size} solves in '$currentSession'?") },
            confirmButton = {
                Button(
                    onClick = {
                        solveRepository.clearCurrentSession()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    }
}

@Composable
private fun SessionOverviewCard(stats: SessionStats) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatCell("Best Single", stats.bestSingle?.let { Solve.formatMillis(it) } ?: "-")
                StatCell("Best Ao5", stats.bestAo5?.let { Solve.formatMillis(it) } ?: "-")
                StatCell("Best Ao12", stats.bestAo12?.let { Solve.formatMillis(it) } ?: "-")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatCell("Current Ao5", stats.currentAo5?.let { Solve.formatMillis(it) } ?: "-")
                StatCell("Current Ao12", stats.currentAo12?.let { Solve.formatMillis(it) } ?: "-")
                StatCell("Mean", stats.sessionMean?.let { Solve.formatMillis(it) } ?: "-")
            }
        }
    }
}

@Composable
private fun SolveTimeTrendChart(solves: List<Solve>) {
    val plotted = solves.takeLast(40).filter { it.penalty != Penalty.DNF }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Solve trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (plotted.size < 2) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("Complete at least two non-DNF solves to see a trend.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val minimum = plotted.minOf { it.effectiveTimeMillis }
                val maximum = plotted.maxOf { it.effectiveTimeMillis }
                val range = (maximum - minimum).coerceAtLeast(1L).toFloat()
                val ao5Points = plotted.indices.mapNotNull { localIndex ->
                    val absoluteEnd = solves.size - plotted.size + localIndex + 1
                    if (absoluteEnd < 5) null else {
                        StatisticsCalculator.calculateAo5(solves.take(absoluteEnd))
                            ?.takeIf { it != Long.MAX_VALUE }
                    }
                }
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                val singleColor = MaterialTheme.colorScheme.primary
                val averageColor = MaterialTheme.colorScheme.tertiary
                Canvas(Modifier.fillMaxWidth().height(168.dp)) {
                    val left = 10f
                    val right = size.width - 10f
                    val top = 10f
                    val bottom = size.height - 12f
                    val yRange = range.coerceAtLeast(1000f)
                    repeat(4) { row ->
                        val y = top + (bottom - top) * row / 3f
                        drawLine(gridColor, Offset(left, y), Offset(right, y), 1f)
                    }
                    fun point(index: Int, time: Long, count: Int): Offset {
                        val x = left + (right - left) * index / (count - 1).coerceAtLeast(1)
                        val y = bottom - (time - minimum).toFloat() / yRange * (bottom - top)
                        return Offset(x, y.coerceIn(top, bottom))
                    }
                    val rawPath = Path()
                    plotted.forEachIndexed { index, solve ->
                        val point = point(index, solve.effectiveTimeMillis, plotted.size)
                        if (index == 0) rawPath.moveTo(point.x, point.y) else rawPath.lineTo(point.x, point.y)
                    }
                    drawPath(rawPath, singleColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

                    if (ao5Points.size > 1) {
                        val ao5Path = Path()
                        ao5Points.forEachIndexed { index, time ->
                            val point = point(index + plotted.size - ao5Points.size, time, plotted.size)
                            if (index == 0) ao5Path.moveTo(point.x, point.y) else ao5Path.lineTo(point.x, point.y)
                        }
                        drawPath(ao5Path, averageColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ChartLegend(MaterialTheme.colorScheme.primary, "Single")
                    ChartLegend(MaterialTheme.colorScheme.tertiary, "Ao5")
                }
            }
        }
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionDetailStats(stats: SessionStats, solves: List<Solve>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatCell("Ao3", StatisticsCalculator.calculateAo3(solves)?.let(Solve::formatMillis) ?: "-")
                StatCell("Ao5", stats.currentAo5?.let(Solve::formatMillis) ?: "-")
                StatCell("Ao12", stats.currentAo12?.let(Solve::formatMillis) ?: "-")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatCell("Ao100", StatisticsCalculator.calculateAo100(solves)?.let(Solve::formatMillis) ?: "-")
                StatCell("Mean", stats.sessionMean?.let(Solve::formatMillis) ?: "-")
                StatCell("Average TPS", stats.averageTps?.let { String.format("%.2f", it) } ?: "-")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatCell("Best", stats.bestSingle?.let(Solve::formatMillis) ?: "-")
                StatCell("Worst", stats.worstSingle?.let(Solve::formatMillis) ?: "-")
                StatCell("Solves", stats.solveCount.toString())
            }
        }
    }
}

@Composable
private fun StatCell(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

@Composable
private fun SolveHistoryItem(
    index: Int,
    solve: Solve,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$index",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.width(42.dp)
                )
                Text(
                    text = solve.formattedTime(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = when (solve.penalty) {
                        Penalty.DNF -> MaterialTheme.colorScheme.error
                        Penalty.PLUS_TWO -> MaterialTheme.colorScheme.tertiary
                        Penalty.NONE -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                solve.turnsPerSecond?.let { tps ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "${String.format("%.1f", tps)} TPS",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                val dateStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(solve.timestamp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SolveDetailContent(
    solve: Solve,
    onUpdatePenalty: (Penalty) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val replayMoves = remember(solve.id, solve.reconstruction) {
        solve.reconstruction.orEmpty().split("\\s+".toRegex()).mapNotNull(CubeMove::fromNotation)
    }
    var replayCube by remember(solve.id, solve.scramble) {
        mutableStateOf(VirtualCube().apply { applyScramble(solve.scramble) })
    }
    var replayIndex by remember(solve.id) { mutableIntStateOf(0) }
    var isReplaying by remember(solve.id) { mutableStateOf(false) }
    var replayAnimation by remember(solve.id) { mutableStateOf<CubeTurnAnimation?>(null) }

    LaunchedEffect(isReplaying, solve.id) {
        while (isReplaying && replayIndex < replayMoves.size) {
            val move = replayMoves[replayIndex]
            val before = replayCube.clone()
            replayAnimation = CubeTurnAnimation(replayIndex.toLong() + 1L, before.clone(), move)
            before.applyMove(move)
            replayCube = before
            replayIndex++
            delay(340)
        }
        if (replayIndex >= replayMoves.size) isReplaying = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Time & Date
        Text(
            text = solve.formattedTime(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        val fullDate = SimpleDateFormat("MMM d, yyyy  HH:mm:ss", Locale.getDefault()).format(Date(solve.timestamp))
        Text(
            text = fullDate,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Penalty Selector Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Penalty.entries.forEach { p ->
                val selected = solve.penalty == p
                FilledTonalButton(
                    onClick = { onUpdatePenalty(p) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    colors = if (selected) {
                        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text(p.name.replace("_", " "))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scramble section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Scramble", style = MaterialTheme.typography.labelMedium)
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Scramble", solve.scramble))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Scramble", modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = solve.scramble,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }

        // Reconstruction (if smart cube was used)
        solve.reconstruction?.let { reco ->
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Reconstruction (${solve.moveCount} moves • ${String.format("%.2f", solve.turnsPerSecond ?: 0.0)} TPS)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reco,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    VirtualCubeView(
                        virtualCube = replayCube,
                        animation = replayAnimation,
                        modifier = Modifier.fillMaxWidth(0.55f).align(Alignment.CenterHorizontally)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (replayIndex >= replayMoves.size) {
                                replayCube = VirtualCube().apply { applyScramble(solve.scramble) }
                                replayIndex = 0
                                replayAnimation = null
                            }
                            isReplaying = !isReplaying
                        }, enabled = replayMoves.isNotEmpty()) {
                            Icon(
                                imageVector = if (isReplaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isReplaying) "Pause replay" else "Play replay"
                            )
                        }
                        IconButton(onClick = {
                            isReplaying = false
                            replayCube = VirtualCube().apply { applyScramble(solve.scramble) }
                            replayIndex = 0
                            replayAnimation = null
                        }, enabled = replayMoves.isNotEmpty()) {
                            Icon(Icons.Default.Replay, contentDescription = "Restart replay")
                        }
                        Text(
                            "$replayIndex / ${replayMoves.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Keep delete below the replay area so the solve detail can be reviewed first.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share")
            }

        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete solve")
        }
    }
}

private fun shareSolve(context: Context, solve: Solve) {
    val text = buildString {
        append("Cubyy Speedcube Solve\n")
        append("Time: ${solve.formattedTime()}\n")
        solve.turnsPerSecond?.let {
            append("Moves: ${solve.moveCount} (${String.format("%.2f", it)} TPS)\n")
        }
        append("Scramble: ${solve.scramble}\n")
        solve.reconstruction?.let {
            append("Reconstruction: $it\n")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Solve"))
}

