package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.AgentState
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun SlashCommandPopup(
    visible: Boolean,
    agents: List<AgentState>,
    onInsertToken: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var stage by remember { mutableStateOf(Stage.ROOT) }
    var selectedAgent by remember { mutableStateOf<AgentState?>(null) }

    CyberpunkPanel(
        title = "COMMANDS",
        modifier = modifier,
    ) {
        when (stage) {
            Stage.ROOT -> {
                CommandOption(
                    label = "AGENTS",
                    onClick = { stage = Stage.AGENTS }
                )
            }

            Stage.AGENTS -> {
                agents.forEach { agent ->
                    CommandOption(
                        label = "${agent.id.value} (${agent.title})",
                        onClick = {
                            selectedAgent = agent
                            stage = Stage.AGENT_ACTIONS
                        }
                    )
                }
            }

            Stage.AGENT_ACTIONS -> {
                val current = selectedAgent
                if (current != null) {
                    CommandOption(
                        label = "AGENT STATS",
                        onClick = {
                            onInsertToken("/agent-${current.id.value}-stats")
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandOption(
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.TextPrimary,
        )
    }
}

private enum class Stage {
    ROOT,
    AGENTS,
    AGENT_ACTIONS,
}
