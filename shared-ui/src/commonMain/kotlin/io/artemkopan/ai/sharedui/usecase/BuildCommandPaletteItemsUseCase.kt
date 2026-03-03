package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.feature.conversationcolumn.command.ConversationCommandDefinition
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.CommandPaletteItemUiModel
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.SlashTokenBounds
import org.koin.core.annotation.Factory

@Factory
class BuildCommandPaletteItemsUseCase {
    operator fun invoke(
        commands: List<ConversationCommandDefinition>,
        slashTokenBounds: SlashTokenBounds?,
    ): List<CommandPaletteItemUiModel> {
        val query = slashTokenBounds?.query?.trim()?.lowercase().orEmpty()
        return commands
            .asSequence()
            .filter { definition ->
                if (query.isBlank()) return@filter true
                definition.label.lowercase().contains(query) ||
                    definition.description.lowercase().contains(query) ||
                    definition.tokenTemplate.lowercase().contains(query) ||
                    definition.keywords.any { keyword -> keyword.contains(query, ignoreCase = true) }
            }
            .map { definition ->
                CommandPaletteItemUiModel(
                    id = definition.id,
                    label = definition.label,
                    description = definition.description,
                    token = definition.tokenTemplate,
                )
            }
            .toList()
    }
}
