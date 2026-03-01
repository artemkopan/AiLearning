package io.artemkopan.ai.sharedui.feature.conversationcolumn.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedcontract.BranchingContextConfigDto
import io.artemkopan.ai.sharedui.core.session.AgentMessageState
import io.artemkopan.ai.sharedui.core.session.QueuedMessageState
import io.artemkopan.ai.sharedui.core.session.QueuedMessageStatus
import io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel.ConversationColumnViewModel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkTextField
import io.artemkopan.ai.sharedui.ui.component.StatusPanel
import io.artemkopan.ai.sharedui.ui.component.SubmitButton
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun ConversationColumnFeature(
    viewModel: ConversationColumnViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val agent = state.agent ?: return
    val displayMessages = remember(agent.messages, state.queuedMessages) {
        agent.messages + state.queuedMessages.map { it.asDisplayMessage() }
    }
    val queuedIds = remember(state.queuedMessages) { state.queuedMessages.map { it.id }.toSet() }

    var messageInputValue by remember(agent.id.value) {
        mutableStateOf(
            TextFieldValue(
                text = agent.draftMessage,
                selection = TextRange(agent.draftMessage.length),
            )
        )
    }

    LaunchedEffect(agent.id.value, agent.draftMessage) {
        if (messageInputValue.text != agent.draftMessage) {
            messageInputValue = TextFieldValue(
                text = agent.draftMessage,
                selection = TextRange(agent.draftMessage.length),
            )
        }
    }

    val slashTokenBounds = remember(messageInputValue) {
        findSlashTokenBounds(messageInputValue)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val scrollState = rememberScrollState()
        val latestMessageSignature = displayMessages.lastOrNull()?.let { message ->
            "${message.id}:${message.status}:${message.text.length}"
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
                    if (displayMessages.isEmpty()) {
                        Text(
                            text = "NO MESSAGES YET",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberpunkColors.TextMuted,
                        )
                    } else {
                        val isBranching = agent.contextConfig is BranchingContextConfigDto
                        displayMessages.forEach { message ->
                            MessageRow(
                                message = message,
                                isQueuedLocal = queuedIds.contains(message.id),
                                isBranchingActive = isBranching,
                                onStop = viewModel::onStopQueue,
                                onBranch = { messageId ->
                                    viewModel.onCreateBranch(
                                        checkpointMessageId = messageId,
                                        fallbackMessageId = message.id,
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
            value = messageInputValue,
            onValueChange = { value ->
                messageInputValue = value
                viewModel.onMessageInputChanged(value.text)
            },
            label = "// MESSAGE",
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
        )

        SlashCommandPopup(
            visible = slashTokenBounds != null,
            agents = state.allAgents,
            onInsertToken = { token ->
                val updated = insertSlashTokenAtCaret(messageInputValue, token, slashTokenBounds)
                messageInputValue = updated
                viewModel.onMessageInputChanged(updated.text)
            },
            onDismiss = {},
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

        if (agent.isLoading || state.queuedMessages.isNotEmpty()) {
            val status = when {
                agent.isLoading && state.queuedMessages.isNotEmpty() -> "${agent.status} / queued ${state.queuedMessages.size}"
                agent.isLoading -> agent.status
                else -> "queued ${state.queuedMessages.size}"
            }
            StatusPanel(
                status = status,
                modifier = Modifier.fillMaxWidth(),
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

private data class SlashTokenBounds(
    val start: Int,
    val endExclusive: Int,
)

private fun findSlashTokenBounds(value: TextFieldValue): SlashTokenBounds? {
    if (!value.selection.collapsed) return null
    val selectionStart = value.selection.start
    val text = value.text
    if (selectionStart < 0 || selectionStart > text.length) return null

    var tokenStart = selectionStart
    while (tokenStart > 0 && !text[tokenStart - 1].isWhitespace()) {
        tokenStart -= 1
    }

    var tokenEnd = selectionStart
    while (tokenEnd < text.length && !text[tokenEnd].isWhitespace()) {
        tokenEnd += 1
    }

    if (tokenStart >= text.length) return null
    if (text[tokenStart] != '/') return null
    return SlashTokenBounds(start = tokenStart, endExclusive = tokenEnd)
}

private fun insertSlashTokenAtCaret(
    value: TextFieldValue,
    token: String,
    slashTokenBounds: SlashTokenBounds?,
): TextFieldValue {
    val text = value.text
    return if (slashTokenBounds != null) {
        val trailingSpace = when {
            slashTokenBounds.endExclusive >= text.length -> " "
            text[slashTokenBounds.endExclusive].isWhitespace() -> ""
            else -> " "
        }
        val replacement = token + trailingSpace
        val updated = text.replaceRange(slashTokenBounds.start, slashTokenBounds.endExclusive, replacement)
        val cursor = slashTokenBounds.start + replacement.length
        TextFieldValue(text = updated, selection = TextRange(cursor))
    } else {
        val selectionStart = value.selection.start.coerceIn(0, text.length)
        val selectionEnd = value.selection.end.coerceIn(0, text.length)
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        val leadingSpace = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
        val trailingSpace = if (end < text.length && !text[end].isWhitespace()) " " else ""
        val replacement = "$leadingSpace$token$trailingSpace"
        val updated = text.replaceRange(start, end, replacement)
        val cursor = start + replacement.length
        TextFieldValue(text = updated, selection = TextRange(cursor))
    }
}

private fun QueuedMessageState.asDisplayMessage(): AgentMessageState {
    return AgentMessageState(
        id = id,
        role = AgentMessageRoleDto.USER,
        text = text,
        status = when (status) {
            QueuedMessageStatus.QUEUED -> "queued"
            QueuedMessageStatus.SENDING -> "sending"
        },
        createdAt = createdAt,
    )
}
