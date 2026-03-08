package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.error.DomainError

class MapFailureToUserMessageUseCase {
    fun execute(throwable: Throwable): String {
        return when (throwable) {
            is DomainError.Validation -> throwable.message ?: "Validation error"
            is DomainError.RateLimited -> "Rate limited. Please try again later."
            is DomainError.ProviderUnavailable -> throwable.message ?: "Service temporarily unavailable"
            is DomainError.Unexpected -> throwable.message ?: "An unexpected error occurred"
            else -> throwable.message ?: "An error occurred"
        }
    }
}
