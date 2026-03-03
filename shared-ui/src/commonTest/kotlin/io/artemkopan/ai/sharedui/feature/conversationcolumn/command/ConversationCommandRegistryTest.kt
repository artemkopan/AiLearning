package io.artemkopan.ai.sharedui.feature.conversationcolumn.command

import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentState
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationCommandRegistryTest {
    @Test
    fun `aggregates commands deduplicates by id and sorts`() {
        val registry = ConversationCommandRegistry(
            providers = listOf(
                provider(
                    ConversationCommandDefinition(
                        id = "b",
                        label = "Beta",
                        description = "",
                        tokenTemplate = "/b",
                        sortOrder = 20,
                    ),
                    ConversationCommandDefinition(
                        id = "a",
                        label = "Alpha",
                        description = "",
                        tokenTemplate = "/a",
                        sortOrder = 10,
                    ),
                ),
                provider(
                    ConversationCommandDefinition(
                        id = "a",
                        label = "Alpha Duplicate",
                        description = "",
                        tokenTemplate = "/a-dup",
                        sortOrder = 0,
                    )
                ),
            )
        )

        val resolved = registry.resolve(
            context = ConversationCommandContext(
                activeAgentId = AgentId("agent-1"),
                allAgents = listOf(
                    AgentState(id = AgentId("agent-1"), title = "First")
                ),
            )
        )

        assertEquals(listOf("a", "b"), resolved.map { it.id })
        assertEquals("/a", resolved.first().tokenTemplate)
    }

    private fun provider(vararg definitions: ConversationCommandDefinition): ConversationCommandProvider {
        return object : ConversationCommandProvider {
            override fun provide(context: ConversationCommandContext): List<ConversationCommandDefinition> {
                return definitions.toList()
            }
        }
    }
}
