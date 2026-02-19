package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun AgentModeSelector(
    selected: AgentMode,
    onModeSelected: (AgentMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    CyberpunkPanel(
        title = "AGENT MODE",
        accentColor = CyberpunkColors.Cyan,
        modifier = modifier,
    ) {
        AgentMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val accentColor = mode.accentColor()

            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor else accentColor.copy(alpha = 0.2f)),
                )
                Text(
                    text = mode.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) accentColor else CyberpunkColors.TextSecondary,
                )
            }
        }
    }
}

private fun AgentMode.accentColor(): Color = when (this) {
    AgentMode.DEFAULT -> CyberpunkColors.Yellow
    AgentMode.ENGINEER -> CyberpunkColors.Cyan
    AgentMode.PHILOSOPHIC -> CyberpunkColors.NeonGreen
    AgentMode.CRITIC -> CyberpunkColors.Red
}
