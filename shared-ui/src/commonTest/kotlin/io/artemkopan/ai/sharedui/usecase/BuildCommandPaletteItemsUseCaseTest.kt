package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.feature.conversationcolumn.command.ConversationCommandDefinition
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.SlashTokenBounds
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildCommandPaletteItemsUseCaseTest {
    private val useCase = BuildCommandPaletteItemsUseCase()

    @Test
    fun `returns all commands when query is blank`() {
        val commands = listOf(
            command(id = "a", label = "ALL", token = "/agents-stats"),
            command(id = "b", label = "AGENT", token = "/agent-agent-1-stats"),
        )

        val result = useCase(commands, SlashTokenBounds(0, 1, ""))

        assertEquals(2, result.size)
    }

    @Test
    fun `filters commands by query across label token and keywords`() {
        val commands = listOf(
            command(id = "all", label = "ALL AGENTS STATS", token = "/agents-stats", keywords = setOf("all")),
            command(id = "agent-1", label = "AGENT STATS: FIRST", token = "/agent-agent-1-stats", keywords = setOf("first")),
        )

        val result = useCase(commands, SlashTokenBounds(0, 4, "first"))

        assertEquals(1, result.size)
        assertEquals("agent-1", result.first().id)
    }

    private fun command(
        id: String,
        label: String,
        token: String,
        keywords: Set<String> = emptySet(),
    ): ConversationCommandDefinition {
        return ConversationCommandDefinition(
            id = id,
            label = label,
            description = "desc-$id",
            keywords = keywords,
            tokenTemplate = token,
            sortOrder = 0,
        )
    }
}
