package io.artemkopan.ai.core.domain.repository

import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.model.UserProfile

interface UserProfileRepository {
    suspend fun getUserProfile(userId: UserId): Result<UserProfile?>
    suspend fun upsertUserProfile(profile: UserProfile): Result<Unit>
}
