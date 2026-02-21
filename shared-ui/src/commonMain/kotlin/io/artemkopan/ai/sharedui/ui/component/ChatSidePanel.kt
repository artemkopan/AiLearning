package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.ChatTab
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun ChatSidePanel(
    chats: List<ChatTab>,
    activeChatId: String?,
    onChatSelected: (String) -> Unit,
    onChatClosed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CyberpunkPanel(
        title = "CHATS",
        accentColor = CyberpunkColors.Cyan,
        modifier = modifier,
    ) {
        if (chats.isEmpty()) {
            Text(
                text = "No active chats",
                style = MaterialTheme.typography.bodySmall,
                color = CyberpunkColors.TextMuted,
            )
        } else {
            chats.forEach { chat ->
                val isActive = chat.chatId == activeChatId
                ChatItem(
                    chat = chat,
                    isActive = isActive,
                    showClose = chats.size > 1,
                    onSelect = { onChatSelected(chat.chatId) },
                    onClose = { onChatClosed(chat.chatId) },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun ChatItem(
    chat: ChatTab,
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
        StatusBadge(status = chat.status)

        Text(
            text = chat.projectName,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

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
