package io.artemkopan.ai.sharedui.feature.conversationcolumn.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.CommandPaletteItemUiModel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun CommandPalette(
    visible: Boolean,
    items: List<CommandPaletteItemUiModel>,
    onSelect: (commandId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    CyberpunkPanel(
        title = "COMMANDS",
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Text(
                text = "NO MATCHING COMMANDS",
                style = MaterialTheme.typography.bodySmall,
                color = CyberpunkColors.TextMuted,
            )
        } else {
            items.forEach { item ->
                CommandOption(
                    label = item.label,
                    description = item.description,
                    token = item.token,
                    onClick = {
                        onSelect(item.id)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun CommandOption(
    label: String,
    description: String,
    token: String,
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
        Text(
            text = "$description ($token)",
            style = MaterialTheme.typography.labelSmall,
            color = CyberpunkColors.TextMuted,
        )
    }
}
