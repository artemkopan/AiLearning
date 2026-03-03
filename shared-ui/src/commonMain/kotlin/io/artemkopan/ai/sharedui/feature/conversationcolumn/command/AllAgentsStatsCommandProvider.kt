package io.artemkopan.ai.sharedui.feature.conversationcolumn.command

import org.koin.core.annotation.Single

@Single(binds = [ConversationCommandProvider::class])
class AllAgentsStatsCommandProvider : ConversationCommandProvider {
    override fun provide(context: ConversationCommandContext): List<ConversationCommandDefinition> {
        return listOf(
            ConversationCommandDefinition(
                id = "all-agents-stats",
                label = "ALL AGENTS STATS",
                description = "Insert token for all agent stats",
                keywords = setOf("agents", "stats", "all"),
                tokenTemplate = "/agents-stats",
                sortOrder = 10,
            )
        )
    }
}
