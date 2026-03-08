package io.artemkopan.ai.sharedui.feature.conversationcolumn.model

import androidx.compose.ui.text.input.TextFieldValue
import io.artemkopan.ai.sharedui.core.session.AgentMessageState
import io.artemkopan.ai.sharedui.core.session.AgentState
import io.artemkopan.ai.sharedui.core.session.QueuedMessageState
import io.artemkopan.ai.sharedui.core.session.TaskState

data class ConversationColumnUiModel(
    val agent: AgentState? = null,
    val allAgents: List<AgentState> = emptyList(),
    val queuedMessages: List<QueuedMessageState> = emptyList(),
    val inputValue: TextFieldValue = TextFieldValue(""),
    val displayMessages: List<ConversationDisplayMessage> = emptyList(),
    val statusText: String? = null,
    val activeTask: TaskState? = null,
    val commandPalette: CommandPaletteUiModel = CommandPaletteUiModel(),
)

data class ConversationDisplayMessage(
    val id: String,
    val message: AgentMessageState,
    val isQueuedLocal: Boolean,
)

data class CommandPaletteUiModel(
    val visible: Boolean = false,
    val items: List<CommandPaletteItemUiModel> = emptyList(),
)

data class CommandPaletteItemUiModel(
    val id: String,
    val label: String,
    val description: String,
    val token: String,
)

data class SlashTokenBounds(
    val start: Int,
    val endExclusive: Int,
    val query: String,
)
