package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.backend.agent.persistence.operation.*
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.AgentRepository

class PostgresAgentRepository internal constructor(
    private val getStateOperation: Lazy<GetStateOperation>,
    private val createAgentOperation: Lazy<CreateAgentOperation>,
    private val selectAgentOperation: Lazy<SelectAgentOperation>,
    private val updateAgentDraftOperation: Lazy<UpdateAgentDraftOperation>,
    private val closeAgentOperation: Lazy<CloseAgentOperation>,
    private val updateAgentStatusOperation: Lazy<UpdateAgentStatusOperation>,
    private val appendMessageOperation: Lazy<AppendMessageOperation>,
    private val updateMessageOperation: Lazy<UpdateMessageOperation>,
    private val findMessageOperation: Lazy<FindMessageOperation>,
    private val hasProcessingMessageOperation: Lazy<HasProcessingMessageOperation>,
    private val getContextMemoryOperation: Lazy<GetContextMemoryOperation>,
    private val upsertContextMemoryOperation: Lazy<UpsertContextMemoryOperation>,
    private val listMessagesAfterOperation: Lazy<ListMessagesAfterOperation>,
    private val upsertMessageEmbeddingOperation: Lazy<UpsertMessageEmbeddingOperation>,
    private val searchRelevantContextOperation: Lazy<SearchRelevantContextOperation>,
    private val getAgentFactsOperation: Lazy<GetAgentFactsOperation>,
    private val upsertAgentFactsOperation: Lazy<UpsertAgentFactsOperation>,
    private val createBranchOperation: Lazy<CreateBranchOperation>,
    private val switchBranchOperation: Lazy<SwitchBranchOperation>,
    private val deleteBranchOperation: Lazy<DeleteBranchOperation>,
    private val getBranchesOperation: Lazy<GetBranchesOperation>,
) : AgentRepository {

    override suspend fun getState(userId: UserId): Result<AgentState> {
        return getStateOperation.value.execute(userId = lazyArg { userId })
    }

    override suspend fun createAgent(userId: UserId): Result<AgentState> {
        return createAgentOperation.value.execute(userId = lazyArg { userId })
    }

    override suspend fun selectAgent(userId: UserId, agentId: AgentId): Result<AgentState> {
        return selectAgentOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
        )
    }

    override suspend fun updateAgentDraft(
        userId: UserId,
        agentId: AgentId,
        draft: AgentDraft,
    ): Result<AgentState> {
        return updateAgentDraftOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            draft = lazyArg { draft },
        )
    }

    override suspend fun closeAgent(userId: UserId, agentId: AgentId): Result<AgentState> {
        return closeAgentOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
        )
    }

    override suspend fun updateAgentStatus(
        userId: UserId,
        agentId: AgentId,
        status: AgentStatus,
    ): Result<AgentState> {
        return updateAgentStatusOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            status = lazyArg { status },
        )
    }

    override suspend fun appendMessage(
        userId: UserId,
        agentId: AgentId,
        message: AgentMessage,
    ): Result<AgentState> {
        return appendMessageOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            message = lazyArg { message },
        )
    }

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
    ): Result<AgentState> {
        return updateMessageOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            messageId = lazyArg { messageId },
            text = lazyArg { text },
            status = lazyArg { status },
            provider = lazyArg { provider },
            model = lazyArg { model },
            usageInputTokens = lazyArg { usageInputTokens },
            usageOutputTokens = lazyArg { usageOutputTokens },
            usageTotalTokens = lazyArg { usageTotalTokens },
            latencyMs = lazyArg { latencyMs },
        )
    }

    override suspend fun findMessage(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
    ): Result<AgentMessage?> {
        return findMessageOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            messageId = lazyArg { messageId },
        )
    }

    override suspend fun hasProcessingMessage(userId: UserId, agentId: AgentId): Result<Boolean> {
        return hasProcessingMessageOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
        )
    }

    override suspend fun getContextMemory(userId: UserId, agentId: AgentId): Result<AgentContextMemory?> {
        return getContextMemoryOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
        )
    }

    override suspend fun upsertContextMemory(userId: UserId, memory: AgentContextMemory): Result<Unit> {
        return upsertContextMemoryOperation.value.execute(
            userId = lazyArg { userId },
            memory = lazyArg { memory },
        )
    }

    override suspend fun listMessagesAfter(
        userId: UserId,
        agentId: AgentId,
        createdAtExclusive: Long,
    ): Result<List<AgentMessage>> {
        return listMessagesAfterOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            createdAtExclusive = lazyArg { createdAtExclusive },
        )
    }

    override suspend fun upsertMessageEmbedding(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
        chunkIndex: Int,
        textChunk: String,
        embedding: List<Double>,
        createdAt: Long,
    ): Result<Unit> {
        return upsertMessageEmbeddingOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            messageId = lazyArg { messageId },
            chunkIndex = lazyArg { chunkIndex },
            textChunk = lazyArg { textChunk },
            embedding = lazyArg { embedding },
            createdAt = lazyArg { createdAt },
        )
    }

    override suspend fun searchRelevantContext(
        userId: UserId,
        agentId: AgentId,
        queryEmbedding: List<Double>,
        limit: Int,
        minScore: Double,
    ): Result<List<RetrievedContextChunk>> {
        return searchRelevantContextOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            queryEmbedding = lazyArg { queryEmbedding },
            limit = lazyArg { limit },
            minScore = lazyArg { minScore },
        )
    }

    override suspend fun getAgentFacts(userId: UserId, agentId: AgentId): Result<AgentFacts?> {
        return getAgentFactsOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
        )
    }

    override suspend fun upsertAgentFacts(userId: UserId, facts: AgentFacts): Result<Unit> {
        return upsertAgentFactsOperation.value.execute(
            userId = lazyArg { userId },
            facts = lazyArg { facts },
        )
    }

    override suspend fun createBranch(
        userId: UserId,
        agentId: AgentId,
        branch: AgentBranch,
    ): Result<AgentState> {
        return createBranchOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            branch = lazyArg { branch },
        )
    }

    override suspend fun switchBranch(
        userId: UserId,
        agentId: AgentId,
        branchId: String,
    ): Result<AgentState> {
        return switchBranchOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            branchId = lazyArg { branchId },
        )
    }

    override suspend fun deleteBranch(
        userId: UserId,
        agentId: AgentId,
        branchId: String,
    ): Result<AgentState> {
        return deleteBranchOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
            branchId = lazyArg { branchId },
        )
    }

    override suspend fun getBranches(userId: UserId, agentId: AgentId): Result<List<AgentBranch>> {
        return getBranchesOperation.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
        )
    }

    private fun <T> lazyArg(valueProvider: () -> T): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE) { valueProvider() }
}
