package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.model.GenerateOutput
import io.artemkopan.ai.core.application.model.UsageOutput
import io.artemkopan.ai.core.domain.error.DomainError
import io.artemkopan.ai.core.domain.model.LlmGenerationInput
import io.artemkopan.ai.core.domain.repository.LlmRepository

class GenerateTextUseCase(
    private val repository: LlmRepository,
    private val validatePromptUseCase: ValidatePromptUseCase,
    private val resolveGenerationOptionsUseCase: ResolveGenerationOptionsUseCase,
) {
    suspend fun execute(command: GenerateCommand): Result<GenerateOutput> {
        val prompt = validatePromptUseCase.execute(command.prompt).getOrElse { return Result.failure(it) }
        val options = resolveGenerationOptionsUseCase.execute(command.model, command.temperature)
            .getOrElse { return Result.failure(it) }

        return repository.generate(
            LlmGenerationInput(
                prompt = prompt,
                modelId = options.modelId,
                temperature = options.temperature,
            )
        ).map { generation ->
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
            throw when (throwable) {
                is DomainError.RateLimited -> AppError.RateLimited(throwable.message ?: "Rate limited", throwable)
                is DomainError.ProviderUnavailable -> AppError.UpstreamUnavailable(
                    throwable.message ?: "Provider unavailable",
                    throwable,
                )
                is AppError -> throwable
                else -> AppError.Unexpected("Unexpected generation failure.", throwable)
            }
        }
    }
}
