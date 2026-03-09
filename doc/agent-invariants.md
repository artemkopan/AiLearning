# Agent Invariants

## Overview

Add a per-agent **invariants** mechanism -- a list of rules the assistant is not allowed to violate.
Invariants are stored separately from the conversation, injected into every LLM system prompt,
and cause the assistant to refuse requests that conflict with them.

Examples of invariants:
- Selected architecture ("Follow Clean Architecture with domain/application/data layers")
- Technical decisions ("Use only Kotlin coroutines, no RxJava")
- Stack constraints ("Backend must be Spring Boot + PostgreSQL only")
- Business rules ("Never suggest deleting user data without confirmation")

---

## Data Flow

```
User edits invariants in ConfigPanel
        |
        v
WS: update_agent_invariants command
        |
        v
UpdateAgentInvariantsResolver (backend)
        |
        v
PostgresAgentRepository.updateAgentInvariants()
        |
        v
scoped_agents.invariants column (JSON array)
        |
        v
Agent domain model loaded with invariants
        |
        v
buildInvariantsBlock() appended to every system prompt
        |
        v
DeepSeek API receives system message with invariants
```

---

## Changes by Layer

### 1. Domain Layer (`core/domain`)

**File:** `core/domain/src/commonMain/kotlin/io/artemkopan/ai/core/domain/model/AgentModels.kt`

Add `invariants` field to `Agent`:

```kotlin
data class Agent(
    // ... existing fields ...
    val invariants: List<String> = emptyList(),
)
```

Add `invariants` field to `AgentDraft`:

```kotlin
data class AgentDraft(
    // ... existing fields ...
    val invariants: List<String> = emptyList(),
)
```

---

### 2. Persistence Layer (`backend`)

**File:** `backend/.../persistence/PostgresSchema.kt`

Add column to `ScopedAgentsTable`:

```kotlin
val invariants = text("invariants").default("")
```

Stored as a JSON array string: `["rule 1","rule 2"]`. Empty string or `"[]"` means no invariants.

**File:** `backend/.../persistence/PostgresMapping.kt`

Parse invariants in `ResultRow.toAgent()`:

```kotlin
invariants = runCatching {
    Json.decodeFromString<List<String>>(this[ScopedAgentsTable.invariants])
}.getOrDefault(emptyList()),
```

**File:** `backend/.../persistence/PostgresAgentRepository.kt`

Write invariants in `createAgent`:

```kotlin
it[ScopedAgentsTable.invariants] = "[]"
```

Write invariants in `updateAgentDraft`:

```kotlin
it[ScopedAgentsTable.invariants] = Json.encodeToString(draft.invariants)
```

Add dedicated `updateAgentInvariants` method:

```kotlin
suspend fun updateAgentInvariants(
    userId: UserId, agentId: AgentId, invariants: List<String>
): Result<AgentState>
```

**File:** `core/domain/.../repository/AgentRepository.kt`

Add interface method:

```kotlin
suspend fun updateAgentInvariants(
    userId: UserId, agentId: AgentId, invariants: List<String>
): Result<AgentState>
```

---

### 3. Shared Contract (`shared-contract`)

**File:** `shared-contract/.../AgentWsContracts.kt`

New WS command:

```kotlin
@Serializable
@SerialName("update_agent_invariants")
data class UpdateAgentInvariantsCommandDto(
    val agentId: String,
    val invariants: List<String>,
    val requestId: String? = null,
) : AgentWsClientMessageDto
```

**File:** `shared-contract/.../ApiContracts.kt`

Add to `AgentDto`:

```kotlin
data class AgentDto(
    // ... existing fields ...
    val invariants: List<String> = emptyList(),
)
```

---

### 4. Application Layer (`core/application`)

**New file:** `core/application/.../usecase/InvariantsPromptBuilder.kt`

```kotlin
object InvariantsPromptBuilder {

    fun buildInvariantsBlock(invariants: List<String>): String {
        if (invariants.isEmpty()) return ""
        val rules = invariants.mapIndexed { i, rule ->
            "${i + 1}. $rule"
        }.joinToString("\n")
        return """

INVARIANTS (mandatory constraints you MUST follow):
$rules

IMPORTANT: If a user request conflicts with any invariant above, you MUST:
1. Refuse to execute the conflicting part
2. Clearly explain which invariant would be violated and why
3. Suggest an alternative approach that respects all invariants"""
    }
}
```

**File:** `core/application/.../usecase/StartAgentMessageUseCase.kt`

Append invariants block to `PLANNING_SYSTEM_PROMPT`:

```kotlin
systemInstruction = PLANNING_SYSTEM_PROMPT +
    InvariantsPromptBuilder.buildInvariantsBlock(agent.invariants),
```

---

### 5. Backend Resolvers

**File:** `backend/.../resolver/AcceptPlanResolver.kt`

Append invariants to execution and validation prompts:

```kotlin
systemInstruction = EXECUTION_SYSTEM_PROMPT +
    InvariantsPromptBuilder.buildInvariantsBlock(agent.invariants),
```

```kotlin
systemInstruction = VALIDATION_SYSTEM_PROMPT +
    InvariantsPromptBuilder.buildInvariantsBlock(agent.invariants),
```

**File:** `backend/.../resolver/RejectPlanResolver.kt`

Append invariants to revision prompt:

```kotlin
systemInstruction = REVISION_SYSTEM_PROMPT +
    InvariantsPromptBuilder.buildInvariantsBlock(agent.invariants),
```

**New file:** `backend/.../resolver/UpdateAgentInvariantsResolver.kt`

```kotlin
@Factory(binds = [AgentWsMessageResolver::class])
class UpdateAgentInvariantsResolver(
    private val agentRepository: AgentRepository,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageResolver<UpdateAgentInvariantsCommandDto> {
    override val messageType = UpdateAgentInvariantsCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: UpdateAgentInvariantsCommandDto,
    ): Result<Unit> {
        val userId = parseUserIdOrError(context.userScope)
            .getOrElse { return Result.failure(it) }
        val agentId = AgentId(message.agentId)
        val state = agentRepository.updateAgentInvariants(
            userId, agentId, message.invariants,
        ).getOrElse { return Result.failure(it) }
        outboundService.broadcastSnapshot(context.userScope, state)
        return Result.success(Unit)
    }
}
```

Koin startup validation automatically picks up the new resolver via `@Factory(binds = ...)`.

---

### 6. Backend WS Mapper

**File:** `backend/.../AgentWsMapper.kt`

Map `Agent.invariants` to `AgentDto.invariants` in snapshot conversion:

```kotlin
invariants = agent.invariants,
```

---

### 7. Frontend (`shared-ui`)

**File:** `shared-ui/.../core/session/SessionModels.kt`

Add to `AgentState`:

```kotlin
data class AgentState(
    // ... existing fields ...
    val invariants: List<String> = emptyList(),
)
```

**File:** `shared-ui/.../usecase/MapSnapshotToUiStateUseCase.kt`

Map invariants from `AgentDto`:

```kotlin
invariants = dto.invariants,
```

**File:** `shared-ui/.../configpanel/view/ConfigPanel.kt`

Add INVARIANTS section below existing config fields:

```kotlin
CyberpunkPanel(title = "[ INVARIANTS ]", accentColor = CyberpunkColors.Red) {
    // Text area where each line is one invariant
    CyberpunkTextField(
        value = invariantsText,
        onValueChange = onInvariantsChanged,
        label = "RULES (ONE PER LINE)",
        singleLine = false,
        minLines = 3,
    )
}
```

**File:** `shared-ui/.../configpanel/model/ConfigPanelUiModel.kt`

Add field:

```kotlin
val invariants: List<String> = emptyList(),
```

**New file:** `shared-ui/.../usecase/UpdateInvariantsActionUseCase.kt`

Sends the `UpdateAgentInvariantsCommandDto` via WS gateway and updates local state.

---

## Conflict Behavior

When a user request contradicts an invariant:

1. **Planning phase** -- the LLM sees invariants in the system prompt and produces a plan
   that flags the conflict. The plan may include a `question_for_user` explaining which
   invariant would be violated.

2. **Execution phase** -- if execution somehow proceeds, invariants are still in the
   execution system prompt, so the LLM will refuse or flag the violation in its output.

3. **Validation phase** -- the validator sees invariants and can mark the result as FAIL
   if it detects a violation.

4. **Revision phase** -- after rejection, invariants remain in the revision prompt,
   ensuring the revised plan also respects them.

### Example: conflict scenario

User invariant: `"Use only REST APIs, no GraphQL"`

User message: `"Add a GraphQL endpoint for user queries"`

Expected assistant response (planning phase):
```json
{
  "plan": [
    "I cannot add a GraphQL endpoint because it violates invariant #1: 'Use only REST APIs, no GraphQL'",
    "Instead, I suggest creating a REST endpoint GET /api/v1/users with query parameters for filtering"
  ],
  "question_for_user": "Your request conflicts with invariant 'Use only REST APIs, no GraphQL'. Would you like me to implement this as a REST endpoint instead, or would you like to update the invariant?"
}
```

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| Per-agent, not global | Different agents can have different project constraints |
| JSON array in single text column | Simple storage, no extra table, easy to read/write |
| Injected into system message | System role has highest priority for LLM instruction following |
| Same block across all phases | Consistent enforcement regardless of pipeline stage |
| Empty list = no constraints | Fully backward compatible, no behavior change for existing agents |
| Explicit refusal instructions | LLM needs clear instructions on how to handle conflicts |
| Red accent color in UI | Signals importance/constraint nature in the cyberpunk theme |

---

## Files Modified (summary)

| File | Change |
|------|--------|
| `core/domain/.../AgentModels.kt` | Add `invariants` to `Agent` and `AgentDraft` |
| `core/domain/.../AgentRepository.kt` | Add `updateAgentInvariants` method |
| `core/application/.../InvariantsPromptBuilder.kt` | **New** -- shared prompt builder |
| `core/application/.../StartAgentMessageUseCase.kt` | Append invariants to planning prompt |
| `shared-contract/.../AgentWsContracts.kt` | Add `UpdateAgentInvariantsCommandDto` |
| `shared-contract/.../ApiContracts.kt` | Add `invariants` to `AgentDto` |
| `backend/.../PostgresSchema.kt` | Add `invariants` column |
| `backend/.../PostgresMapping.kt` | Parse invariants JSON |
| `backend/.../PostgresAgentRepository.kt` | Write invariants, add `updateAgentInvariants` |
| `backend/.../AcceptPlanResolver.kt` | Inject invariants into execution/validation prompts |
| `backend/.../RejectPlanResolver.kt` | Inject invariants into revision prompt |
| `backend/.../UpdateAgentInvariantsResolver.kt` | **New** -- WS command handler |
| `backend/.../AgentWsMapper.kt` | Map invariants to DTO |
| `shared-ui/.../SessionModels.kt` | Add `invariants` to `AgentState` |
| `shared-ui/.../MapSnapshotToUiStateUseCase.kt` | Map invariants from snapshot |
| `shared-ui/.../ConfigPanel.kt` | Add INVARIANTS UI section |
| `shared-ui/.../ConfigPanelUiModel.kt` | Add `invariants` field |
| `shared-ui/.../UpdateInvariantsActionUseCase.kt` | **New** -- send WS command |
