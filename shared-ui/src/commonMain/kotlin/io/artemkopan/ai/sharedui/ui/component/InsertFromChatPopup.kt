package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.ChatState
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

data class InsertItem(
    val chatTitle: String,
    val label: String,
    val preview: String,
    val content: String,
    val token: String,
    val color: Color,
)

fun buildInsertItems(otherChats: List<ChatState>): List<InsertItem> = buildList {
    otherChats.forEach { chat ->
        val chatNumber = chat.id.value.substringAfter("chat-")
        val displayTitle = if (chatNumber.isNotBlank()) "#$chatNumber: ${chat.title}" else chat.title
        if (chat.prompt.isNotBlank()) {
            add(
                InsertItem(
                    chatTitle = displayTitle,
                    label = "PROMPT",
                    preview = chat.prompt.take(80).replace('\n', ' '),
                    content = chat.prompt,
                    token = "[#$chatNumber prompt]",
                    color = CyberpunkColors.Yellow,
                )
            )
        }
        val responseText = chat.response?.text
        if (!responseText.isNullOrBlank()) {
            add(
                InsertItem(
                    chatTitle = displayTitle,
                    label = "OUTPUT",
                    preview = responseText.take(80).replace('\n', ' '),
                    content = responseText,
                    token = "[#$chatNumber output]",
                    color = CyberpunkColors.NeonGreen,
                )
            )
        }
    }
}

@Composable
fun InsertFromChatPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<InsertItem>,
    selectedIndex: Int,
    onInsert: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = CyberpunkColors.CardDark,
        modifier = Modifier.widthIn(min = 250.dp, max = 400.dp),
    ) {
        // Header
        Text(
            text = "[ INSERT FROM CHAT ]",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = CyberpunkColors.Yellow,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        if (items.isEmpty()) {
            Text(
                text = "No other chats available",
                style = MaterialTheme.typography.bodySmall,
                color = CyberpunkColors.TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${item.chatTitle} > ${item.label}  ${item.preview}",
                            style = MaterialTheme.typography.bodySmall,
                            color = item.color,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        onInsert(item.token)
                        onDismiss()
                    },
                    modifier = if (isSelected) {
                        Modifier.background(CyberpunkColors.BorderDark)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
