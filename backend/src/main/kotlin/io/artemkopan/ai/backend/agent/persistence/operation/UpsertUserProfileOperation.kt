package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedUserProfileTable
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.model.UserProfile
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.koin.core.annotation.Single

@Single
internal class UpsertUserProfileOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(userId: Lazy<UserId>, profile: Lazy<UserProfile>): Result<Unit> = runtime.value.runDb {
        val user = userId.value
        val p = profile.value
        val now = runtime.value.nowMillis()

        val existing = ScopedUserProfileTable.selectAll()
            .where { ScopedUserProfileTable.userId eq user.value }
            .singleOrNull()

        if (existing == null) {
            ScopedUserProfileTable.insert { row ->
                row[ScopedUserProfileTable.userId] = user.value
                row[communicationStyle] = p.communicationStyle.name.lowercase()
                row[responseFormat] = p.responseFormat.name.lowercase()
                row[restrictions] = json.value.encodeToString(p.restrictions)
                row[customInstructions] = p.customInstructions
                row[updatedAt] = now
            }
        } else {
            ScopedUserProfileTable.update(
                where = { ScopedUserProfileTable.userId eq user.value }
            ) { row ->
                row[communicationStyle] = p.communicationStyle.name.lowercase()
                row[responseFormat] = p.responseFormat.name.lowercase()
                row[restrictions] = json.value.encodeToString(p.restrictions)
                row[customInstructions] = p.customInstructions
                row[updatedAt] = now
            }
        }
    }
}
