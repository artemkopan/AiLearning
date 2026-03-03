package io.artemkopan.ai.sharedui.feature.agentssidepanel.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.feature.agentssidepanel.model.AgentsSidePanelItemModel
import io.artemkopan.ai.sharedui.feature.agentssidepanel.viewmodel.AgentsSidePanelViewModel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun AgentsSidePanelFeature(
    viewModel: AgentsSidePanelViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    CyberpunkPanel(
        title = "AGENTS",
        accentColor = CyberpunkColors.Cyan,
        modifier = modifier,
    ) {
        state.agents.forEach { item ->
            AgentItem(
                item = item,
                isActive = item.id == state.activeAgentId,
                showClose = state.canCloseAgent,
                onSelect = { viewModel.onAgentSelected(item.id) },
                onClose = { viewModel.onAgentClosed(item.id) },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        AddAgentButton(
            onClick = viewModel::onCreateAgentClicked,
        )
    }
}

@Composable
private fun AgentItem(
    item: AgentsSidePanelItemModel,
    isActive: Boolean,
    showClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val backgroundColor = if (isActive) CyberpunkColors.Cyan.copy(alpha = 0.15f) else CyberpunkColors.CardDark
    val textColor = if (isActive) CyberpunkColors.Cyan else CyberpunkColors.TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(backgroundColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (item.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = CyberpunkColors.Cyan,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.status.uppercase(),
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
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "+ NEW AGENT",
            style = MaterialTheme.typography.labelMedium,
            color = CyberpunkColors.Cyan,
        )
    }
}
