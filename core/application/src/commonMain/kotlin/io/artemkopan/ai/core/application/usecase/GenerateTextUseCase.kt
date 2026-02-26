package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.mapper.DomainErrorMapper
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.model.GenerateOutput
import io.artemkopan.ai.core.application.model.UsageOutput
import io.artemkopan.ai.core.domain.model.LlmGenerationInput
import io.artemkopan.ai.core.domain.repository.LlmRepository
import co.touchlab.kermit.Logger

class GenerateTextUseCase(
    private val repository: LlmRepository,
    private val validatePromptUseCase: ValidatePromptUseCase,
    private val resolveGenerationOptionsUseCase: ResolveGenerationOptionsUseCase,
    private val resolveAgentModeUseCase: ResolveAgentModeUseCase,
    private val errorMapper: DomainErrorMapper,
) {
    private val log = Logger.withTag("GenerateTextUseCase")

    suspend fun execute(command: GenerateCommand): Result<GenerateOutput> {
        log.d { "Executing generate text: promptLength=${command.prompt.length}" }

        val options = resolveGenerationOptionsUseCase.execute(
            command.model, command.temperature, command.maxOutputTokens, command.stopSequences,
        )
            .getOrElse { error ->
                log.w { "Options resolution failed: ${error.message}" }
                return Result.failure(error)
            }

        val modelMetadata = repository.getModelMetadata(options.modelId.value).getOrElse { error ->
            val mappedError = errorMapper.mapToAppError(error)
            log.w { "Model metadata lookup failed: model=${options.modelId.value}, reason=${mappedError.message}" }
            return Result.failure(mappedError)
        }

        val prompt = validatePromptUseCase.execute(command.prompt, modelMetadata.inputTokenLimit).getOrElse { error ->
            log.w {
                "Prompt validation failed: ${error.message}; model=${options.modelId.value}, inputLimit=${modelMetadata.inputTokenLimit}"
            }
            return Result.failure(error)
        }

        val systemInstruction = resolveAgentModeUseCase.execute(command.agentMode).getOrElse { error ->
            log.w { "Agent mode resolution failed: ${error.message}" }
            return Result.failure(error)
        }

        log.d { "Calling repository: model=${options.modelId.value}, temp=${options.temperature.value}" }

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
            log.i {
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
            log.e(throwable) { "Generation failed: ${mappedError.message}" }
            throw mappedError
        }
    }
}
