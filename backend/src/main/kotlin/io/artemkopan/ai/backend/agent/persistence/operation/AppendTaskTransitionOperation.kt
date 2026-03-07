package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentTaskTransitionsTable
import io.artemkopan.ai.core.domain.model.TaskPhaseTransition
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.insert
import org.koin.core.annotation.Single

@Single
internal class AppendTaskTransitionOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
) {
    suspend fun execute(userId: Lazy<UserId>, transition: Lazy<TaskPhaseTransition>): Result<Unit> =
        runtime.value.runDb {
            val t = transition.value
            ScopedAgentTaskTransitionsTable.insert {
                it[this.userId] = userId.value.value
                it[taskId] = t.taskId.value
                it[fromPhase] = t.fromPhase.name.lowercase()
                it[toPhase] = t.toPhase.name.lowercase()
                it[reason] = t.reason
                it[timestamp] = t.timestamp
            }
        }
}
