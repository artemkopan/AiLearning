package io.artemkopan.ai.sharedui.feature.conversationcolumn.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedcontract.AgentMessageTypeDto
import io.artemkopan.ai.sharedui.core.session.AgentMessageState
import io.artemkopan.ai.sharedui.core.session.TaskState
import io.artemkopan.ai.sharedui.core.session.TaskValidationCheckState
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.PhaseProgressStyle
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.PhaseProgressUiState
import io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel.ConversationColumnViewModel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkTextField
import io.artemkopan.ai.sharedui.ui.component.StatusPanel
import io.artemkopan.ai.sharedui.ui.component.SubmitButton
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors
import kotlinx.coroutines.flow.first

@Composable
fun ConversationColumnFeature(
    viewModel: ConversationColumnViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val agent = state.agent ?: return
    val taskUi = state.taskUi

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val scrollState = rememberScrollState()

        val scrollTrigger = remember(state.displayMessages, state.activeTask?.currentPhase) {
            val last = state.displayMessages.lastOrNull()
            "${agent.id.value}:${last?.id}:${last?.message?.status}:${last?.message?.text?.length}:${state.activeTask?.currentPhase}"
        }

        LaunchedEffect(scrollTrigger) {
            val maxValue = snapshotFlow { scrollState.maxValue }.first { it > 0 }
            scrollState.animateScrollTo(maxValue)
        }

        CyberpunkPanel(
            title = "MESSAGES",
            accentColor = CyberpunkColors.NeonGreen,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.displayMessages.isEmpty()) {
                        Text(
                            text = "NO MESSAGES YET",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberpunkColors.TextMuted,
                        )
                    } else {
                        val lastMessageId = state.displayMessages.lastOrNull()?.message?.id
                        state.displayMessages.forEach { displayMessage ->
                            MessageRow(
                                message = displayMessage.message,
                                isQueuedLocal = displayMessage.isQueuedLocal,
                                isLastMessage = displayMessage.message.id == lastMessageId,
                                activeTask = state.activeTask,
                                showPlanActions = taskUi.showPlanActions,
                                onStop = viewModel::onStopQueue,
                                onAcceptPlan = viewModel::onAcceptPlan,
                                onRejectPlan = { viewModel.onRejectPlan() },
                            )
                        }
                    }

                    TaskPhaseProgress(phaseProgress = taskUi.phaseProgress)
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    style = LocalScrollbarStyle.current.copy(
                        unhoverColor = CyberpunkColors.TextPrimary.copy(alpha = 0.5f),
                        hoverColor = CyberpunkColors.TextPrimary,
                    ),
                )
            }
        }

        taskUi.answerPrompt?.let { prompt ->
            Text(
                text = "ANSWER REQUIRED: $prompt",
                style = MaterialTheme.typography.labelMedium,
                color = CyberpunkColors.Cyan,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }

        CyberpunkTextField(
            value = state.inputValue,
            onValueChange = viewModel::onMessageInputChanged,
            label = taskUi.inputLabel,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
        )

        CommandPalette(
            visible = state.commandPalette.visible,
            items = state.commandPalette.items,
            onSelect = viewModel::onCommandSelected,
            onDismiss = viewModel::onCommandDismissed,
            modifier = Modifier.fillMaxWidth(),
        )

        SubmitButton(
            isLoading = agent.isLoading,
            queuedCount = state.queuedMessages.size,
            onClick = viewModel::onSubmit,
        )

        if (state.queuedMessages.isNotEmpty()) {
            Text(
                text = "STOP QUEUE",
                style = MaterialTheme.typography.labelMedium,
                color = CyberpunkColors.Red,
                modifier = Modifier.clickable(onClick = viewModel::onStopQueue),
            )
        }

        state.statusText?.let { status ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPanel(
                    status = status,
                    modifier = Modifier.weight(1f),
                    showSpinner = taskUi.showSpinner,
                )
            }
        }
    }
}

@Composable
private fun TaskPhaseProgress(phaseProgress: PhaseProgressUiState?) {
    if (phaseProgress == null) return

    val color = when (phaseProgress.style) {
        PhaseProgressStyle.AWAITING_INPUT -> CyberpunkColors.Cyan
        PhaseProgressStyle.PAUSED -> CyberpunkColors.Yellow
        PhaseProgressStyle.ACTIVE -> CyberpunkColors.NeonGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = phaseProgress.label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun MessageRow(
    message: AgentMessageState,
    isQueuedLocal: Boolean,
    isLastMessage: Boolean,
    activeTask: TaskState?,
    showPlanActions: Boolean,
    onStop: () -> Unit,
    onAcceptPlan: () -> Unit,
    onRejectPlan: () -> Unit,
) {
    val roleColor = when (message.role) {
        AgentMessageRoleDto.USER -> if (isQueuedLocal) CyberpunkColors.Cyan else CyberpunkColors.Yellow
        AgentMessageRoleDto.ASSISTANT -> CyberpunkColors.NeonGreen
    }
    val messageColor = if (isQueuedLocal) CyberpunkColors.TextMuted else CyberpunkColors.TextPrimary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${message.role.name.lowercase()} - ${message.status}",
                style = MaterialTheme.typography.labelMedium,
                color = roleColor,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isQueuedLocal &&
                    message.role == AgentMessageRoleDto.ASSISTANT &&
                    message.status.equals("processing", ignoreCase = true)
                ) {
                    Text(
                        text = "STOP",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberpunkColors.Red,
                        modifier = Modifier.clickable(onClick = onStop),
                    )
                }
            }
        }

        Text(
            text = message.text.ifBlank { "..." },
            style = MaterialTheme.typography.bodyMedium,
            color = messageColor,
        )

        if (!isQueuedLocal &&
            message.role == AgentMessageRoleDto.ASSISTANT &&
            message.status.equals("done", ignoreCase = true)
        ) {
            val provider = message.provider.orEmpty().ifBlank { "n/a" }
            val model = message.model.orEmpty().ifBlank { "n/a" }
            val tokens = message.usage?.let { usage ->
                "in ${usage.inputTokens}, out ${usage.outputTokens}, total ${usage.totalTokens}"
            } ?: "n/a"
            val apiDuration = message.latencyMs?.let { "$it ms" } ?: "n/a"
            Text(
                text = "info: provider=$provider, model=$model, tokens=$tokens, api duration=$apiDuration",
                style = MaterialTheme.typography.bodySmall,
                color = CyberpunkColors.Cyan,
            )
        }

        if (isLastMessage && !isQueuedLocal && message.status.equals("done", ignoreCase = true)) {
            when (message.messageType) {
                AgentMessageTypeDto.REVIEW -> {
                    ReviewMessageContent(
                        activeTask = activeTask,
                        showActions = showPlanActions,
                        onAccept = onAcceptPlan,
                        onReject = onRejectPlan,
                    )
                }
                AgentMessageTypeDto.EXECUTION_CONFIRMATION -> {
                    if (showPlanActions) {
                        ApproveButton(onAccept = onAcceptPlan)
                    }
                }
                else -> {
                    if (activeTask != null && activeTask.planSteps.isNotEmpty()) {
                        PlanStepsDisplay(planSteps = activeTask.planSteps)
                    }
                }
            }
        }

        if (activeTask != null && activeTask.validationChecks.isNotEmpty() && isLastMessage) {
            ValidationChecksDisplay(checks = activeTask.validationChecks)
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(
            thickness = 1.dp,
            color = CyberpunkColors.BorderDark,
        )
    }
}

@Composable
private fun ReviewMessageContent(
    activeTask: TaskState?,
    showActions: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!activeTask?.goal.isNullOrBlank()) {
            Text(
                text = "GOAL: ${activeTask!!.goal}",
                style = MaterialTheme.typography.labelMedium,
                color = CyberpunkColors.Yellow,
            )
        }

        if (activeTask != null && activeTask.planSteps.isNotEmpty()) {
            PlanStepsDisplay(planSteps = activeTask.planSteps)
        }

        val hasQuestion = !activeTask?.questionForUser.isNullOrBlank()

        if (hasQuestion) {
            Text(
                text = "Q: ${activeTask!!.questionForUser}",
                style = MaterialTheme.typography.bodySmall,
                color = CyberpunkColors.Cyan,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (showActions) {
                Text(
                    text = "ANSWER THE QUESTION ABOVE BEFORE APPROVING",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberpunkColors.Yellow,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (showActions) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = "[APPROVE]",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberpunkColors.NeonGreen,
                    modifier = Modifier.clickable(onClick = onAccept),
                )
                Text(
                    text = "[REJECT]",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberpunkColors.Red,
                    modifier = Modifier.clickable(onClick = onReject),
                )
            }
        }
    }
}

@Composable
private fun ApproveButton(onAccept: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Text(
            text = "[APPROVE]",
            style = MaterialTheme.typography.labelMedium,
            color = CyberpunkColors.NeonGreen,
            modifier = Modifier.clickable(onClick = onAccept),
        )
    }
}

@Composable
private fun PlanStepsDisplay(planSteps: List<String>) {
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "PLAN:",
            style = MaterialTheme.typography.labelSmall,
            color = CyberpunkColors.Yellow,
        )
        planSteps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodySmall,
                color = CyberpunkColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun ValidationChecksDisplay(checks: List<TaskValidationCheckState>) {
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "VALIDATION:",
            style = MaterialTheme.typography.labelSmall,
            color = CyberpunkColors.Cyan,
        )
        checks.forEach { check ->
            val color = if (check.passed) CyberpunkColors.NeonGreen else CyberpunkColors.Red
            Text(
                text = "${if (check.passed) "✓" else "✗"} ${check.name}",
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}
