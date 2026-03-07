package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.application.usecase.parseAgentIdOrError
import io.artemkopan.ai.core.application.usecase.parseUserIdOrError
import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.repository.TaskRepository

class GetActiveTaskUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(userId: String, agentId: String): Result<AgentTask?> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val domainAgentId = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        return repository.getActiveTask(domainUserId, domainAgentId)
    }
}
