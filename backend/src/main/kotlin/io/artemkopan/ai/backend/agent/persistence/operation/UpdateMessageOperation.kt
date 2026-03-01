package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresStateHelpers
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentMessagesTable
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update

internal class UpdateMessageOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        agentId: Lazy<AgentId>,
        messageId: Lazy<AgentMessageId>,
        text: Lazy<String?>,
        status: Lazy<String?>,
        provider: Lazy<String?>,
        model: Lazy<String?>,
        usageInputTokens: Lazy<Int?>,
        usageOutputTokens: Lazy<Int?>,
        usageTotalTokens: Lazy<Int?>,
        latencyMs: Lazy<Long?>,
    ): Result<AgentState> = runtime.value.runDb {
        val user = userId.value
        val agent = agentId.value
        val message = messageId.value
        val nextText = text.value
        val nextStatus = status.value
        val nextProvider = provider.value
        val nextModel = model.value
        val nextUsageInputTokens = usageInputTokens.value
        val nextUsageOutputTokens = usageOutputTokens.value
        val nextUsageTotalTokens = usageTotalTokens.value
        val nextLatencyMs = latencyMs.value
        val updated = ScopedAgentMessagesTable.update({
            (ScopedAgentMessagesTable.userId eq user.value) and
                (ScopedAgentMessagesTable.agentId eq agent.value) and
                (ScopedAgentMessagesTable.id eq message.value)
        }) { row ->
            if (nextText != null) row[ScopedAgentMessagesTable.text] = nextText
            if (nextStatus != null) row[ScopedAgentMessagesTable.status] = nextStatus
            if (nextProvider != null) row[ScopedAgentMessagesTable.provider] = nextProvider
            if (nextModel != null) row[ScopedAgentMessagesTable.model] = nextModel
            if (nextUsageInputTokens != null) row[ScopedAgentMessagesTable.usageInputTokens] = nextUsageInputTokens
            if (nextUsageOutputTokens != null) row[ScopedAgentMessagesTable.usageOutputTokens] = nextUsageOutputTokens
            if (nextUsageTotalTokens != null) row[ScopedAgentMessagesTable.usageTotalTokens] = nextUsageTotalTokens
            if (nextLatencyMs != null) row[ScopedAgentMessagesTable.latencyMs] = nextLatencyMs
        }
        require(updated > 0) { "Message not found: ${message.value}" }

        stateHelpers.value.bumpVersionTx(user)
        stateHelpers.value.readStateTx(user)
    }
}
