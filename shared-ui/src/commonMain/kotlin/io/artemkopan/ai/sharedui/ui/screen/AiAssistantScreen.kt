package io.artemkopan.ai.sharedui.ui.screen

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedui.state.AgentMessageState
import io.artemkopan.ai.sharedui.state.AgentState
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.state.UiAction
import io.artemkopan.ai.sharedui.state.UiState
import io.artemkopan.ai.sharedui.ui.component.AgentModeSelector
import io.artemkopan.ai.sharedui.ui.component.AgentSidePanel
import io.artemkopan.ai.sharedui.ui.component.ConfigPanel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkTextField
import io.artemkopan.ai.sharedui.ui.component.ErrorDialog
import io.artemkopan.ai.sharedui.ui.component.StatusPanel
import io.artemkopan.ai.sharedui.ui.component.SubmitButton
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkTheme
import kotlin.math.max

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

    CyberpunkTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
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
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val scrollState = rememberScrollState()
        val latestMessageSignature = agent.messages.lastOrNull()?.let { message ->
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
                    if (agent.messages.isEmpty()) {
                        Text(
                            text = "NO MESSAGES YET",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberpunkColors.TextMuted,
                        )
                    } else {
                        agent.messages.forEach { message ->
                            MessageRow(
                                message = message,
                                onStop = {
                                    onAction(UiAction.StopMessage(message.id))
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
        )

        SubmitButton(
            isLoading = agent.isLoading,
            onClick = { onAction(UiAction.Submit) },
        )

        if (agent.isLoading) {
            StatusPanel(
                status = agent.status,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MessageRow(
    message: AgentMessageState,
    onStop: () -> Unit,
) {
    val roleColor = when (message.role) {
        AgentMessageRoleDto.USER -> CyberpunkColors.Yellow
        AgentMessageRoleDto.ASSISTANT -> CyberpunkColors.NeonGreen
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${message.role.name.lowercase()}  [${message.status}]",
                style = MaterialTheme.typography.labelMedium,
                color = roleColor,
            )

            if (message.role == AgentMessageRoleDto.ASSISTANT && message.status.equals("processing", ignoreCase = true)) {
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
            color = CyberpunkColors.TextPrimary,
        )

        if (message.role == AgentMessageRoleDto.ASSISTANT && message.status.equals("done", ignoreCase = true)) {
            val provider = message.provider.orEmpty()
            val model = message.model.orEmpty()
            if (provider.isNotBlank() || model.isNotBlank()) {
                Text(
                    text = "provider: $provider  //  model: $model",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberpunkColors.TextMuted,
                )
            }
            message.usage?.let { usage ->
                Text(
                    text = "tokens  in: ${usage.inputTokens}  out: ${usage.outputTokens}  total: ${usage.totalTokens}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberpunkColors.Cyan,
                )
            }
            message.latencyMs?.let { latencyMs ->
                Text(
                    text = "api duration: ${latencyMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberpunkColors.TextMuted,
                )
            }
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
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
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

        RuntimeInfoPanel(
            agent = agent,
            agentConfig = agentConfig,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RuntimeInfoPanel(
    agent: AgentState,
    agentConfig: AgentConfigDto?,
    modifier: Modifier = Modifier,
) {
    val latestAssistant = agent.messages.lastOrNull {
        it.role == AgentMessageRoleDto.ASSISTANT && it.status.equals("done", ignoreCase = true)
    }
    val contextUsed = estimateContextTokens(agent)
    val contextWindow = resolveContextWindowTokens(agent, agentConfig)
    val contextLeft = contextWindow?.let { max(it - contextUsed, 0) }

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
            text = "context left: ${contextLeft?.toString() ?: "n/a"}",
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

private fun resolveContextWindowTokens(
    agent: AgentState,
    agentConfig: AgentConfigDto?,
): Int? {
    if (agentConfig == null) return null

    val selectedModelId = agent.model.ifBlank { agentConfig.defaultModel }
    return agentConfig.models.firstOrNull { it.id == selectedModelId }?.contextWindowTokens
        ?: agentConfig.defaultContextWindowTokens
}

private fun estimateContextTokens(agent: AgentState): Int {
    val summaryTokens = estimateTokens(agent.contextSummary)
    val recentMessages = agent.messages
        .filter { !it.status.equals("stopped", ignoreCase = true) }
        .filter { it.createdAt > agent.summarizedUntilCreatedAt }
    val messageTokens = recentMessages.sumOf { message ->
        estimateTokens(message.text) + 4
    }
    return summaryTokens + messageTokens + 64
}

private fun estimateTokens(text: String): Int {
    if (text.isBlank()) return 0
    return (text.length + 3) / 4
}
