package io.artemkopan.ai.core.data.error

sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkError(message: String, cause: Throwable? = null) : DataError(message, cause)
    class AuthenticationError(message: String, cause: Throwable? = null) : DataError(message, cause)
    class RateLimitError(message: String, cause: Throwable? = null) : DataError(message, cause)
    class EmptyResponseError(message: String) : DataError(message)
}
