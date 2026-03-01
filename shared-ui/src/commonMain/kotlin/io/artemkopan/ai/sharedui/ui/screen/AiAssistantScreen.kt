package io.artemkopan.ai.sharedui.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedui.state.*
import io.artemkopan.ai.sharedui.ui.component.*
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkTheme

@Composable
fun AiAssistantScreen(
    viewModel: AppViewModel,
) {
    val uiState by viewModel.state.collectAsState()

    AiAssistantContent(
        state = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
private fun AiAssistantContent(
    state: UiState,
    onAction: (UiAction) -> Unit,
) {
    val activeAgent = state.activeAgentId?.let { state.agents[it] }
    val orderedAgents = state.agentOrder.mapNotNull { state.agents[it] }
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    CyberpunkTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.isCtrlPressed && event.key == Key.Enter -> {
                            onAction(UiAction.Submit)
                            true
                        }

                        event.isAltPressed && event.key == Key.DirectionDown -> {
                            nextAgentId(state.agentOrder, state.activeAgentId)?.let {
                                onAction(UiAction.SelectAgent(it))
                            }
                            true
                        }

                        event.isAltPressed && event.key == Key.DirectionUp -> {
                            previousAgentId(state.agentOrder, state.activeAgentId)?.let {
                                onAction(UiAction.SelectAgent(it))
                            }
                            true
                        }

                        event.isAltPressed && event.key == Key.N -> {
                            onAction(UiAction.CreateAgent)
                            true
                        }

                        else -> false
                    }
                },
            color = CyberpunkColors.DarkBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScreenHeader()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AgentSidePanel(
                        agents = orderedAgents,
                        activeAgentId = state.activeAgentId,
                        onAgentSelected = { onAction(UiAction.SelectAgent(it)) },
                        onAgentClosed = { onAction(UiAction.CloseAgent(it)) },
                        onNewAgentClicked = { onAction(UiAction.CreateAgent) },
                        modifier = Modifier
                            .width(180.dp)
                            .fillMaxHeight(),
                    )

                    if (activeAgent != null) {
                        CenterConversationColumn(
                            agent = activeAgent,
                            allAgents = orderedAgents,
                            queuedMessages = state.queuedByAgent[activeAgent.id].orEmpty(),
                            onAction = onAction,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }

                    if (activeAgent != null) {
                        SettingsColumn(
                            agent = activeAgent,
                            agentConfig = state.agentConfig,
                            contextTotalTokensLabel = state.contextTotalTokensByAgent[activeAgent.id] ?: "n/a",
                            contextLeftLabel = state.contextLeftByAgent[activeAgent.id] ?: "n/a",
                            onAction = onAction,
                            modifier = Modifier
                                .width(220.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }

        state.errorPopup?.let { error ->
            ErrorDialog(
                error = error,
                onDismiss = { onAction(UiAction.DismissError) },
            )
        }
    }
}

@Composable
private fun ScreenHeader() {
    Column {
        Text(
            text = "AI assistant",
            style = MaterialTheme.typography.headlineSmall,
            color = CyberpunkColors.Yellow,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            thickness = 2.dp,
            color = CyberpunkColors.Yellow.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun CenterConversationColumn(
    agent: AgentState,
    allAgents: List<AgentState>,
    queuedMessages: List<QueuedMessageState>,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayMessages = remember(agent.messages, queuedMessages) {
        agent.messages + queuedMessages.map { it.asDisplayMessage() }
    }
    val queuedIds = remember(queuedMessages) { queuedMessages.map { it.id }.toSet() }

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
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
                        displayMessages.forEach { message ->
                            MessageRow(
                                message = message,
                                isQueuedLocal = queuedIds.contains(message.id),
                                onStop = {
                                    onAction(UiAction.StopQueue)
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
            value = agent.draftMessage,
            onValueChange = { onAction(UiAction.MessageInputChanged(it)) },
            label = "// MESSAGE",
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
        )
        SlashCommandPopup(
            visible = agent.draftMessage.trimEnd().endsWith("/"),
            agents = allAgents,
            onInsertToken = { token -> onAction(UiAction.InsertSlashToken(token)) },
            onDismiss = {},
            modifier = Modifier.fillMaxWidth(),
        )

        SubmitButton(
            isLoading = agent.isLoading,
            queuedCount = queuedMessages.size,
            onClick = { onAction(UiAction.Submit) },
        )

        if (queuedMessages.isNotEmpty()) {
            Text(
                text = "STOP QUEUE",
                style = MaterialTheme.typography.labelMedium,
                color = CyberpunkColors.Red,
                modifier = Modifier.clickable { onAction(UiAction.StopQueue) },
            )
        }

        if (agent.isLoading || queuedMessages.isNotEmpty()) {
            val status = when {
                agent.isLoading && queuedMessages.isNotEmpty() -> "${agent.status} / queued ${queuedMessages.size}"
                agent.isLoading -> agent.status
                else -> "queued ${queuedMessages.size}"
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
    onStop: () -> Unit,
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

@Composable
private fun SettingsColumn(
    agent: AgentState,
    agentConfig: AgentConfigDto?,
    contextTotalTokensLabel: String,
    contextLeftLabel: String,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentModeSelector(
                selected = agent.agentMode,
                onModeSelected = { onAction(UiAction.AgentModeChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            ConfigPanel(
                model = agent.model,
                onModelChanged = { onAction(UiAction.ModelChanged(it)) },
                maxOutputTokens = agent.maxOutputTokens,
                onMaxOutputTokensChanged = { onAction(UiAction.MaxOutputTokensChanged(it)) },
                temperature = agent.temperature,
                onTemperatureChanged = { onAction(UiAction.TemperatureChanged(it)) },
                stopSequences = agent.stopSequences,
                onStopSequencesChanged = { onAction(UiAction.StopSequencesChanged(it)) },
                models = agentConfig?.models.orEmpty(),
                temperaturePlaceholder = agentConfig?.let {
                    "${it.temperatureMin} – ${it.temperatureMax}"
                } ?: "0.0 – 2.0",
                modifier = Modifier.fillMaxWidth(),
            )
            ContextConfigPanel(
                contextConfig = agent.contextConfig,
                onStrategyChanged = { onAction(UiAction.ContextStrategyChanged(it)) },
                onRecentMessagesChanged = { onAction(UiAction.ContextRecentMessagesChanged(it)) },
                onSummarizeEveryChanged = { onAction(UiAction.ContextSummarizeEveryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            RuntimeInfoPanel(
                agent = agent,
                contextTotalTokensLabel = contextTotalTokensLabel,
                contextLeftLabel = contextLeftLabel,
                modifier = Modifier.fillMaxWidth(),
            )
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

@Composable
private fun RuntimeInfoPanel(
    agent: AgentState,
    contextTotalTokensLabel: String,
    contextLeftLabel: String,
    modifier: Modifier = Modifier,
) {
    val latestAssistant = agent.messages.lastOrNull {
        it.role == AgentMessageRoleDto.ASSISTANT && it.status.equals("done", ignoreCase = true)
    }

    CyberpunkPanel(
        title = "RUNTIME",
        accentColor = CyberpunkColors.Cyan,
        modifier = modifier,
    ) {
        Text(
            text = "out tokens: ${latestAssistant?.usage?.outputTokens?.toString() ?: "n/a"}",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.Cyan,
        )
        Text(
            text = "context total: $contextTotalTokensLabel",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.TextPrimary,
        )
        Text(
            text = "context left: $contextLeftLabel",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.TextPrimary,
        )
        Text(
            text = "api duration: ${latestAssistant?.latencyMs?.let { "$it ms" } ?: "n/a"}",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.TextMuted,
        )
    }
}

private fun nextAgentId(order: List<AgentId>, activeAgentId: AgentId?): AgentId? {
    if (order.isEmpty()) return null
    val currentIndex = order.indexOf(activeAgentId).takeIf { it >= 0 } ?: 0
    return order[(currentIndex + 1) % order.size]
}

private fun previousAgentId(order: List<AgentId>, activeAgentId: AgentId?): AgentId? {
    if (order.isEmpty()) return null
    val currentIndex = order.indexOf(activeAgentId).takeIf { it >= 0 } ?: 0
    return order[(currentIndex - 1 + order.size) % order.size]
}

private fun QueuedMessageState.asDisplayMessage(): AgentMessageState {
    return AgentMessageState(
        id = id,
        role = AgentMessageRoleDto.USER,
        text = text,
        status = status.name.lowercase(),
        createdAt = createdAt,
    )
}
