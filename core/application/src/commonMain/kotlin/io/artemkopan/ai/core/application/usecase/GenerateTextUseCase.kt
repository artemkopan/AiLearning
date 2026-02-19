package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.mapper.DomainErrorMapper
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.model.GenerateOutput
import io.artemkopan.ai.core.application.model.UsageOutput
import io.artemkopan.ai.core.domain.model.LlmGenerationInput
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.github.aakira.napier.Napier

class GenerateTextUseCase(
    private val repository: LlmRepository,
    private val validatePromptUseCase: ValidatePromptUseCase,
    private val resolveGenerationOptionsUseCase: ResolveGenerationOptionsUseCase,
    private val resolveAgentModeUseCase: ResolveAgentModeUseCase,
    private val errorMapper: DomainErrorMapper,
) {
    suspend fun execute(command: GenerateCommand): Result<GenerateOutput> {
        Napier.d(tag = TAG) { "Executing generate text: promptLength=${command.prompt.length}" }

        val prompt = validatePromptUseCase.execute(command.prompt).getOrElse { error ->
            Napier.w(tag = TAG) { "Prompt validation failed: ${error.message}" }
            return Result.failure(error)
        }

        val options = resolveGenerationOptionsUseCase.execute(
            command.model, command.temperature, command.maxOutputTokens, command.stopSequences,
        )
            .getOrElse { error ->
                Napier.w(tag = TAG) { "Options resolution failed: ${error.message}" }
                return Result.failure(error)
            }

        val systemInstruction = resolveAgentModeUseCase.execute(command.agentMode).getOrElse { error ->
            Napier.w(tag = TAG) { "Agent mode resolution failed: ${error.message}" }
            return Result.failure(error)
        }

        Napier.d(tag = TAG) { "Calling repository: model=${options.modelId.value}, temp=${options.temperature.value}" }

        return repository.generate(
            LlmGenerationInput(
                prompt = prompt,
                modelId = options.modelId,
                temperature = options.temperature,
                maxOutputTokens = options.maxOutputTokens,
                stopSequences = options.stopSequences,
                systemInstruction = systemInstruction,
            )
        ).map { generation ->
            Napier.i(tag = TAG) {
                "Generation successful: provider=${generation.provider}, totalTokens=${generation.usage?.totalTokens ?: 0}"
            }
            GenerateOutput(
                text = generation.text,
                provider = generation.provider,
                model = generation.model,
                usage = generation.usage?.let {
                    UsageOutput(
                        inputTokens = it.inputTokens,
                        outputTokens = it.outputTokens,
                        totalTokens = it.totalTokens,
                    )
                },
            )
        }.recoverCatching { throwable ->
            val mappedError = errorMapper.mapToAppError(throwable)
            Napier.e(tag = TAG, throwable = throwable) { "Generation failed: ${mappedError.message}" }
            throw mappedError
        }
    }

    private companion object {
        const val TAG = "GenerateTextUseCase"
    }
}
