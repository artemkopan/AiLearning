package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.error.DomainError
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.UserId

internal fun parseUserIdOrError(value: String): Result<UserId> {
    val trimmed = value.trim()
    return if (trimmed.isNotEmpty()) {
        Result.success(UserId(trimmed))
    } else {
        Result.failure(DomainError.Validation("User scope must not be blank"))
    }
}

internal fun parseAgentIdOrError(value: String): Result<AgentId> {
    val trimmed = value.trim()
    return if (trimmed.isNotEmpty()) {
        Result.success(AgentId(trimmed))
    } else {
        Result.failure(DomainError.Validation("Agent ID must not be blank"))
    }
}
