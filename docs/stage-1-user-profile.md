# Stage 1: User Profile Personalization

## Goal
Create a per-user profile system (communication style, format, restrictions) that gets injected into every LLM request as part of the LONG-TERM memory layer.

## Result
A personalized agent tailored to each user. Different profiles produce different assistant behavior automatically.

---

## Step 1: Domain Models

**Create** `core/domain/src/commonMain/kotlin/io/artemkopan/ai/core/domain/model/UserProfileModels.kt`

```kotlin
package io.artemkopan.ai.core.domain.model

data class UserProfile(
    val userId: UserId,
    val communicationStyle: CommunicationStyle,
    val responseFormat: ResponseFormat,
    val restrictions: List<String>,       // e.g. "no code in Python", "avoid jargon"
    val customInstructions: String,       // free-form custom instructions
    val updatedAt: Long,
)

enum class CommunicationStyle {
    CONCISE,
    DETAILED,
    SOCRATIC,
    CASUAL,
    FORMAL,
}

enum class ResponseFormat {
    PLAIN_TEXT,
    MARKDOWN,
    STRUCTURED,    // bullet-points, numbered lists
    CODE_FOCUSED,
}
```

**Why separate from AgentFacts?** AgentFacts is per-agent (extracted from conversation). UserProfile is per-user — it applies across ALL agents and is explicitly set by the user, not auto-extracted.

---

## Step 2: Repository Interface

**Create** `core/domain/src/commonMain/kotlin/io/artemkopan/ai/core/domain/repository/UserProfileRepository.kt`

```kotlin
package io.artemkopan.ai.core.domain.repository

import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.model.UserProfile

interface UserProfileRepository {
    suspend fun getUserProfile(userId: UserId): Result<UserProfile?>
    suspend fun upsertUserProfile(profile: UserProfile): Result<Unit>
}
```

---

## Step 3: Database Schema

**Modify** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/helper/PostgresSchema.kt`

Add after `ScopedAgentBranchesTable`:

```kotlin
internal object ScopedUserProfileTable : Table("scoped_user_profile") {
    val userId = varchar("user_id", 128)
    val communicationStyle = varchar("communication_style", 32).default("concise")
    val responseFormat = varchar("response_format", 32).default("markdown")
    val restrictions = text("restrictions").default("[]")       // JSON array of strings
    val customInstructions = text("custom_instructions").default("")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId)
}
```

**Modify** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/helper/PostgresDbRuntime.kt`

Add `ScopedUserProfileTable` to the `SchemaUtils.createMissingTablesAndColumns(...)` call.

---

## Step 4: Database Operations

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/operation/GetUserProfileOperation.kt`

Follow the pattern of `GetAgentFactsOperation`:

```kotlin
package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedUserProfileTable
import io.artemkopan.ai.core.domain.model.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.annotation.Single

@Single
internal class GetUserProfileOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(userId: UserId): Result<UserProfile?> = runtime.value.runDb {
        ScopedUserProfileTable.selectAll()
            .where { ScopedUserProfileTable.userId eq userId.value }
            .singleOrNull()
            ?.let { row ->
                UserProfile(
                    userId = userId,
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
```

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/operation/UpsertUserProfileOperation.kt`

```kotlin
package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedUserProfileTable
import io.artemkopan.ai.core.domain.model.UserProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.koin.core.annotation.Single

@Single
internal class UpsertUserProfileOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(profile: UserProfile): Result<Unit> = runtime.value.runDb {
        val now = System.currentTimeMillis()
        val exists = ScopedUserProfileTable.selectAll()
            .where { ScopedUserProfileTable.userId eq profile.userId.value }
            .count() > 0

        if (exists) {
            ScopedUserProfileTable.update(
                where = { ScopedUserProfileTable.userId eq profile.userId.value }
            ) {
                it[communicationStyle] = profile.communicationStyle.name.lowercase()
                it[responseFormat] = profile.responseFormat.name.lowercase()
                it[restrictions] = json.value.encodeToString(profile.restrictions)
                it[customInstructions] = profile.customInstructions
                it[updatedAt] = now
            }
        } else {
            ScopedUserProfileTable.insert {
                it[userId] = profile.userId.value
                it[communicationStyle] = profile.communicationStyle.name.lowercase()
                it[responseFormat] = profile.responseFormat.name.lowercase()
                it[restrictions] = json.value.encodeToString(profile.restrictions)
                it[customInstructions] = profile.customInstructions
                it[updatedAt] = now
            }
        }
    }
}
```

---

## Step 5: Repository Implementation

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/PostgresUserProfileRepository.kt`

```kotlin
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
        getOperation.value.execute(userId)

    override suspend fun upsertUserProfile(profile: UserProfile): Result<Unit> =
        upsertOperation.value.execute(profile)
}
```

---

## Step 6: Application Use Cases

**Create** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/model/UserProfileCommands.kt`

```kotlin
package io.artemkopan.ai.core.application.model

import io.artemkopan.ai.core.domain.model.CommunicationStyle
import io.artemkopan.ai.core.domain.model.ResponseFormat

data class UpdateUserProfileCommand(
    val communicationStyle: CommunicationStyle,
    val responseFormat: ResponseFormat,
    val restrictions: List<String>,
    val customInstructions: String,
)
```

**Create** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/GetUserProfileUseCase.kt`

```kotlin
package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.UserId
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
```

**Create** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/UpdateUserProfileUseCase.kt`

```kotlin
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
            updatedAt = 0L, // DB sets actual timestamp
        )
        return repository.upsertUserProfile(profile)
    }
}
```

**Create** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/BuildUserProfilePromptSnippetUseCase.kt`

```kotlin
package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.UserProfile

class BuildUserProfilePromptSnippetUseCase {
    fun execute(profile: UserProfile?): String {
        if (profile == null) return ""
        return buildString {
            appendLine("Communication style: ${profile.communicationStyle.name.lowercase().replace('_', ' ')}")
            appendLine("Response format: ${profile.responseFormat.name.lowercase().replace('_', ' ')}")
            if (profile.restrictions.isNotEmpty()) {
                appendLine("Restrictions: ${profile.restrictions.joinToString("; ")}")
            }
            if (profile.customInstructions.isNotBlank()) {
                appendLine("Custom instructions: ${profile.customInstructions}")
            }
        }
    }
}
```

---

## Step 7: Modify AssistantMemoryModel

**Modify** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/model/AssistantMemoryModel.kt`

Add field to `LongTermMemoryLayer`:

```kotlin
data class LongTermMemoryLayer(
    val profileAndDecisions: String,
    val userProfileSnippet: String = "",    // <-- ADD THIS
    val retrievedKnowledge: List<RetrievedContextChunk>,
)
```

---

## Step 8: Modify BuildContextPromptUseCase

**Modify** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/BuildContextPromptUseCase.kt`

In the `execute(memory: AssistantMemoryModel)` method, add after the `longTermProfile` block (after line 60):

```kotlin
val userProfileText = memory.longTerm.userProfileSnippet.trim()
if (userProfileText.isNotBlank()) {
    appendLine()
    appendLine("LONG-TERM MEMORY (USER PROFILE PREFERENCES):")
    appendLine(userProfileText)
}
```

Also update the empty-check on line 40 to include the new field:

```kotlin
if (workingSummary.isBlank() && shortTerm.isEmpty() && longTermProfile.isBlank()
    && retrievedMemory.isEmpty() && memory.longTerm.userProfileSnippet.isBlank()) return ""
```

---

## Step 9: Modify StartAgentMessageUseCase

**Modify** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/StartAgentMessageUseCase.kt`

Add constructor parameters:

```kotlin
class StartAgentMessageUseCase(
    private val repository: AgentRepository,
    private val prepareAgentContextUseCase: PrepareAgentContextUseCase,
    private val buildContextPromptUseCase: BuildContextPromptUseCase,
    private val retrieveRelevantContextUseCase: RetrieveRelevantContextUseCase,
    private val indexMessageEmbeddingsUseCase: IndexMessageEmbeddingsUseCase,
    private val expandStatsShortcutsInPromptUseCase: ExpandStatsShortcutsInPromptUseCase,
    private val extractAndPersistFactsUseCase: ExtractAndPersistFactsUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,                        // <-- ADD
    private val buildUserProfilePromptSnippetUseCase: BuildUserProfilePromptSnippetUseCase, // <-- ADD
)
```

In `execute()`, before building `AssistantMemoryModel` (before line 124), add:

```kotlin
val userProfile = getUserProfileUseCase.execute(userId).getOrNull()
val userProfileSnippet = buildUserProfilePromptSnippetUseCase.execute(userProfile)
```

Update the `LongTermMemoryLayer` construction (line 132-134):

```kotlin
longTerm = LongTermMemoryLayer(
    profileAndDecisions = agentFacts?.factsJson.orEmpty(),
    userProfileSnippet = userProfileSnippet,
    retrievedKnowledge = retrievedMemory,
),
```

---

## Step 10: WebSocket Contract DTOs

**Modify** `shared-contract/src/commonMain/kotlin/io/artemkopan/ai/sharedcontract/AgentWsContracts.kt`

Add client message:

```kotlin
@Serializable
@SerialName("update_user_profile")
data class UpdateUserProfileCommandDto(
    val communicationStyle: String,   // "concise", "detailed", "socratic", "casual", "formal"
    val responseFormat: String,       // "plain_text", "markdown", "structured", "code_focused"
    val restrictions: List<String> = emptyList(),
    val customInstructions: String = "",
    val requestId: String? = null,
) : AgentWsClientMessageDto
```

Add server message:

```kotlin
@Serializable
@SerialName("user_profile_snapshot")
data class UserProfileSnapshotDto(
    val communicationStyle: String,
    val responseFormat: String,
    val restrictions: List<String>,
    val customInstructions: String,
) : AgentWsServerMessageDto
```

---

## Step 11: WebSocket Handler

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/ws/usecase/UpdateUserProfileWsUseCase.kt`

```kotlin
package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.model.UpdateUserProfileCommand
import io.artemkopan.ai.core.application.usecase.GetUserProfileUseCase
import io.artemkopan.ai.core.application.usecase.UpdateUserProfileUseCase
import io.artemkopan.ai.core.domain.model.CommunicationStyle
import io.artemkopan.ai.core.domain.model.ResponseFormat
import io.artemkopan.ai.sharedcontract.UpdateUserProfileCommandDto
import io.artemkopan.ai.sharedcontract.UserProfileSnapshotDto
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class UpdateUserProfileWsUseCase(
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<UpdateUserProfileCommandDto> {

    override val messageType: KClass<UpdateUserProfileCommandDto> = UpdateUserProfileCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: UpdateUserProfileCommandDto,
    ): Result<Unit> {
        val command = UpdateUserProfileCommand(
            communicationStyle = CommunicationStyle.valueOf(message.communicationStyle.uppercase()),
            responseFormat = ResponseFormat.valueOf(message.responseFormat.uppercase()),
            restrictions = message.restrictions,
            customInstructions = message.customInstructions,
        )
        updateUserProfileUseCase.execute(context.userScope, command)
            .getOrElse { return Result.failure(it) }

        // Send updated profile back to client
        val profile = getUserProfileUseCase.execute(context.userScope).getOrNull()
        if (profile != null) {
            outboundService.sendToSession(
                context.session,
                UserProfileSnapshotDto(
                    communicationStyle = profile.communicationStyle.name.lowercase(),
                    responseFormat = profile.responseFormat.name.lowercase(),
                    restrictions = profile.restrictions,
                    customInstructions = profile.customInstructions,
                ),
            )
        }
        return Result.success(Unit)
    }
}
```

---

## Step 12: Update requestIdOrNull

**Modify** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/ws/AgentWsMessageHandler.kt`

Add branch in `requestIdOrNull()` (line 100-113):

```kotlin
is UpdateUserProfileCommandDto -> requestId
```

---

## Step 13: DI Registration

**Modify** `backend/src/main/kotlin/io/artemkopan/ai/backend/di/KoinModules.kt`

In `applicationModule`, add:

```kotlin
factory { GetUserProfileUseCase(repository = get()) }
factory { UpdateUserProfileUseCase(repository = get()) }
factory { BuildUserProfilePromptSnippetUseCase() }
```

Update `StartAgentMessageUseCase` factory (lines 200-209):

```kotlin
factory {
    StartAgentMessageUseCase(
        repository = get(),
        prepareAgentContextUseCase = get(),
        buildContextPromptUseCase = get(),
        retrieveRelevantContextUseCase = get(),
        indexMessageEmbeddingsUseCase = get(),
        expandStatsShortcutsInPromptUseCase = get(),
        extractAndPersistFactsUseCase = get(),
        getUserProfileUseCase = get(),                        // <-- ADD
        buildUserProfilePromptSnippetUseCase = get(),          // <-- ADD
    )
}
```

---

## Verification Checklist

- [ ] `./gradlew build` compiles all modules (JVM + JS)
- [ ] Start backend, verify `scoped_user_profile` table created
- [ ] Send `update_user_profile` WS message with style=detailed, format=markdown
- [ ] Receive `user_profile_snapshot` response with saved values
- [ ] Send a chat message — check server logs for prompt containing "USER PROFILE PREFERENCES"
- [ ] Change profile to style=concise — verify different response behavior
- [ ] Agent without profile set — verify no error, empty snippet skipped
- [ ] Existing 3-arg `BuildContextPromptUseCase.execute()` still works (backward compat)
