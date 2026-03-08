package io.artemkopan.ai.core.application.error

sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Validation(message: String) : AppError(message)
    class RateLimited(message: String, cause: Throwable? = null) : AppError(message, cause)
    class UpstreamUnavailable(message: String, cause: Throwable? = null) : AppError(message, cause)
}
