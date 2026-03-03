package io.artemkopan.ai.sharedui.feature.conversationcolumn.command

import org.koin.core.annotation.Factory

@Factory
class ConversationCommandRegistry(
    private val providers: List<ConversationCommandProvider>,
) {
    fun resolve(context: ConversationCommandContext): List<ConversationCommandDefinition> {
        val deduplicatedById = LinkedHashMap<String, ConversationCommandDefinition>()
        providers.forEach { provider ->
            provider.provide(context).forEach { definition ->
                if (!deduplicatedById.containsKey(definition.id)) {
                    deduplicatedById[definition.id] = definition
                }
            }
        }
        return deduplicatedById.values
            .sortedWith(
                compareBy<ConversationCommandDefinition> { it.sortOrder }
                    .thenBy { it.label.lowercase() }
            )
    }
}
