package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.backend.agent.persistence.operation.GetUserProfileOperation
import io.artemkopan.ai.backend.agent.persistence.operation.UpsertUserProfileOperation
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.model.UserProfile
import io.artemkopan.ai.core.domain.repository.UserProfileRepository
import org.koin.core.annotation.Single

@Single(binds = [UserProfileRepository::class])
class PostgresUserProfileRepository internal constructor(
    private val getOperation: Lazy<GetUserProfileOperation>,
    private val upsertOperation: Lazy<UpsertUserProfileOperation>,
) : UserProfileRepository {

    override suspend fun getUserProfile(userId: UserId): Result<UserProfile?> =
        getOperation.value.execute(userId = lazyArg { userId })

    override suspend fun upsertUserProfile(profile: UserProfile): Result<Unit> =
        upsertOperation.value.execute(
            userId = lazyArg { profile.userId },
            profile = lazyArg { profile },
        )

    private fun <T> lazyArg(valueProvider: () -> T): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE) { valueProvider() }
}
