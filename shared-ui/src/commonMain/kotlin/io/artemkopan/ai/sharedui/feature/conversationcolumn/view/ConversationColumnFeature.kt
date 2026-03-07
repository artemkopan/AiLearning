package io.artemkopan.ai.sharedui.feature.conversationcolumn.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedcontract.BranchingContextConfigDto
import io.artemkopan.ai.sharedui.core.session.AgentMessageState
import io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel.ConversationColumnViewModel
import io.artemkopan.ai.sharedui.feature.taskstatemanager.view.TaskStateManagerFeature
import io.artemkopan.ai.sharedui.feature.taskstatemanager.viewmodel.TaskStateManagerViewModel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkTextField
import io.artemkopan.ai.sharedui.ui.component.StatusPanel
import io.artemkopan.ai.sharedui.ui.component.SubmitButton
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun ConversationColumnFeature(
    viewModel: ConversationColumnViewModel,
    taskStateManagerViewModel: TaskStateManagerViewModel?,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val agent = state.agent ?: return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val scrollState = rememberScrollState()
        val latestMessageSignature = state.displayMessages.lastOrNull()?.let { message ->
            "${message.id}:${message.message.status}:${message.message.text.length}"
        }

        LaunchedEffect(agent.id.value, latestMessageSignature) {
            scrollState.scrollTo(scrollState.maxValue)
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
                        val isBranching = agent.contextConfig is BranchingContextConfigDto
                        state.displayMessages.forEach { displayMessage ->
                            MessageRow(
                                message = displayMessage.message,
                                isQueuedLocal = displayMessage.isQueuedLocal,
                                isBranchingActive = isBranching,
                                onStop = viewModel::onStopQueue,
                                onBranch = { messageId ->
                                    viewModel.onCreateBranch(
                                        checkpointMessageId = messageId,
                                        fallbackMessageId = displayMessage.message.id,
                                    )
                                },
                            )
                        }
                    }
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

        CyberpunkTextField(
            value = state.inputValue,
            onValueChange = viewModel::onMessageInputChanged,
            label = "// MESSAGE",
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

        if (taskStateManagerViewModel != null) {
            TaskStateManagerFeature(
                viewModel = taskStateManagerViewModel,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.statusText?.let { status ->
            StatusPanel(
                status = status,
                modifier = Modifier.fillMaxWidth(),
                showSpinner = agent.isLoading,
            )
        }
    }
}

@Composable
private fun MessageRow(
    message: AgentMessageState,
    isQueuedLocal: Boolean,
    isBranchingActive: Boolean,
    onStop: () -> Unit,
    onBranch: (messageId: String) -> Unit,
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
                if (isBranchingActive && !isQueuedLocal) {
                    Text(
                        text = "BRANCH",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberpunkColors.Yellow,
                        modifier = Modifier.clickable { onBranch(message.id) },
                    )
                }

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

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(
            thickness = 1.dp,
            color = CyberpunkColors.BorderDark,
        )
    }
}
