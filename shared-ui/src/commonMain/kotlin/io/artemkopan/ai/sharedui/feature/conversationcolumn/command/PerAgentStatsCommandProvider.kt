package io.artemkopan.ai.sharedui.feature.conversationcolumn.command

import org.koin.core.annotation.Single

@Single(binds = [ConversationCommandProvider::class])
class PerAgentStatsCommandProvider : ConversationCommandProvider {
    override fun provide(context: ConversationCommandContext): List<ConversationCommandDefinition> {
        return context.allAgents.map { agent ->
            val normalizedId = agent.id.value
            ConversationCommandDefinition(
                id = "agent-stats-$normalizedId",
                label = "AGENT STATS: ${agent.title}",
                description = "Insert token for ${agent.id.value}",
                keywords = setOf("agent", "stats", normalizedId, agent.title.lowercase()),
                tokenTemplate = "/agent-$normalizedId-stats",
                sortOrder = 20,
            )
        }
    }
}
