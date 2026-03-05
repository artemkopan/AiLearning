package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.model.UpdateUserProfileCommand
import io.artemkopan.ai.core.domain.model.UserProfile
import io.artemkopan.ai.core.domain.repository.UserProfileRepository

class UpdateUserProfileUseCase(
    private val repository: UserProfileRepository,
) {
    suspend fun execute(userId: String, command: UpdateUserProfileCommand): Result<Unit> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val profile = UserProfile(
            userId = domainUserId,
            communicationStyle = command.communicationStyle,
            responseFormat = command.responseFormat,
            restrictions = command.restrictions,
            customInstructions = command.customInstructions,
            updatedAt = 0L,
        )
        return repository.upsertUserProfile(profile)
    }
}
