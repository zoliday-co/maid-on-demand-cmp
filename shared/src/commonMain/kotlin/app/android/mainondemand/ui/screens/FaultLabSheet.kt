package app.android.mainondemand.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.android.mainondemand.data.fake.Fault
import app.android.mainondemand.data.fake.FaultMode
import app.android.mainondemand.ui.components.PillChip
import app.android.mainondemand.ui.components.ScreenPadding

/** Developer controls for the fake backend. Documented in the README under "Failure controls". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaultLabSheet(
    modes: Map<Fault, FaultMode>,
    onModeSelected: (Fault, FaultMode) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Failure lab",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = onReset) { Text("Reset all") }
            }
            Text(
                "Make the fake backend misbehave. “Next call” fails once and then recovers, so a retry succeeds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Fault.entries.forEach { fault ->
                Spacer(Modifier.height(20.dp))
                Text(fault.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    fault.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FaultMode.entries.forEach { mode ->
                        PillChip(
                            text = mode.label,
                            selected = modes[fault] == mode,
                            onClick = { onModeSelected(fault, mode) },
                        )
                    }
                }
            }
        }
    }
}
