package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository

class IndexMessageEmbeddingsUseCase(
    private val repository: AgentRepository,
    private val llmRepository: LlmRepository,
    private val enabled: Boolean = true,
    private val embeddingModel: String,
    private val chunkSizeChars: Int = 1_200,
) {
    suspend fun execute(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
        text: String,
        createdAt: Long,
    ): Result<Unit> {
        if (!enabled) return Result.success(Unit)

        val normalized = text.trim()
        if (normalized.isEmpty()) return Result.success(Unit)
        val normalizedCreatedAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()

        val chunks = splitIntoChunks(normalized)
        chunks.forEachIndexed { index, chunk ->
            val embedding = llmRepository.embed(chunk, embeddingModel).getOrElse { return Result.failure(it) }
            repository.upsertMessageEmbedding(
                userId = userId,
                agentId = agentId,
                messageId = messageId,
                chunkIndex = index,
                textChunk = chunk,
                embedding = embedding.values,
                createdAt = normalizedCreatedAt,
            ).getOrElse { return Result.failure(it) }
        }

        return Result.success(Unit)
    }

    private fun splitIntoChunks(text: String): List<String> {
        if (text.length <= chunkSizeChars) return listOf(text)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(text.length, start + chunkSizeChars)
            chunks += text.substring(start, end).trim()
            start = end
        }
        return chunks.filter { it.isNotBlank() }
    }
}
