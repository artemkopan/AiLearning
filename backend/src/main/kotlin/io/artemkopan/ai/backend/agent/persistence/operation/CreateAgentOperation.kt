package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.*
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

internal class CreateAgentOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
    private val config: Lazy<AppConfig>,
) {
    suspend fun execute(userId: Lazy<UserId>): Result<AgentState> = runtime.value.runDb {
        val user = userId.value
        val meta = stateHelpers.value.loadMetaTx(user)
        val counter = meta[ScopedAgentStateTable.nextAgentCounter]
        val newId = "agent-$counter"
        val position = ScopedAgentsTable.selectAll()
            .where { ScopedAgentsTable.userId eq user.value }
            .map { it[ScopedAgentsTable.position] }
            .maxOrNull()
            ?.plus(1)
            ?: 0
        val now = runtime.value.nowMillis()

        ScopedAgentsTable.insert { row ->
            row[ScopedAgentsTable.userId] = user.value
            row[id] = newId
            row[title] = "Agent $counter"
            row[model] = ""
            row[maxOutputTokens] = ""
            row[temperature] = ""
            row[stopSequences] = ""
            row[agentMode] = "default"
            row[contextStrategy] = CONTEXT_STRATEGY_ROLLING_SUMMARY
            row[contextRecentMessagesN] = config.value.contextRecentMaxMessages
            row[contextSummarizeEveryK] = config.value.contextSummarizeEveryMessages
            row[status] = STATUS_DONE
            row[ScopedAgentsTable.position] = position
            row[createdAt] = now
            row[updatedAt] = now
        }

        ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq user.value }) { row ->
            row[activeAgentId] = newId
            row[nextAgentCounter] = counter + 1
            row[version] = meta[ScopedAgentStateTable.version] + 1
            row[updatedAt] = now
        }

        stateHelpers.value.readStateTx(user)
    }
}
