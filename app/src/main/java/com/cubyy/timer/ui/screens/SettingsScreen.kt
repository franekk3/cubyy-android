package com.cubyy.timer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cubyy.timer.data.SettingsRepository
import com.cubyy.timer.data.SolveRepository
import com.cubyy.timer.data.CsTimerImportSummary
import com.cubyy.timer.data.CsTimerBackup
import com.cubyy.timer.data.CsTimerBackupImport

@Composable
fun SettingsScreen(settingsRepository: SettingsRepository, solveRepository: SolveRepository) {
    val settings by settingsRepository.settings.collectAsState()
    val sessions by solveRepository.sessions.collectAsState()
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<CsTimerBackupImport?>(null) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(solveRepository.exportCsTimerBackup())
                } ?: error("Could not open the selected file.")
            }.onSuccess {
                backupMessage = "csTimer backup exported."
            }.onFailure {
                backupMessage = "Export failed: ${it.message ?: "unknown error"}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Could not read the selected file.")
                json to CsTimerBackup.parse(json)
            }.onSuccess { (json, backup) ->
                val hasNameCollision = backup.sessions.any { it.name in sessions }
                if (hasNameCollision) {
                    pendingImport = backup
                    pendingImportJson = json
                } else {
                    backupMessage = solveRepository.importCsTimerBackup(json, mergeMatchingSessions = true).toMessage()
                }
            }.onFailure {
                backupMessage = "Import failed: ${it.message ?: "invalid csTimer backup"}"
            }
        }
    }

    fun importBackup(json: String, merge: Boolean) {
        runCatching {
            solveRepository.importCsTimerBackup(json, mergeMatchingSessions = merge)
        }.onSuccess { backupMessage = it.toMessage() }
            .onFailure { backupMessage = "Import failed: ${it.message ?: "unknown error"}" }
        pendingImport = null
        pendingImportJson = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .widthIn(max = 1000.dp)
            .fillMaxSize()
            .align(Alignment.TopCenter)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Customize your speedcubing timer and smart cube experience.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section: Timer & Inspection
        item {
            SettingSectionHeader("Timer & Inspection")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingToggleRow(
                        title = "WCA Inspection",
                        subtitle = "15-second countdown with haptic cues at 8s and 12s",
                        checked = settings.inspectionEnabled,
                        onCheckedChange = { settingsRepository.updateInspection(it) }
                    )

                    Column {
                        Text(
                            text = "Hold-to-Start Duration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Time required to hold before the timer becomes ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val holdOptions = listOf(150L to "Fast (0.15s)", 300L to "Normal (0.3s)", 500L to "Safe (0.5s)")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            holdOptions.forEachIndexed { index, (duration, label) ->
                                SegmentedButton(
                                    selected = settings.holdToStartMillis == duration,
                                    onClick = { settingsRepository.updateHoldMillis(duration) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = holdOptions.size)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    SettingToggleRow(
                        title = "Haptic Feedback",
                        subtitle = "Vibrate when ready, during inspection, and on solve stop",
                        checked = settings.hapticFeedback,
                        onCheckedChange = { settingsRepository.updateHaptic(it) }
                    )
                }
            }
        }

        // Section: Smart Cube Automation
        item {
            SettingSectionHeader("Smart Cube Automation")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingToggleRow(
                        title = "Auto-Start on First Turn",
                        subtitle = "Starts on the first solve turn after the scramble is complete",
                        checked = settings.smartCubeAutoStart,
                        onCheckedChange = { settingsRepository.updateSmartAutoStart(it) }
                    )

                    SettingToggleRow(
                        title = "Auto-Stop on Solved",
                        subtitle = "Instantly stops the timer the exact moment the cube is solved",
                        checked = settings.smartCubeAutoStop,
                        onCheckedChange = { settingsRepository.updateSmartAutoStop(it) }
                    )
                }
            }
        }

        item {
            SettingSectionHeader("Session Backups")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Import or export all sessions as a csTimer JSON backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { exportLauncher.launch("Cubyy-csTimer-backup.json") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Export") }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Import") }
                    }
                }
            }
        }

        // Section: Appearance & Theme
        item {
            SettingSectionHeader("Appearance")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ScaleSetting(
                        title = "3D cube size",
                        value = settings.cubeScale,
                        valueRange = 0.7f..1.3f,
                        onValueChange = settingsRepository::updateCubeScale
                    )
                    ScaleSetting(
                        title = "Scramble text size",
                        value = settings.scrambleFontScale,
                        valueRange = 0.75f..1.5f,
                        onValueChange = settingsRepository::updateScrambleFontScale
                    )
                    ScaleSetting(
                        title = "Solve time text size",
                        value = settings.timerFontScale,
                        valueRange = 0.75f..1.5f,
                        onValueChange = settingsRepository::updateTimerFontScale
                    )
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Material You dynamic colors adapt to your Android wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val themeModes = listOf("SYSTEM" to "System", "DARK" to "Dark", "LIGHT" to "Light")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeModes.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { settingsRepository.updateTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // Section: About & Supported Hardware
        item {
            SettingSectionHeader("About Cubyy")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Cubyy - Speedcubing Timer v1.0.0 BETA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Created with Kotlin & Jetpack Compose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Supported Hardware:\n• GAN: 356 i, i Play, i2, i3, i Carry, i Carry S, i Carry 2, GAN 12 ui, Monster Go 3Ai\n• MoYu: WeiLong AI, AI 2023\n• GiiKER: Super Cube i3s, i2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/franekk3/cubyy-android")
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(com.cubyy.timer.R.drawable.ic_github),
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View project on GitHub")
                    }
                }
            }
        }
    }

    val collisionBackup = pendingImport
    val collisionJson = pendingImportJson
    if (collisionBackup != null && collisionJson != null) {
        val collidingNames = collisionBackup.sessions.map { it.name }.filter { it in sessions }.distinct()
        AlertDialog(
            onDismissRequest = {
                pendingImport = null
                pendingImportJson = null
            },
            title = { Text("Matching sessions found") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The backup contains session names already in Cubyy:")
                    Text(collidingNames.joinToString(", "), fontWeight = FontWeight.SemiBold)
                    Text("csTimer session numbers are local to each backup, so names are used to identify sessions. Merge matching names and skip duplicate solves, or import them as separate sessions.")
                }
            },
            confirmButton = {
                TextButton(onClick = { importBackup(collisionJson, merge = true) }) { Text("Merge matching") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { importBackup(collisionJson, merge = false) }) { Text("Import separately") }
                    TextButton(onClick = {
                        pendingImport = null
                        pendingImportJson = null
                    }) { Text("Cancel") }
                }
            }
        )
    }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text("Session backup") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { backupMessage = null }) { Text("Done") } }
        )
    }
    }
}

@Composable
private fun ScaleSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = 0
        )
    }
}

private fun CsTimerImportSummary.toMessage(): String =
    "Imported $addedSolves solve(s) into $addedSessions new session(s). Skipped $skippedDuplicates duplicate solve(s)."

@Composable
private fun SettingSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

