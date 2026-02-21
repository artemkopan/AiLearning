package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.ChatStatus
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun StatusBadge(status: ChatStatus) {
    val color = when (status) {
        ChatStatus.idle -> CyberpunkColors.TextMuted
        ChatStatus.running -> CyberpunkColors.Cyan
        ChatStatus.done -> CyberpunkColors.NeonGreen
        ChatStatus.failed -> CyberpunkColors.Red
        ChatStatus.attention -> CyberpunkColors.Yellow
    }
    Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
}
