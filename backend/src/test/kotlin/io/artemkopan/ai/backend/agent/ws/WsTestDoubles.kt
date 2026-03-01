package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.application.usecase.MapFailureToUserMessageUseCase
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy

fun testAgentState(
    agentId: String = "agent-1",
    version: Long = 1L,
): AgentState {
    return AgentState(
        agents = listOf(
            Agent(
                id = AgentId(agentId),
                title = "agent",
                status = AgentStatus("done"),
            )
        ),
        activeAgentId = AgentId(agentId),
        version = version,
    )
}

fun dummySession(): DefaultWebSocketServerSession {
    val invocationHandler = java.lang.reflect.InvocationHandler { _, method, _ ->
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Void.TYPE -> null
            else -> null
        }
    }
    return Proxy.newProxyInstance(
        DefaultWebSocketServerSession::class.java.classLoader,
        arrayOf(DefaultWebSocketServerSession::class.java),
        invocationHandler,
    ) as DefaultWebSocketServerSession
}

open class RecordingOutboundService : AgentWsOutboundService(
    sessionRegistry = AgentWsSessionRegistry(),
    mapper = AgentWsMapper(),
    json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "type"
    },
    mapFailureToUserMessageUseCase = MapFailureToUserMessageUseCase(),
) {
    val snapshots = mutableListOf<AgentState>()
    val broadcasts = mutableListOf<Pair<String, AgentState>>()
    val errors = mutableListOf<Pair<String?, String>>()
    val operationFailures = mutableListOf<Pair<String?, Throwable>>()

    override suspend fun sendSnapshot(session: DefaultWebSocketServerSession, state: AgentState) {
        snapshots += state
    }

    override suspend fun broadcastSnapshot(userScope: String, state: AgentState) {
        broadcasts += userScope to state
    }

    override suspend fun sendOperationFailure(
        session: DefaultWebSocketServerSession,
        throwable: Throwable,
        requestId: String?,
    ) {
        operationFailures += requestId to throwable
    }

    override suspend fun sendError(session: DefaultWebSocketServerSession, message: String, requestId: String?) {
        errors += requestId to message
    }
}

class FakeAgentRepository(
    var currentState: AgentState = testAgentState(),
) : AgentRepository {
    var createAgentResult: AgentState = currentState
    var findMessageResult: AgentMessage? = null
    val updatedStatuses = mutableListOf<AgentStatus>()
    val updatedMessageStatuses = mutableListOf<String?>()

    override suspend fun getState(userId: UserId): Result<AgentState> = Result.success(currentState)

    override suspend fun createAgent(userId: UserId): Result<AgentState> {
        currentState = createAgentResult
        return Result.success(createAgentResult)
    }

    override suspend fun selectAgent(userId: UserId, agentId: AgentId): Result<AgentState> = unsupported()

    override suspend fun updateAgentDraft(userId: UserId, agentId: AgentId, draft: AgentDraft): Result<AgentState> = unsupported()

    override suspend fun closeAgent(userId: UserId, agentId: AgentId): Result<AgentState> = unsupported()

    override suspend fun updateAgentStatus(userId: UserId, agentId: AgentId, status: AgentStatus): Result<AgentState> {
        updatedStatuses += status
        return Result.success(currentState.copy(version = currentState.version + 1))
    }

    override suspend fun appendMessage(userId: UserId, agentId: AgentId, message: AgentMessage): Result<AgentState> = unsupported()

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
        updatedMessageStatuses += status
        return Result.success(currentState.copy(version = currentState.version + 1))
    }

    override suspend fun findMessage(userId: UserId, agentId: AgentId, messageId: AgentMessageId): Result<AgentMessage?> {
        return Result.success(findMessageResult)
    }

    override suspend fun hasProcessingMessage(userId: UserId, agentId: AgentId): Result<Boolean> = unsupported()

    override suspend fun getContextMemory(userId: UserId, agentId: AgentId): Result<AgentContextMemory?> = unsupported()

    override suspend fun upsertContextMemory(userId: UserId, memory: AgentContextMemory): Result<Unit> = unsupported()

    override suspend fun listMessagesAfter(
        userId: UserId,
        agentId: AgentId,
        createdAtExclusive: Long,
    ): Result<List<AgentMessage>> = unsupported()

    override suspend fun upsertMessageEmbedding(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
        chunkIndex: Int,
        textChunk: String,
        embedding: List<Double>,
        createdAt: Long,
    ): Result<Unit> = unsupported()

    override suspend fun searchRelevantContext(
        userId: UserId,
        agentId: AgentId,
        queryEmbedding: List<Double>,
        limit: Int,
        minScore: Double,
    ): Result<List<RetrievedContextChunk>> = unsupported()

    override suspend fun getAgentFacts(userId: UserId, agentId: AgentId): Result<AgentFacts?> = unsupported()

    override suspend fun upsertAgentFacts(userId: UserId, facts: AgentFacts): Result<Unit> = unsupported()

    override suspend fun createBranch(userId: UserId, agentId: AgentId, branch: AgentBranch): Result<AgentState> = unsupported()

    override suspend fun switchBranch(userId: UserId, agentId: AgentId, branchId: String): Result<AgentState> = unsupported()

    override suspend fun deleteBranch(userId: UserId, agentId: AgentId, branchId: String): Result<AgentState> = unsupported()

    override suspend fun getBranches(userId: UserId, agentId: AgentId): Result<List<AgentBranch>> = unsupported()

    private fun <T> unsupported(): Result<T> {
        return Result.failure(UnsupportedOperationException("Not implemented for this test"))
    }
}
