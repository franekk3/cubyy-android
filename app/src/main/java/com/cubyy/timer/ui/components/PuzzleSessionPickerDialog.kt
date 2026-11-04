package com.cubyy.timer.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cubyy.timer.data.PuzzleSession
import com.cubyy.timer.scramble.WcaEvent

@Composable
fun PuzzleSessionPickerDialog(
    sessions: List<PuzzleSession>,
    currentSession: PuzzleSession,
    restrictToThreeByThree: Boolean,
    title: String,
    onSelect: (PuzzleSession) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEventId by remember(currentSession.id, restrictToThreeByThree) {
        mutableStateOf(currentSession.eventId)
    }
    val availableEvents = if (restrictToThreeByThree) {
        WcaEvent.all.filter { it.threeByThreeHardware }
    } else WcaEvent.all
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Puzzle / event")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableEvents.forEach { event ->
                        FilterChip(
                            selected = event.id == selectedEventId,
                            onClick = { selectedEventId = event.id },
                            label = { Text(event.label) }
                        )
                    }
                }
                Text(availableEvents.firstOrNull { it.id == selectedEventId }?.label.orEmpty())
                sessions.filter { it.eventId == selectedEventId }.forEach { session ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = session.id == currentSession.id,
                            onClick = { onSelect(session) }
                        )
                        TextButton(
                            onClick = { onSelect(session) },
                            modifier = Modifier.weight(1f)
                        ) { Text(session.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(selectedEventId) }) { Text("New session") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
