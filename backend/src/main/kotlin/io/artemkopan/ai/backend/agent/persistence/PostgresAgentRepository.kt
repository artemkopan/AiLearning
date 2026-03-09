package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.AgentRepository
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.koin.core.annotation.Single

@Single(binds = [AgentRepository::class])
internal class PostgresAgentRepository(
    private val runtime: PostgresDbRuntime,
    private val json: Json,
) : AgentRepository {

    override suspend fun getState(userId: UserId) =
        runCatching { runtime.readState(userId) }

    override suspend fun createAgent(userId: UserId) =
        runtime.runDb {
            val meta = loadMeta(userId)
            val counter = meta[ScopedAgentStateTable.nextAgentCounter]
            val newId = "agent-$counter"
            val position = ScopedAgentsTable.selectAll()
                .where { ScopedAgentsTable.userId eq userId.value }
                .map { it[ScopedAgentsTable.position] }
                .maxOrNull()?.plus(1) ?: 0
            val now = runtime.nowMillis()

            ScopedAgentsTable.insert {
                it[ScopedAgentsTable.userId] = userId.value
                it[ScopedAgentsTable.id] = newId
                it[ScopedAgentsTable.title] = "Agent $counter"
                it[ScopedAgentsTable.model] = ""
                it[ScopedAgentsTable.maxOutputTokens] = ""
                it[ScopedAgentsTable.temperature] = ""
                it[ScopedAgentsTable.stopSequences] = ""
                it[ScopedAgentsTable.agentMode] = "default"
                it[ScopedAgentsTable.invariants] = "[]"
                it[ScopedAgentsTable.contextStrategy] = CONTEXT_STRATEGY_ROLLING
                it[ScopedAgentsTable.status] = STATUS_DONE
                it[ScopedAgentsTable.position] = position
                it[ScopedAgentsTable.createdAt] = now
                it[ScopedAgentsTable.updatedAt] = now
            }

            ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) {
                it[ScopedAgentStateTable.activeAgentId] = newId
                it[ScopedAgentStateTable.nextAgentCounter] = counter + 1
                it[ScopedAgentStateTable.version] = meta[ScopedAgentStateTable.version] + 1
                it[ScopedAgentStateTable.updatedAt] = now
            }
        }.mapCatching { runtime.readState(userId) }

    override suspend fun selectAgent(userId: UserId, agentId: AgentId) =
        runtime.runDb {
            ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) {
                it[ScopedAgentStateTable.activeAgentId] = agentId.value
                it[ScopedAgentStateTable.updatedAt] = runtime.nowMillis()
            }
            bumpVersionTx(userId)
        }.mapCatching { runtime.readState(userId) }

    override suspend fun closeAgent(userId: UserId, agentId: AgentId) =
        runtime.runDb {
            ScopedAgentMessagesTable.deleteWhere { builder ->
                with(builder) {
                    (ScopedAgentMessagesTable.userId eq userId.value) and (ScopedAgentMessagesTable.agentId eq agentId.value)
                }
            }
            ScopedAgentsTable.deleteWhere { builder ->
                with(builder) {
                    (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value)
                }
            }
        }.mapCatching {
            val state = runtime.readState(userId)
            val newActive = state.agents.firstOrNull()?.id?.value
            runtime.runDb {
                ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) {
                    it[ScopedAgentStateTable.activeAgentId] = newActive
                    it[ScopedAgentStateTable.updatedAt] = runtime.nowMillis()
                }
            }.getOrThrow()
            runtime.readState(userId)
        }

    override suspend fun updateAgentStatus(userId: UserId, agentId: AgentId, status: AgentStatus) =
        runtime.runDb {
            ScopedAgentsTable.update({ (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value) }) {
                it[ScopedAgentsTable.status] = status.value
                it[ScopedAgentsTable.updatedAt] = runtime.nowMillis()
            }
            bumpVersionTx(userId)
        }.mapCatching { runtime.readState(userId) }

    override suspend fun appendMessage(userId: UserId, agentId: AgentId, message: AgentMessage) =
        runtime.runDb {
            val existing = ScopedAgentsTable.selectAll()
                .where { (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value) }
                .singleOrNull() ?: throw IllegalStateException("Agent not found: ${agentId.value}")

            ScopedAgentMessagesTable.insert {
                it[ScopedAgentMessagesTable.userId] = userId.value
                it[ScopedAgentMessagesTable.id] = message.id.value
                it[ScopedAgentMessagesTable.agentId] = agentId.value
                it[ScopedAgentMessagesTable.branchId] = existing[ScopedAgentsTable.activeBranchId]
                it[ScopedAgentMessagesTable.role] = when (message.role) {
                    AgentMessageRole.USER -> ROLE_USER
                    AgentMessageRole.ASSISTANT -> ROLE_ASSISTANT
                }
                it[ScopedAgentMessagesTable.text] = message.text
                it[ScopedAgentMessagesTable.status] = message.status
                it[ScopedAgentMessagesTable.createdAt] = message.createdAt
                it[ScopedAgentMessagesTable.provider] = message.provider
                it[ScopedAgentMessagesTable.model] = message.model
                it[ScopedAgentMessagesTable.usageInputTokens] = message.usage?.inputTokens
                it[ScopedAgentMessagesTable.usageOutputTokens] = message.usage?.outputTokens
                it[ScopedAgentMessagesTable.usageTotalTokens] = message.usage?.totalTokens
                it[ScopedAgentMessagesTable.latencyMs] = message.latencyMs
                it[ScopedAgentMessagesTable.messageType] = message.messageType.name.lowercase()
            }

            bumpVersionTx(userId)
        }.mapCatching { runtime.readState(userId) }

    override suspend fun updateMessage(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
        text: String?,
        status: String?,
        provider: String?,
        model: String?,
        usageInputTokens: Int?,
        usageOutputTokens: Int?,
        usageTotalTokens: Int?,
        latencyMs: Long?,
        messageType: AgentMessageType?,
    ) = runtime.runDb {
        ScopedAgentMessagesTable.update({ (ScopedAgentMessagesTable.userId eq userId.value) and (ScopedAgentMessagesTable.id eq messageId.value) }) { row ->
            text?.let { row[ScopedAgentMessagesTable.text] = it }
            status?.let { row[ScopedAgentMessagesTable.status] = it }
            provider?.let { row[ScopedAgentMessagesTable.provider] = it }
            model?.let { row[ScopedAgentMessagesTable.model] = it }
            usageInputTokens?.let { row[ScopedAgentMessagesTable.usageInputTokens] = it }
            usageOutputTokens?.let { row[ScopedAgentMessagesTable.usageOutputTokens] = it }
            usageTotalTokens?.let { row[ScopedAgentMessagesTable.usageTotalTokens] = it }
            latencyMs?.let { row[ScopedAgentMessagesTable.latencyMs] = it }
            messageType?.let { row[ScopedAgentMessagesTable.messageType] = it.name.lowercase() }
        }
        bumpVersionTx(userId)
    }.mapCatching { runtime.readState(userId) }

    override suspend fun findMessage(userId: UserId, agentId: AgentId, messageId: AgentMessageId) =
        runtime.runDb {
            ScopedAgentMessagesTable.selectAll()
                .where {
                    (ScopedAgentMessagesTable.userId eq userId.value) and
                        (ScopedAgentMessagesTable.agentId eq agentId.value) and
                        (ScopedAgentMessagesTable.id eq messageId.value)
                }
                .singleOrNull()
                ?.toMessage()
        }

    override suspend fun hasProcessingMessage(userId: UserId, agentId: AgentId) =
        runtime.runDb {
            ScopedAgentMessagesTable.selectAll()
                .where {
                    (ScopedAgentMessagesTable.userId eq userId.value) and
                        (ScopedAgentMessagesTable.agentId eq agentId.value) and
                        (ScopedAgentMessagesTable.status eq STATUS_PROCESSING)
                }
                .count() > 0
        }

    override suspend fun updateAgentDraft(userId: UserId, agentId: AgentId, draft: AgentDraft) =
        runtime.runDb {
            ScopedAgentsTable.update({ (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value) }) {
                it[ScopedAgentsTable.model] = draft.model
                it[ScopedAgentsTable.maxOutputTokens] = draft.maxOutputTokens
                it[ScopedAgentsTable.temperature] = draft.temperature
                it[ScopedAgentsTable.stopSequences] = draft.stopSequences
                it[ScopedAgentsTable.agentMode] = draft.agentMode
                it[ScopedAgentsTable.invariants] = json.encodeToString(draft.invariants)
                it[ScopedAgentsTable.updatedAt] = runtime.nowMillis()
            }
            bumpVersionTx(userId)
        }.mapCatching { runtime.readState(userId) }

    override suspend fun getContextMemory(userId: UserId, agentId: AgentId) =
        Result.success(null)

    override suspend fun upsertContextMemory(userId: UserId, memory: AgentContextMemory) =
        Result.success(Unit)

    override suspend fun listMessagesAfter(userId: UserId, agentId: AgentId, createdAtExclusive: Long) =
        runtime.runDb {
            ScopedAgentMessagesTable.selectAll()
                .where {
                    (ScopedAgentMessagesTable.userId eq userId.value) and
                        (ScopedAgentMessagesTable.agentId eq agentId.value) and
                        (ScopedAgentMessagesTable.createdAt greater createdAtExclusive)
                }
                .orderBy(ScopedAgentMessagesTable.createdAt, SortOrder.ASC)
                .map { it.toMessage() }
        }

    override suspend fun upsertMessageEmbedding(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
        chunkIndex: Int,
        textChunk: String,
        embedding: List<Double>,
        createdAt: Long,
    ) = Result.success(Unit)

    override suspend fun searchRelevantContext(
        userId: UserId,
        agentId: AgentId,
        queryEmbedding: List<Double>,
        limit: Int,
        minScore: Double,
    ): Result<List<RetrievedContextChunk>> = Result.success(emptyList())

    override suspend fun getAgentFacts(userId: UserId, agentId: AgentId) =
        Result.success(null)

    override suspend fun upsertAgentFacts(userId: UserId, facts: AgentFacts) =
        Result.success(Unit)

    override suspend fun createBranch(userId: UserId, agentId: AgentId, branch: AgentBranch) =
        Result.failure<AgentState>(UnsupportedOperationException("Branches not supported"))

    override suspend fun switchBranch(userId: UserId, agentId: AgentId, branchId: String) =
        Result.failure<AgentState>(UnsupportedOperationException("Branches not supported"))

    override suspend fun deleteBranch(userId: UserId, agentId: AgentId, branchId: String) =
        Result.failure<AgentState>(UnsupportedOperationException("Branches not supported"))

    override suspend fun getBranches(userId: UserId, agentId: AgentId): Result<List<AgentBranch>> =
        Result.success(emptyList())

    override suspend fun updateAgentInvariants(userId: UserId, agentId: AgentId, invariants: List<String>) =
        runtime.runDb {
            ScopedAgentsTable.update({ (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value) }) {
                it[ScopedAgentsTable.invariants] = json.encodeToString(invariants)
                it[ScopedAgentsTable.updatedAt] = runtime.nowMillis()
            }
            bumpVersionTx(userId)
        }.mapCatching { runtime.readState(userId) }

    private fun loadMeta(userId: UserId): ResultRow =
        loadMetaTx(runtime, userId)

    private fun bumpVersionTx(userId: UserId) {
        val meta = ScopedAgentStateTable.selectAll()
            .where { ScopedAgentStateTable.userId eq userId.value }
            .single()
        ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) {
            it[ScopedAgentStateTable.version] = meta[ScopedAgentStateTable.version] + 1
            it[ScopedAgentStateTable.updatedAt] = runtime.nowMillis()
        }
    }
}
