package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.RetrievedContextChunk
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository

class RetrieveRelevantContextUseCase(
    private val repository: AgentRepository,
    private val llmRepository: LlmRepository,
    private val enabled: Boolean = true,
    private val embeddingModel: String,
    private val topK: Int,
    private val minScore: Double,
) {
    suspend fun execute(
        userId: UserId,
        agentId: AgentId,
        queryText: String,
    ): Result<List<RetrievedContextChunk>> {
        if (!enabled) return Result.success(emptyList())

        val normalized = queryText.trim()
        if (normalized.isEmpty()) return Result.success(emptyList())

        val embedding = llmRepository.embed(normalized, embeddingModel).getOrElse { return Result.failure(it) }
        return repository.searchRelevantContext(
            userId = userId,
            agentId = agentId,
            queryEmbedding = embedding.values,
            limit = topK,
            minScore = minScore,
        )
    }
}
