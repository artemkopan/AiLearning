package io.artemkopan.ai.sharedui.feature.settingscolumn.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedui.core.session.AgentState
import io.artemkopan.ai.sharedui.feature.configpanel.view.ConfigPanelFeature
import io.artemkopan.ai.sharedui.feature.configpanel.viewmodel.ConfigPanelViewModel
import io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel.SettingsColumnViewModel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun SettingsColumnFeature(
    settingsViewModel: SettingsColumnViewModel,
    configViewModel: ConfigPanelViewModel,
    modifier: Modifier = Modifier,
) {
    val state by settingsViewModel.state.collectAsState()
    val agent = state.agent ?: return
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
                onModeSelected = settingsViewModel::onAgentModeChanged,
                modifier = Modifier.fillMaxWidth(),
            )

            ConfigPanelFeature(
                viewModel = configViewModel,
                modifier = Modifier.fillMaxWidth(),
            )

            ContextConfigPanel(
                contextConfig = agent.contextConfig,
                onStrategyChanged = settingsViewModel::onContextStrategyChanged,
                onRecentMessagesChanged = settingsViewModel::onContextRecentMessagesChanged,
                onSummarizeEveryChanged = settingsViewModel::onContextSummarizeEveryChanged,
                onWindowSizeChanged = settingsViewModel::onContextWindowSizeChanged,
                branches = agent.branches,
                activeBranchId = agent.activeBranchId,
                onSwitchBranch = settingsViewModel::onSwitchBranch,
                onDeleteBranch = settingsViewModel::onDeleteBranch,
                modifier = Modifier.fillMaxWidth(),
            )

            RuntimeInfoPanel(
                agent = agent,
                contextTotalTokensLabel = state.contextTotalTokensLabel,
                contextLeftLabel = state.contextLeftLabel,
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
