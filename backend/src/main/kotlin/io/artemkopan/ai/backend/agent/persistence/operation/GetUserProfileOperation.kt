package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedUserProfileTable
import io.artemkopan.ai.core.domain.model.CommunicationStyle
import io.artemkopan.ai.core.domain.model.ResponseFormat
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.model.UserProfile
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.annotation.Single

@Single
internal class GetUserProfileOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(userId: Lazy<UserId>): Result<UserProfile?> = runtime.value.runDb {
        ScopedUserProfileTable.selectAll()
            .where { ScopedUserProfileTable.userId eq userId.value.value }
            .singleOrNull()
            ?.let { row ->
                UserProfile(
                    userId = userId.value,
                    communicationStyle = CommunicationStyle.valueOf(
                        row[ScopedUserProfileTable.communicationStyle].uppercase()
                    ),
                    responseFormat = ResponseFormat.valueOf(
                        row[ScopedUserProfileTable.responseFormat].uppercase()
                    ),
                    restrictions = json.value.decodeFromString<List<String>>(
                        row[ScopedUserProfileTable.restrictions]
                    ),
                    customInstructions = row[ScopedUserProfileTable.customInstructions],
                    updatedAt = row[ScopedUserProfileTable.updatedAt],
                )
            }
    }
}
