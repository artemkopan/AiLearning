package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.*
import io.artemkopan.ai.core.domain.model.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.koin.core.annotation.Single

@Single
internal class AppendMessageOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        agentId: Lazy<AgentId>,
        message: Lazy<AgentMessage>,
    ): Result<AgentState> = runtime.value.runDb {
        val user = userId.value
        val agent = agentId.value
        val msg = message.value
        val existing = ScopedAgentsTable.selectAll().where {
            (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
        }.singleOrNull()
        require(existing != null) { "Agent not found: ${agent.value}" }

        val currentMaxCreatedAt = ScopedAgentMessagesTable.selectAll()
            .where {
                (ScopedAgentMessagesTable.userId eq user.value) and
                    (ScopedAgentMessagesTable.agentId eq agent.value)
            }
            .map { it[ScopedAgentMessagesTable.createdAt] }
            .maxOrNull()
        val requestedCreatedAt = msg.createdAt.takeIf { it > 0 } ?: runtime.value.nowMillis()
        val persistedCreatedAt = currentMaxCreatedAt
            ?.let { maxOf(requestedCreatedAt, it + 1) }
            ?: requestedCreatedAt

        val activeBranch = existing[ScopedAgentsTable.activeBranchId]

        ScopedAgentMessagesTable.insert { row ->
            row[ScopedAgentMessagesTable.userId] = user.value
            row[id] = msg.id.value
            row[ScopedAgentMessagesTable.agentId] = agent.value
            row[branchId] = activeBranch
            row[role] = when (msg.role) {
                AgentMessageRole.USER -> ROLE_USER
                AgentMessageRole.ASSISTANT -> ROLE_ASSISTANT
            }
            row[text] = msg.text
            row[status] = msg.status
            row[createdAt] = persistedCreatedAt
            row[provider] = msg.provider
            row[model] = msg.model
            row[usageInputTokens] = msg.usage?.inputTokens
            row[usageOutputTokens] = msg.usage?.outputTokens
            row[usageTotalTokens] = msg.usage?.totalTokens
            row[latencyMs] = msg.latencyMs
            row[messageType] = msg.messageType.name.lowercase()
        }

        if (msg.role == AgentMessageRole.USER && msg.text.isNotBlank()) {
            val currentTitle = existing[ScopedAgentsTable.title]
            if (currentTitle.startsWith("Agent ")) {
                val newTitle = msg.text.lineSequence().firstOrNull()?.trim().orEmpty().take(MAX_TITLE_LENGTH)
                if (newTitle.isNotBlank()) {
                    ScopedAgentsTable.update({
                        (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
                    }) { row ->
                        row[title] = newTitle
                        row[updatedAt] = runtime.value.nowMillis()
                    }
                }
            }
        }

        stateHelpers.value.bumpVersionTx(user)
        stateHelpers.value.readStateTx(user)
    }
}
