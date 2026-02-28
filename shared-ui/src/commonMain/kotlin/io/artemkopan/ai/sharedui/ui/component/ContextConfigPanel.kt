package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.artemkopan.ai.sharedcontract.AgentContextConfigDto
import io.artemkopan.ai.sharedcontract.FullHistoryContextConfigDto
import io.artemkopan.ai.sharedcontract.RollingSummaryContextConfigDto
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun ContextConfigPanel(
    contextConfig: AgentContextConfigDto,
    onStrategyChanged: (String) -> Unit,
    onRecentMessagesChanged: (String) -> Unit,
    onSummarizeEveryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLocked = contextConfig.locked
    val isRolling = contextConfig is RollingSummaryContextConfigDto

    CyberpunkPanel(
        title = "CONTEXT",
        accentColor = CyberpunkColors.Yellow,
        modifier = modifier,
    ) {
        Text(
            text = if (isLocked) "STATUS: LOCKED" else "STATUS: EDITABLE",
            style = MaterialTheme.typography.bodySmall,
            color = if (isLocked) CyberpunkColors.Red else CyberpunkColors.Cyan,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StrategyOption(
                label = "FULL HISTORY",
                selected = contextConfig is FullHistoryContextConfigDto,
                enabled = !isLocked,
                onClick = { onStrategyChanged(STRATEGY_FULL_HISTORY) },
            )
            StrategyOption(
                label = "ROLLING SUMMARY",
                selected = isRolling,
                enabled = !isLocked,
                onClick = { onStrategyChanged(STRATEGY_ROLLING_SUMMARY) },
            )
        }

        if (isRolling) {
            val rolling = contextConfig as RollingSummaryContextConfigDto
            CyberpunkTextField(
                value = rolling.recentMessagesN.toString(),
                onValueChange = { value ->
                    if (!isLocked) onRecentMessagesChanged(value.filter { it.isDigit() })
                },
                label = "RECENT N",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            CyberpunkTextField(
                value = rolling.summarizeEveryK.toString(),
                onValueChange = { value ->
                    if (!isLocked) onSummarizeEveryChanged(value.filter { it.isDigit() })
                },
                label = "SUMMARIZE EVERY K",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun StrategyOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            !enabled -> CyberpunkColors.TextMuted
            selected -> CyberpunkColors.Yellow
            else -> CyberpunkColors.TextSecondary
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

private const val STRATEGY_FULL_HISTORY = "full_history"
private const val STRATEGY_ROLLING_SUMMARY = "rolling_summary_recent_n"
