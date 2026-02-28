package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.UserId

interface ContextPreparationStrategy {
    suspend fun prepare(userId: UserId, agent: Agent): Result<PreparedContextWindow>
}
