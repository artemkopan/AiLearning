package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.*
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun ContextConfigPanel(
    contextConfig: AgentContextConfigDto,
    onStrategyChanged: (String) -> Unit,
    onRecentMessagesChanged: (String) -> Unit,
    onSummarizeEveryChanged: (String) -> Unit,
    onWindowSizeChanged: (String) -> Unit,
    branches: List<BranchDto>,
    activeBranchId: String?,
    onSwitchBranch: (branchId: String) -> Unit,
    onDeleteBranch: (branchId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLocked = contextConfig.locked

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

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StrategyOption(
                label = "FULL HISTORY",
                selected = contextConfig is FullHistoryContextConfigDto,
                enabled = !isLocked,
                onClick = { onStrategyChanged(STRATEGY_FULL_HISTORY) },
            )
            StrategyOption(
                label = "ROLLING",
                selected = contextConfig is RollingSummaryContextConfigDto,
                enabled = !isLocked,
                onClick = { onStrategyChanged(STRATEGY_ROLLING_SUMMARY) },
            )
            StrategyOption(
                label = "WINDOW",
                selected = contextConfig is SlidingWindowContextConfigDto,
                enabled = !isLocked,
                onClick = { onStrategyChanged(STRATEGY_SLIDING_WINDOW) },
            )
            StrategyOption(
                label = "FACTS",
                selected = contextConfig is StickyFactsContextConfigDto,
                enabled = !isLocked,
                onClick = { onStrategyChanged(STRATEGY_STICKY_FACTS) },
            )
            StrategyOption(
                label = "BRANCHING",
                selected = contextConfig is BranchingContextConfigDto,
                enabled = !isLocked,
                onClick = { onStrategyChanged(STRATEGY_BRANCHING) },
            )
        }

        when (contextConfig) {
            is RollingSummaryContextConfigDto -> {
                CyberpunkTextField(
                    value = contextConfig.recentMessagesN.toString(),
                    onValueChange = { value ->
                        if (!isLocked) onRecentMessagesChanged(value.filter { it.isDigit() })
                    },
                    label = "RECENT N",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                CyberpunkTextField(
                    value = contextConfig.summarizeEveryK.toString(),
                    onValueChange = { value ->
                        if (!isLocked) onSummarizeEveryChanged(value.filter { it.isDigit() })
                    },
                    label = "SUMMARIZE EVERY K",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            is SlidingWindowContextConfigDto -> {
                CyberpunkTextField(
                    value = contextConfig.windowSize.toString(),
                    onValueChange = { value ->
                        if (!isLocked) onWindowSizeChanged(value.filter { it.isDigit() })
                    },
                    label = "WINDOW SIZE",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            is StickyFactsContextConfigDto -> {
                CyberpunkTextField(
                    value = contextConfig.recentMessagesN.toString(),
                    onValueChange = { value ->
                        if (!isLocked) onRecentMessagesChanged(value.filter { it.isDigit() })
                    },
                    label = "RECENT N",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            is BranchingContextConfigDto -> {
                CyberpunkTextField(
                    value = contextConfig.recentMessagesN.toString(),
                    onValueChange = { value ->
                        if (!isLocked) onRecentMessagesChanged(value.filter { it.isDigit() })
                    },
                    label = "RECENT N",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                BranchPanel(
                    branches = branches,
                    activeBranchId = activeBranchId,
                    onSwitchBranch = onSwitchBranch,
                    onDeleteBranch = onDeleteBranch,
                )
            }
            is FullHistoryContextConfigDto -> { /* no extra fields */ }
        }
    }
}

@Composable
private fun BranchPanel(
    branches: List<BranchDto>,
    activeBranchId: String?,
    onSwitchBranch: (String) -> Unit,
    onDeleteBranch: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "BRANCHES:",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.Yellow,
        )
        Text(
            text = "main",
            style = MaterialTheme.typography.bodySmall,
            color = if (activeBranchId == null) CyberpunkColors.Cyan else CyberpunkColors.TextSecondary,
            modifier = Modifier.clickable { onSwitchBranch("main") },
        )
        branches.forEach { branch ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = branch.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activeBranchId == branch.id) CyberpunkColors.Cyan else CyberpunkColors.TextSecondary,
                    modifier = Modifier.clickable { onSwitchBranch(branch.id) },
                )
                Text(
                    text = "[X]",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberpunkColors.Red,
                    modifier = Modifier.clickable { onDeleteBranch(branch.id) },
                )
            }
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
private const val STRATEGY_SLIDING_WINDOW = "sliding_window"
private const val STRATEGY_STICKY_FACTS = "sticky_facts"
private const val STRATEGY_BRANCHING = "branching"
