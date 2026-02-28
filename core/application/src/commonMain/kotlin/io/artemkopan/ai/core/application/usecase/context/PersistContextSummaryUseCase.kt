package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.AgentContextMemory
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository

class PersistContextSummaryUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(
        userId: UserId,
        agentId: AgentId,
        summaryText: String,
        summarizedUntilCreatedAt: Long,
    ): Result<Unit> {
        return repository.upsertContextMemory(
            userId = userId,
            memory = AgentContextMemory(
                agentId = agentId,
                summaryText = summaryText,
                summarizedUntilCreatedAt = summarizedUntilCreatedAt,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
