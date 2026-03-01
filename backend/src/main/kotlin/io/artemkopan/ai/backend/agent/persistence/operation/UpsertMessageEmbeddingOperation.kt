package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentMessageEmbeddingsTable
import io.artemkopan.ai.backend.agent.persistence.helper.serializeEmbedding
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

internal class UpsertMessageEmbeddingOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        agentId: Lazy<AgentId>,
        messageId: Lazy<AgentMessageId>,
        chunkIndex: Lazy<Int>,
        textChunk: Lazy<String>,
        embedding: Lazy<List<Double>>,
        createdAt: Lazy<Long>,
    ): Result<Unit> = runtime.value.runDb {
        val user = userId.value
        val agent = agentId.value
        val message = messageId.value
        val chunk = chunkIndex.value
        val now = runtime.value.nowMillis()
        val embeddingPayload = serializeEmbedding(embedding.value)
        val existing = ScopedAgentMessageEmbeddingsTable.selectAll().where {
            (ScopedAgentMessageEmbeddingsTable.userId eq user.value) and
                (ScopedAgentMessageEmbeddingsTable.agentId eq agent.value) and
                (ScopedAgentMessageEmbeddingsTable.messageId eq message.value) and
                (ScopedAgentMessageEmbeddingsTable.chunkIndex eq chunk)
        }.singleOrNull()

        if (existing == null) {
            ScopedAgentMessageEmbeddingsTable.insert { row ->
                row[ScopedAgentMessageEmbeddingsTable.userId] = user.value
                row[ScopedAgentMessageEmbeddingsTable.agentId] = agent.value
                row[ScopedAgentMessageEmbeddingsTable.messageId] = message.value
                row[ScopedAgentMessageEmbeddingsTable.chunkIndex] = chunk
                row[ScopedAgentMessageEmbeddingsTable.textChunk] = textChunk.value
                row[ScopedAgentMessageEmbeddingsTable.embedding] = embeddingPayload
                row[ScopedAgentMessageEmbeddingsTable.createdAt] = createdAt.value.takeIf { it > 0 } ?: now
                row[ScopedAgentMessageEmbeddingsTable.updatedAt] = now
            }
        } else {
            ScopedAgentMessageEmbeddingsTable.update({
                (ScopedAgentMessageEmbeddingsTable.userId eq user.value) and
                    (ScopedAgentMessageEmbeddingsTable.agentId eq agent.value) and
                    (ScopedAgentMessageEmbeddingsTable.messageId eq message.value) and
                    (ScopedAgentMessageEmbeddingsTable.chunkIndex eq chunk)
            }) { row ->
                row[ScopedAgentMessageEmbeddingsTable.textChunk] = textChunk.value
                row[ScopedAgentMessageEmbeddingsTable.embedding] = embeddingPayload
                row[ScopedAgentMessageEmbeddingsTable.updatedAt] = now
            }
        }
    }
}
