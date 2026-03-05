package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.UserProfile
import io.artemkopan.ai.core.domain.repository.UserProfileRepository

class GetUserProfileUseCase(
    private val repository: UserProfileRepository,
) {
    suspend fun execute(userId: String): Result<UserProfile?> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        return repository.getUserProfile(domainUserId)
    }
}
