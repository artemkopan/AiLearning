package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentTasksTable
import io.artemkopan.ai.backend.agent.persistence.helper.toJson
import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.model.UserId
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.koin.core.annotation.Single

@Single
internal class UpsertTaskOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(userId: Lazy<UserId>, task: Lazy<AgentTask>): Result<Unit> = runtime.value.runDb {
        val user = userId.value
        val t = task.value
        val now = runtime.value.nowMillis()

        val exists = ScopedAgentTasksTable.selectAll()
            .where {
                (ScopedAgentTasksTable.userId eq user.value) and
                    (ScopedAgentTasksTable.taskId eq t.id.value)
            }
            .count() > 0

        if (exists) {
            ScopedAgentTasksTable.update(
                where = {
                    (ScopedAgentTasksTable.userId eq user.value) and
                        (ScopedAgentTasksTable.taskId eq t.id.value)
                }
            ) {
                it[title] = t.title
                it[currentPhase] = t.currentPhase.name.lowercase()
                it[currentStepIndex] = t.currentStepIndex
                it[stepsJson] = t.steps.toJson(json.value)
                it[planJson] = t.planJson
                it[validationJson] = t.validationJson
                it[updatedAt] = now
            }
        } else {
            ScopedAgentTasksTable.insert {
                it[this.userId] = user.value
                it[taskId] = t.id.value
                it[agentId] = t.agentId.value
                it[title] = t.title
                it[currentPhase] = t.currentPhase.name.lowercase()
                it[currentStepIndex] = t.currentStepIndex
                it[stepsJson] = t.steps.toJson(json.value)
                it[planJson] = t.planJson
                it[validationJson] = t.validationJson
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }
}
