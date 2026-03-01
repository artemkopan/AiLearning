package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.*
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.RetrievedContextChunk
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

internal class SearchRelevantContextOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        agentId: Lazy<AgentId>,
        queryEmbedding: Lazy<List<Double>>,
        limit: Lazy<Int>,
        minScore: Lazy<Double>,
    ): Result<List<RetrievedContextChunk>> = runtime.value.runDb {
        if (queryEmbedding.value.isEmpty()) return@runDb emptyList()

        val ranked = ScopedAgentMessageEmbeddingsTable.selectAll()
            .where {
                (ScopedAgentMessageEmbeddingsTable.userId eq userId.value.value) and
                    (ScopedAgentMessageEmbeddingsTable.agentId eq agentId.value.value)
            }
            .mapNotNull { row ->
                val candidateEmbedding = parseEmbedding(row[ScopedAgentMessageEmbeddingsTable.embedding])
                if (candidateEmbedding.isEmpty()) return@mapNotNull null
                val score = cosineSimilarity(queryEmbedding.value, candidateEmbedding)
                if (score < minScore.value) return@mapNotNull null
                SearchCandidate(
                    messageId = AgentMessageId(row[ScopedAgentMessageEmbeddingsTable.messageId]),
                    text = row[ScopedAgentMessageEmbeddingsTable.textChunk],
                    score = score,
                    createdAt = row[ScopedAgentMessageEmbeddingsTable.createdAt],
                )
            }
            .sortedWith(compareByDescending<SearchCandidate> { it.score }.thenByDescending { it.createdAt })
            .take(limit.value)

        ranked.map {
            RetrievedContextChunk(
                messageId = it.messageId,
                text = it.text,
                score = it.score,
                createdAt = it.createdAt,
            )
        }
    }
}
