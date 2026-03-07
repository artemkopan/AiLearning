package io.artemkopan.ai.sharedui.feature.taskstatemanager.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.feature.taskstatemanager.model.TaskStepUiModel
import io.artemkopan.ai.sharedui.feature.taskstatemanager.viewmodel.TaskStateManagerViewModel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun TaskStateManagerFeature(
    viewModel: TaskStateManagerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    if (!state.visible) return

    val accentColor = when {
        state.isPaused -> CyberpunkColors.Cyan
        else -> CyberpunkColors.Yellow
    }

    CyberpunkPanel(
        title = "TASK: ${state.currentPhase.uppercase()}",
        accentColor = accentColor,
        onHeaderClick = viewModel::onToggleExpanded,
        showContent = state.expanded,
        modifier = modifier,
        headerAction = {
            Text(
                text = state.progressLabel,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
            )
        },
    ) {
        Text(
            text = state.taskTitle,
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.TextPrimary,
        )

        Spacer(modifier = Modifier.height(4.dp))

        state.steps.forEach { step ->
            StepRow(step = step)
        }

        Spacer(modifier = Modifier.height(4.dp))

        PauseResumeButton(
            isPaused = state.isPaused,
            isPauseRequested = state.isPauseRequested,
            onPause = viewModel::onPauseTask,
            onResume = viewModel::onResumeTask,
        )
    }
}

@Composable
private fun StepRow(step: TaskStepUiModel) {
    val statusColor = stepStatusColor(step.status, step.isCurrent)
    val prefix = stepStatusPrefix(step.status, step.isCurrent)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (step.isCurrent) CyberpunkColors.TextPrimary else CyberpunkColors.TextMuted,
            )
            if (step.result.isNotBlank()) {
                Text(
                    text = step.result,
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberpunkColors.NeonGreen,
                )
            }
        }
    }
}

@Composable
private fun PauseResumeButton(
    isPaused: Boolean,
    isPauseRequested: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val (text, color, onClick) = when {
        isPauseRequested -> Triple("PAUSING...", CyberpunkColors.TextMuted, {})
        isPaused -> Triple("RESUME TASK", CyberpunkColors.NeonGreen, onResume)
        else -> Triple("PAUSE TASK", CyberpunkColors.Red, onPause)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = if (isPauseRequested) Modifier else Modifier.clickable(onClick = onClick),
    )
}

private fun stepStatusColor(status: String, isCurrent: Boolean): Color = when {
    status.equals("completed", ignoreCase = true) -> CyberpunkColors.NeonGreen
    isCurrent -> CyberpunkColors.Yellow
    else -> CyberpunkColors.TextMuted
}

private fun stepStatusPrefix(status: String, isCurrent: Boolean): String = when {
    status.equals("completed", ignoreCase = true) -> "[+]"
    status.equals("skipped", ignoreCase = true) -> "[-]"
    isCurrent -> "[>]"
    else -> "[ ]"
}
