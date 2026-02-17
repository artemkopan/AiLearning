package io.artemkopan.ai.core.domain.error

sealed class DomainError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Validation(message: String) : DomainError(message)
    class ProviderUnavailable(message: String, cause: Throwable? = null) : DomainError(message, cause)
    class RateLimited(message: String, cause: Throwable? = null) : DomainError(message, cause)
    class Unexpected(message: String, cause: Throwable? = null) : DomainError(message, cause)
}
