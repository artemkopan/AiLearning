package io.artemkopan.ai.sharedui.feature.conversationcolumn.command

interface ConversationCommandProvider {
    fun provide(context: ConversationCommandContext): List<ConversationCommandDefinition>
}
