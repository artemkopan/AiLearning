package io.artemkopan.ai.sharedui.feature.conversationcolumn.command

import org.koin.core.annotation.Single

@Single(binds = [ConversationCommandProvider::class])
class MemoryLayerCommandProvider : ConversationCommandProvider {
    override fun provide(context: ConversationCommandContext): List<ConversationCommandDefinition> {
        return listOf(
            ConversationCommandDefinition(
                id = "memory-short-term",
                label = "MEMORY: SHORT-TERM",
                description = "Switch to current dialogue memory",
                keywords = setOf("memory", "short-term", "dialogue"),
                tokenTemplate = "/memory-short-term",
                sortOrder = 1,
            ),
            ConversationCommandDefinition(
                id = "memory-working",
                label = "MEMORY: WORKING",
                description = "Switch to current task data memory",
                keywords = setOf("memory", "working", "task"),
                tokenTemplate = "/memory-working",
                sortOrder = 2,
            ),
            ConversationCommandDefinition(
                id = "memory-long-term",
                label = "MEMORY: LONG-TERM",
                description = "Switch to profile/decision/knowledge memory",
                keywords = setOf("memory", "long-term", "profile", "knowledge"),
                tokenTemplate = "/memory-long-term",
                sortOrder = 3,
            ),
        )
    }
}
