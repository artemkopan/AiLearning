package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.repository.TaskRepository

class GetActiveTaskUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(userScope: String, agentId: String): Result<AgentTask?> {
        val userId = parseUserIdOrError(userScope).getOrElse { return Result.failure(it) }
        val aid = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        return repository.getActiveTask(userId, aid)
    }
}
