package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.AgentId
import io.artemkopan.ai.sharedui.state.AgentState
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun AgentSidePanel(
    agents: List<AgentState>,
    activeAgentId: AgentId?,
    onAgentSelected: (AgentId) -> Unit,
    onAgentClosed: (AgentId) -> Unit,
    onNewAgentClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAddAgent = agents.size < 5
    val canClose = agents.size > 1

    CyberpunkPanel(
        title = "AGENTS",
        accentColor = CyberpunkColors.Cyan,
        modifier = modifier,
    ) {
        agents.forEach { agent ->
            val isActive = agent.id == activeAgentId
            AgentItem(
                agent = agent,
                isActive = isActive,
                showClose = canClose,
                onSelect = { onAgentSelected(agent.id) },
                onClose = { onAgentClosed(agent.id) },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        AddAgentButton(
            enabled = canAddAgent,
            onClick = onNewAgentClicked,
        )
    }
}

@Composable
private fun AgentItem(
    agent: AgentState,
    isActive: Boolean,
    showClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val bgColor = if (isActive) CyberpunkColors.Cyan.copy(alpha = 0.15f) else CyberpunkColors.CardDark
    val textColor = if (isActive) CyberpunkColors.Cyan else CyberpunkColors.TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(bgColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (agent.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = CyberpunkColors.Cyan,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatAgentTitle(agent),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = agent.status.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = CyberpunkColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showClose) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "x",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberpunkColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun AddAgentButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val textColor = if (enabled) CyberpunkColors.Cyan else CyberpunkColors.TextMuted
    val label = if (enabled) "+ NEW AGENT" else "MAX AGENTS (5)"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

private fun formatAgentTitle(agent: AgentState): String {
    val agentNumber = agent.id.value.substringAfter("agent-", "")
    return if (agentNumber.isNotBlank()) "#$agentNumber: ${agent.title}" else agent.title
}
