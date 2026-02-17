package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError

class MapFailureToUserMessageUseCase {
    fun execute(throwable: Throwable): Result<String> {
        val message = when (throwable) {
            is AppError.Validation -> throwable.message ?: "Your input is invalid."
            is AppError.RateLimited -> "Too many requests. Please wait and retry."
            is AppError.UpstreamUnavailable -> "Service is temporarily unavailable. Please try again soon."
            else -> "Something went wrong. Please try again."
        }
        return Result.success(message)
    }
}
