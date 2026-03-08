package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.LlmRepository

class GenerateTextUseCase(
    private val repository: LlmRepository,
) {
    suspend fun execute(command: GenerateCommand): Result<LlmGeneration> {
        val input = LlmGenerationInput(
            prompt = Prompt(command.prompt),
            modelId = ModelId(command.model),
            temperature = Temperature(command.temperature),
            maxOutputTokens = command.maxOutputTokens?.let { MaxOutputTokens(it) },
            stopSequences = command.stopSequences?.takeIf { it.isNotEmpty() }?.let { StopSequences(it) },
            systemInstruction = command.systemInstruction?.takeIf { it.isNotBlank() }?.let { SystemInstruction(it) },
            responseFormat = command.responseFormat,
        )
        return repository.generate(input)
    }
}
