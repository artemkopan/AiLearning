package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.ChatState
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun InsertFromChatPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    otherChats: List<ChatState>,
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

        if (otherChats.isEmpty()) {
            Text(
                text = "No other chats available",
                style = MaterialTheme.typography.bodySmall,
                color = CyberpunkColors.TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            otherChats.forEach { chat ->
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    // Chat title
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkColors.Cyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )

                    // Insert Prompt option
                    if (chat.prompt.isNotBlank()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "> INSERT PROMPT  ${chat.prompt.take(80).replace('\n', ' ')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyberpunkColors.Yellow,
                                    maxLines = 1,
                                )
                            },
                            onClick = {
                                onInsert(chat.prompt)
                                onDismiss()
                            },
                        )
                    }

                    // Insert Output option
                    val responseText = chat.response?.text
                    if (!responseText.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "> INSERT OUTPUT  ${responseText.take(80).replace('\n', ' ')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyberpunkColors.NeonGreen,
                                    maxLines = 1,
                                )
                            },
                            onClick = {
                                onInsert(responseText)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}
