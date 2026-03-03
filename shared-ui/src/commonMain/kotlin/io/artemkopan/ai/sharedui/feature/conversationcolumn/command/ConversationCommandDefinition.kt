package io.artemkopan.ai.sharedui.feature.conversationcolumn.command

data class ConversationCommandDefinition(
    val id: String,
    val label: String,
    val description: String,
    val keywords: Set<String> = emptySet(),
    val tokenTemplate: String,
    val sortOrder: Int = 0,
)
