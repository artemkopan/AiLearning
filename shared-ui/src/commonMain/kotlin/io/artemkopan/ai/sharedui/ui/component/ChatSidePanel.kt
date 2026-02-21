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
import io.artemkopan.ai.sharedui.state.ChatId
import io.artemkopan.ai.sharedui.state.ChatState
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun ChatSidePanel(
    chats: List<ChatState>,
    activeChatId: ChatId?,
    onChatSelected: (ChatId) -> Unit,
    onChatClosed: (ChatId) -> Unit,
    onNewChatClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAddChat = chats.size < 5
    val canClose = chats.size > 1

    CyberpunkPanel(
        title = "CHATS",
        accentColor = CyberpunkColors.Cyan,
        modifier = modifier,
    ) {
        chats.forEach { chat ->
            val isActive = chat.id == activeChatId
            ChatItem(
                chat = chat,
                isActive = isActive,
                showClose = canClose,
                onSelect = { onChatSelected(chat.id) },
                onClose = { onChatClosed(chat.id) },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        AddChatButton(
            enabled = canAddChat,
            onClick = onNewChatClicked,
        )
    }
}

@Composable
private fun ChatItem(
    chat: ChatState,
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
        if (chat.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = CyberpunkColors.Cyan,
            )
        }

        Text(
            text = formatChatTitle(chat),
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

@Composable
private fun AddChatButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val textColor = if (enabled) CyberpunkColors.Cyan else CyberpunkColors.TextMuted
    val label = if (enabled) "+ NEW CHAT" else "MAX CHATS (5)"

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

private fun formatChatTitle(chat: ChatState): String {
    val chatNumber = chat.id.value.substringAfter("chat-", "")
    return if (chatNumber.isNotBlank()) "#$chatNumber: ${chat.title}" else chat.title
}
