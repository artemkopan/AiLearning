package io.artemkopan.ai.core.application.mapper

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.domain.error.DomainError

class DomainErrorMapper {
    fun mapToAppError(throwable: Throwable): AppError = when (throwable) {
        is DomainError.RateLimited -> AppError.RateLimited(
            throwable.message ?: "Rate limited",
            throwable,
        )
        is DomainError.ProviderUnavailable -> AppError.UpstreamUnavailable(
            throwable.message ?: "Provider unavailable",
            throwable,
        )
        is AppError -> throwable
        else -> AppError.Unexpected("Unexpected generation failure.", throwable)
    }
}
