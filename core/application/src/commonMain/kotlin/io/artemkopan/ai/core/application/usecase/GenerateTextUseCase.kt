package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.model.GenerateOutput
import io.artemkopan.ai.core.application.model.UsageOutput
import io.artemkopan.ai.core.domain.error.DomainError
import io.artemkopan.ai.core.domain.model.LlmGenerationInput
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.github.aakira.napier.Napier

class GenerateTextUseCase(
    private val repository: LlmRepository,
    private val validatePromptUseCase: ValidatePromptUseCase,
    private val resolveGenerationOptionsUseCase: ResolveGenerationOptionsUseCase,
) {
    suspend fun execute(command: GenerateCommand): Result<GenerateOutput> {
        Napier.d(tag = TAG) { "Executing generate text: promptLength=${command.prompt.length}" }

        val prompt = validatePromptUseCase.execute(command.prompt).getOrElse { error ->
            Napier.w(tag = TAG) { "Prompt validation failed: ${error.message}" }
            return Result.failure(error)
        }

        val options = resolveGenerationOptionsUseCase.execute(command.model, command.temperature)
            .getOrElse { error ->
                Napier.w(tag = TAG) { "Options resolution failed: ${error.message}" }
                return Result.failure(error)
            }

        Napier.d(tag = TAG) { "Calling repository: model=${options.modelId.value}, temp=${options.temperature.value}" }

        return repository.generate(
            LlmGenerationInput(
                prompt = prompt,
                modelId = options.modelId,
                temperature = options.temperature,
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
            val mappedError = when (throwable) {
                is DomainError.RateLimited -> AppError.RateLimited(throwable.message ?: "Rate limited", throwable)
                is DomainError.ProviderUnavailable -> AppError.UpstreamUnavailable(
                    throwable.message ?: "Provider unavailable",
                    throwable,
                )
                is AppError -> throwable
                else -> AppError.Unexpected("Unexpected generation failure.", throwable)
            }
            Napier.e(tag = TAG, throwable = throwable) { "Generation failed: ${mappedError.message}" }
            throw mappedError
        }
    }

    private companion object {
        const val TAG = "GenerateTextUseCase"
    }
}
