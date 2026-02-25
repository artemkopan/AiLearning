package io.artemkopan.ai.core.data.repository

import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.data.client.NetworkEmbedRequest
import io.artemkopan.ai.core.data.client.NetworkGenerateRequest
import io.artemkopan.ai.core.data.error.DataError
import io.artemkopan.ai.core.domain.error.DomainError
import io.artemkopan.ai.core.domain.model.LlmEmbedding
import io.artemkopan.ai.core.domain.model.LlmGeneration
import io.artemkopan.ai.core.domain.model.LlmGenerationInput
import io.artemkopan.ai.core.domain.model.TokenUsage
import io.artemkopan.ai.core.domain.repository.LlmRepository
import co.touchlab.kermit.Logger

class GeminiLlmRepository(
    private val networkClient: LlmNetworkClient,
) : LlmRepository {

    private val log = Logger.withTag("GeminiLlmRepository")

    override suspend fun generate(input: LlmGenerationInput): Result<LlmGeneration> {
        log.d { "Repository generate called: model=${input.modelId.value}" }

        return networkClient.generate(
            NetworkGenerateRequest(
                prompt = input.prompt.value,
                model = input.modelId.value,
                temperature = input.temperature.value,
                maxOutputTokens = input.maxOutputTokens?.value,
                stopSequences = input.stopSequences?.values,
                systemInstruction = input.systemInstruction?.value,
            )
        ).map { response ->
            log.d { "Repository mapping response: provider=${response.provider}" }
            LlmGeneration(
                text = response.text,
                provider = response.provider,
                model = response.model,
                usage = response.usage?.let {
                    TokenUsage(
                        inputTokens = it.inputTokens,
                        outputTokens = it.outputTokens,
                        totalTokens = it.totalTokens,
                    )
                },
            )
        }.recoverCatching { throwable ->
            log.e(throwable) { "Repository generate failed" }
            throw mapToDomainError(throwable)
        }
    }

    override suspend fun embed(text: String, model: String): Result<LlmEmbedding> {
        log.d { "Repository embed called: model=$model, textLength=${text.length}" }

        return networkClient.embed(
            NetworkEmbedRequest(
                text = text,
                model = model,
            )
        ).map { response ->
            LlmEmbedding(
                values = response.values,
                model = response.model,
                provider = response.provider,
            )
        }.recoverCatching { throwable ->
            log.e(throwable) { "Repository embed failed" }
            throw mapToDomainError(throwable)
        }
    }

    private fun mapToDomainError(throwable: Throwable): DomainError = when (throwable) {
        is DataError.RateLimitError -> DomainError.RateLimited(throwable.message ?: "Rate limited", throwable)
        is DataError.AuthenticationError -> DomainError.ProviderUnavailable(throwable.message ?: "Auth failed", throwable)
        is DataError.EmptyResponseError -> DomainError.ProviderUnavailable(throwable.message ?: "Empty response", throwable)
        is DataError.NetworkError -> DomainError.ProviderUnavailable(throwable.message ?: "Network error", throwable)
        is DomainError -> throwable
        else -> DomainError.Unexpected(throwable.message ?: "Unexpected error", throwable)
    }
}
