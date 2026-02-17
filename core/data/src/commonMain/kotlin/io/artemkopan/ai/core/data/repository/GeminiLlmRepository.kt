package io.artemkopan.ai.core.data.repository

import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.data.client.NetworkGenerateRequest
import io.artemkopan.ai.core.domain.model.LlmGeneration
import io.artemkopan.ai.core.domain.model.LlmGenerationInput
import io.artemkopan.ai.core.domain.model.TokenUsage
import io.artemkopan.ai.core.domain.repository.LlmRepository

class GeminiLlmRepository(
    private val networkClient: LlmNetworkClient,
) : LlmRepository {
    override suspend fun generate(input: LlmGenerationInput): Result<LlmGeneration> {
        return networkClient.generate(
            NetworkGenerateRequest(
                prompt = input.prompt.value,
                model = input.modelId.value,
                temperature = input.temperature.value,
            )
        ).map { response ->
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
        }
    }
}
