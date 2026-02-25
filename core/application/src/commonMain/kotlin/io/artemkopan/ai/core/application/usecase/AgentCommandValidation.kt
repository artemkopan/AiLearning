package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.UserId

internal fun parseAgentIdOrError(raw: String): Result<AgentId> {
    val value = raw.trim()
    if (value.isEmpty()) {
        return Result.failure(AppError.Validation("Agent id must not be blank."))
    }
    return Result.success(AgentId(value))
}

internal fun parseMessageIdOrError(raw: String): Result<AgentMessageId> {
    val value = raw.trim()
    if (value.isEmpty()) {
        return Result.failure(AppError.Validation("Message id must not be blank."))
    }
    return Result.success(AgentMessageId(value))
}

internal fun parseUserIdOrError(raw: String): Result<UserId> {
    val value = raw.trim()
    if (value.isEmpty()) {
        return Result.failure(AppError.Validation("User id must not be blank."))
    }
    return Result.success(UserId(value))
}
