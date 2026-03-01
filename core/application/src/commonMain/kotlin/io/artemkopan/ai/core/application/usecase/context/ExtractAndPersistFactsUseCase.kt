package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.usecase.GenerateTextUseCase
import io.artemkopan.ai.core.domain.model.AgentFacts
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository

class ExtractAndPersistFactsUseCase(
    private val repository: AgentRepository,
    private val generateTextUseCase: GenerateTextUseCase,
    private val buildFactsExtractionPromptUseCase: BuildFactsExtractionPromptUseCase,
    private val factsMaxOutputTokens: Int = FACTS_MAX_OUTPUT_TOKENS,
    private val factsModelOverride: String? = null,
) {
    suspend fun execute(
        userId: UserId,
        agentId: AgentId,
        agentModel: String,
        newUserMessage: String,
    ): Result<Unit> {
        val existingFacts = repository.getAgentFacts(userId, agentId)
            .getOrElse { return Result.failure(it) }
        val existingJson = existingFacts?.factsJson.orEmpty()

        val prompt = buildFactsExtractionPromptUseCase.execute(existingJson, newUserMessage)
        val model = factsModelOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: agentModel.trim().takeIf { it.isNotEmpty() }

        val generated = generateTextUseCase.execute(
            GenerateCommand(
                prompt = prompt,
                model = model,
                temperature = FACTS_TEMPERATURE,
                maxOutputTokens = factsMaxOutputTokens,
                stopSequences = null,
                agentMode = null,
            )
        ).getOrElse { return Result.failure(it) }

        val responseText = generated.text.trim()
        val factsJson = extractJsonObject(responseText)
        if (factsJson.isBlank()) return Result.success(Unit)

        return repository.upsertAgentFacts(
            userId,
            AgentFacts(
                agentId = agentId,
                factsJson = factsJson,
                updatedAt = 0L,
            )
        )
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < 0 || end <= start) return text
        return text.substring(start, end + 1)
    }
}

private const val FACTS_TEMPERATURE = 0.2
private const val FACTS_MAX_OUTPUT_TOKENS = 500
