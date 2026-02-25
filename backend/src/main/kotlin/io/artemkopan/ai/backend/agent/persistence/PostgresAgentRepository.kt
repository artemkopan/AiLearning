package io.artemkopan.ai.backend.agent.persistence

import co.touchlab.kermit.Logger
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.AgentContextMemory
import io.artemkopan.ai.core.domain.model.AgentDraft
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.model.AgentUsage
import io.artemkopan.ai.core.domain.model.RetrievedContextChunk
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.math.sqrt

class PostgresAgentRepository(
    private val config: AppConfig,
) : AgentRepository {

    private val log = Logger.withTag("PostgresAgentRepository")
    private val initLock = Any()

    @Volatile
    private var initialized = false

    override suspend fun getState(userId: UserId): Result<AgentState> = runDb {
        readStateTx(userId)
    }

    override suspend fun createAgent(userId: UserId): Result<AgentState> = runDb {
        val meta = loadMetaTx(userId)
        val counter = meta[ScopedAgentStateTable.nextAgentCounter]
        val newId = "agent-$counter"
        val position = ScopedAgentsTable.selectAll()
            .where { ScopedAgentsTable.userId eq userId.value }
            .map { it[ScopedAgentsTable.position] }
            .maxOrNull()
            ?.plus(1)
            ?: 0
        val now = nowMillis()

        ScopedAgentsTable.insert { row ->
            row[ScopedAgentsTable.userId] = userId.value
            row[id] = newId
            row[title] = "Agent $counter"
            row[model] = ""
            row[maxOutputTokens] = ""
            row[temperature] = ""
            row[stopSequences] = ""
            row[agentMode] = "default"
            row[status] = STATUS_DONE
            row[ScopedAgentsTable.position] = position
            row[createdAt] = now
            row[updatedAt] = now
        }

        ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) { row ->
            row[activeAgentId] = newId
            row[nextAgentCounter] = counter + 1
            row[version] = meta[ScopedAgentStateTable.version] + 1
            row[updatedAt] = now
        }

        readStateTx(userId)
    }

    override suspend fun selectAgent(userId: UserId, agentId: AgentId): Result<AgentState> = runDb {
        val exists = ScopedAgentsTable.selectAll()
            .where { (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value) }
            .any()
        require(exists) { "Agent not found: ${agentId.value}" }

        val meta = loadMetaTx(userId)
        val now = nowMillis()
        ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) { row ->
            row[activeAgentId] = agentId.value
            row[version] = meta[ScopedAgentStateTable.version] + 1
            row[updatedAt] = now
        }

        readStateTx(userId)
    }

    override suspend fun updateAgentDraft(
        userId: UserId,
        agentId: AgentId,
        draft: AgentDraft,
    ): Result<AgentState> = runDb {
        val exists = ScopedAgentsTable.selectAll().where {
            (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value)
        }.singleOrNull()
        require(exists != null) { "Agent not found: ${agentId.value}" }

        val now = nowMillis()
        ScopedAgentsTable.update({
            (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value)
        }) { row ->
            row[model] = draft.model
            row[maxOutputTokens] = draft.maxOutputTokens
            row[temperature] = draft.temperature
            row[stopSequences] = draft.stopSequences
            row[agentMode] = draft.agentMode.ifBlank { "default" }
            row[updatedAt] = now
        }

        bumpVersionTx(userId, now)
        readStateTx(userId)
    }

    override suspend fun closeAgent(userId: UserId, agentId: AgentId): Result<AgentState> = runDb {
        val allBefore = ScopedAgentsTable.selectAll()
            .where { ScopedAgentsTable.userId eq userId.value }
            .orderBy(ScopedAgentsTable.position, SortOrder.ASC)
            .map { it[ScopedAgentsTable.id] }
        val closedIndex = allBefore.indexOf(agentId.value)
        require(closedIndex >= 0) { "Agent not found: ${agentId.value}" }

        ScopedAgentMessagesTable.deleteWhere {
            (ScopedAgentMessagesTable.userId eq userId.value) and (ScopedAgentMessagesTable.agentId eq agentId.value)
        }
        ScopedAgentContextMemoryTable.deleteWhere {
            (ScopedAgentContextMemoryTable.userId eq userId.value) and
                (ScopedAgentContextMemoryTable.agentId eq agentId.value)
        }
        ScopedAgentMessageEmbeddingsTable.deleteWhere {
            (ScopedAgentMessageEmbeddingsTable.userId eq userId.value) and
                (ScopedAgentMessageEmbeddingsTable.agentId eq agentId.value)
        }
        ScopedAgentsTable.deleteWhere {
            (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value)
        }

        val remaining = ScopedAgentsTable.selectAll()
            .where { ScopedAgentsTable.userId eq userId.value }
            .orderBy(ScopedAgentsTable.position, SortOrder.ASC)
            .map { it[ScopedAgentsTable.id] }

        val meta = loadMetaTx(userId)
        val currentActive = meta[ScopedAgentStateTable.activeAgentId]
        val newActive = when {
            remaining.isEmpty() -> null
            currentActive == agentId.value -> {
                if (closedIndex >= remaining.size) remaining.last() else remaining[closedIndex]
            }
            currentActive != null && remaining.contains(currentActive) -> currentActive
            else -> remaining.first()
        }

        val now = nowMillis()
        ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) { row ->
            row[activeAgentId] = newActive
            row[version] = meta[ScopedAgentStateTable.version] + 1
            row[updatedAt] = now
        }

        readStateTx(userId)
    }

    override suspend fun updateAgentStatus(
        userId: UserId,
        agentId: AgentId,
        status: AgentStatus,
    ): Result<AgentState> = runDb {
        val updated = ScopedAgentsTable.update({
            (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value)
        }) { row ->
            row[ScopedAgentsTable.status] = status.value
            row[updatedAt] = nowMillis()
        }
        require(updated > 0) { "Agent not found: ${agentId.value}" }

        bumpVersionTx(userId)
        readStateTx(userId)
    }

    override suspend fun appendMessage(
        userId: UserId,
        agentId: AgentId,
        message: AgentMessage,
    ): Result<AgentState> = runDb {
        val existing = ScopedAgentsTable.selectAll().where {
            (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value)
        }.singleOrNull()
        require(existing != null) { "Agent not found: ${agentId.value}" }

        val currentMaxCreatedAt = ScopedAgentMessagesTable.selectAll()
            .where {
                (ScopedAgentMessagesTable.userId eq userId.value) and
                    (ScopedAgentMessagesTable.agentId eq agentId.value)
            }
            .map { it[ScopedAgentMessagesTable.createdAt] }
            .maxOrNull()
        val requestedCreatedAt = message.createdAt.takeIf { it > 0 } ?: nowMillis()
        val persistedCreatedAt = currentMaxCreatedAt
            ?.let { maxOf(requestedCreatedAt, it + 1) }
            ?: requestedCreatedAt

        ScopedAgentMessagesTable.insert { row ->
            row[ScopedAgentMessagesTable.userId] = userId.value
            row[id] = message.id.value
            row[ScopedAgentMessagesTable.agentId] = agentId.value
            row[role] = when (message.role) {
                AgentMessageRole.USER -> ROLE_USER
                AgentMessageRole.ASSISTANT -> ROLE_ASSISTANT
            }
            row[text] = message.text
            row[status] = message.status
            row[createdAt] = persistedCreatedAt
            row[provider] = message.provider
            row[model] = message.model
            row[usageInputTokens] = message.usage?.inputTokens
            row[usageOutputTokens] = message.usage?.outputTokens
            row[usageTotalTokens] = message.usage?.totalTokens
            row[latencyMs] = message.latencyMs
        }

        if (message.role == AgentMessageRole.USER && message.text.isNotBlank()) {
            val currentTitle = existing[ScopedAgentsTable.title]
            if (currentTitle.startsWith("Agent ")) {
                val newTitle = message.text.lineSequence().firstOrNull()?.trim().orEmpty().take(MAX_TITLE_LENGTH)
                if (newTitle.isNotBlank()) {
                    ScopedAgentsTable.update({
                        (ScopedAgentsTable.userId eq userId.value) and (ScopedAgentsTable.id eq agentId.value)
                    }) { row ->
                        row[title] = newTitle
                        row[updatedAt] = nowMillis()
                    }
                }
            }
        }

        bumpVersionTx(userId)
        readStateTx(userId)
    }

    override suspend fun updateMessage(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
        text: String?,
        status: String?,
        provider: String?,
        model: String?,
        usageInputTokens: Int?,
        usageOutputTokens: Int?,
        usageTotalTokens: Int?,
        latencyMs: Long?,
    ): Result<AgentState> = runDb {
        val updated = ScopedAgentMessagesTable.update({
            (ScopedAgentMessagesTable.userId eq userId.value) and
                (ScopedAgentMessagesTable.agentId eq agentId.value) and
                (ScopedAgentMessagesTable.id eq messageId.value)
        }) { row ->
            if (text != null) row[ScopedAgentMessagesTable.text] = text
            if (status != null) row[ScopedAgentMessagesTable.status] = status
            if (provider != null) row[ScopedAgentMessagesTable.provider] = provider
            if (model != null) row[ScopedAgentMessagesTable.model] = model
            if (usageInputTokens != null) row[ScopedAgentMessagesTable.usageInputTokens] = usageInputTokens
            if (usageOutputTokens != null) row[ScopedAgentMessagesTable.usageOutputTokens] = usageOutputTokens
            if (usageTotalTokens != null) row[ScopedAgentMessagesTable.usageTotalTokens] = usageTotalTokens
            if (latencyMs != null) row[ScopedAgentMessagesTable.latencyMs] = latencyMs
        }
        require(updated > 0) { "Message not found: ${messageId.value}" }

        bumpVersionTx(userId)
        readStateTx(userId)
    }

    override suspend fun findMessage(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
    ): Result<AgentMessage?> = runDb {
        ScopedAgentMessagesTable.selectAll().where {
            (ScopedAgentMessagesTable.userId eq userId.value) and
                (ScopedAgentMessagesTable.agentId eq agentId.value) and
                (ScopedAgentMessagesTable.id eq messageId.value)
        }.singleOrNull()?.let(::toMessage)
    }

    override suspend fun hasProcessingMessage(userId: UserId, agentId: AgentId): Result<Boolean> = runDb {
        ScopedAgentMessagesTable.selectAll().where {
            (ScopedAgentMessagesTable.userId eq userId.value) and
                (ScopedAgentMessagesTable.agentId eq agentId.value) and
                (ScopedAgentMessagesTable.status eq STATUS_PROCESSING)
        }.any()
    }

    override suspend fun getContextMemory(userId: UserId, agentId: AgentId): Result<AgentContextMemory?> = runDb {
        ScopedAgentContextMemoryTable.selectAll()
            .where {
                (ScopedAgentContextMemoryTable.userId eq userId.value) and
                    (ScopedAgentContextMemoryTable.agentId eq agentId.value)
            }
            .singleOrNull()
            ?.let(::toContextMemory)
    }

    override suspend fun upsertContextMemory(userId: UserId, memory: AgentContextMemory): Result<Unit> = runDb {
        val now = nowMillis()
        val existing = ScopedAgentContextMemoryTable.selectAll()
            .where {
                (ScopedAgentContextMemoryTable.userId eq userId.value) and
                    (ScopedAgentContextMemoryTable.agentId eq memory.agentId.value)
            }
            .singleOrNull()

        if (existing == null) {
            ScopedAgentContextMemoryTable.insert { row ->
                row[ScopedAgentContextMemoryTable.userId] = userId.value
                row[agentId] = memory.agentId.value
                row[summaryText] = memory.summaryText
                row[summarizedUntilCreatedAt] = memory.summarizedUntilCreatedAt
                row[updatedAt] = memory.updatedAt.takeIf { it > 0 } ?: now
            }
        } else {
            ScopedAgentContextMemoryTable.update({
                (ScopedAgentContextMemoryTable.userId eq userId.value) and
                    (ScopedAgentContextMemoryTable.agentId eq memory.agentId.value)
            }) { row ->
                row[summaryText] = memory.summaryText
                row[summarizedUntilCreatedAt] = memory.summarizedUntilCreatedAt
                row[updatedAt] = memory.updatedAt.takeIf { it > 0 } ?: now
            }
        }

        bumpVersionTx(userId, now)
    }

    override suspend fun listMessagesAfter(
        userId: UserId,
        agentId: AgentId,
        createdAtExclusive: Long,
    ): Result<List<AgentMessage>> = runDb {
        ScopedAgentMessagesTable.selectAll()
            .where {
                (ScopedAgentMessagesTable.userId eq userId.value) and
                    (ScopedAgentMessagesTable.agentId eq agentId.value)
            }
            .filter { it[ScopedAgentMessagesTable.createdAt] > createdAtExclusive }
            .sortedWith(
                compareBy<ResultRow>(
                    { it[ScopedAgentMessagesTable.createdAt] },
                    { it[ScopedAgentMessagesTable.id] },
                )
            )
            .map(::toMessage)
    }

    override suspend fun upsertMessageEmbedding(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
        chunkIndex: Int,
        textChunk: String,
        embedding: List<Double>,
        createdAt: Long,
    ): Result<Unit> = runDb {
        val now = nowMillis()
        val embeddingPayload = serializeEmbedding(embedding)
        val existing = ScopedAgentMessageEmbeddingsTable.selectAll().where {
            (ScopedAgentMessageEmbeddingsTable.userId eq userId.value) and
                (ScopedAgentMessageEmbeddingsTable.agentId eq agentId.value) and
                (ScopedAgentMessageEmbeddingsTable.messageId eq messageId.value) and
                (ScopedAgentMessageEmbeddingsTable.chunkIndex eq chunkIndex)
        }.singleOrNull()

        if (existing == null) {
            ScopedAgentMessageEmbeddingsTable.insert { row ->
                row[ScopedAgentMessageEmbeddingsTable.userId] = userId.value
                row[ScopedAgentMessageEmbeddingsTable.agentId] = agentId.value
                row[ScopedAgentMessageEmbeddingsTable.messageId] = messageId.value
                row[ScopedAgentMessageEmbeddingsTable.chunkIndex] = chunkIndex
                row[ScopedAgentMessageEmbeddingsTable.textChunk] = textChunk
                row[ScopedAgentMessageEmbeddingsTable.embedding] = embeddingPayload
                row[ScopedAgentMessageEmbeddingsTable.createdAt] = createdAt.takeIf { it > 0 } ?: now
                row[ScopedAgentMessageEmbeddingsTable.updatedAt] = now
            }
        } else {
            ScopedAgentMessageEmbeddingsTable.update({
                (ScopedAgentMessageEmbeddingsTable.userId eq userId.value) and
                    (ScopedAgentMessageEmbeddingsTable.agentId eq agentId.value) and
                    (ScopedAgentMessageEmbeddingsTable.messageId eq messageId.value) and
                    (ScopedAgentMessageEmbeddingsTable.chunkIndex eq chunkIndex)
            }) { row ->
                row[ScopedAgentMessageEmbeddingsTable.textChunk] = textChunk
                row[ScopedAgentMessageEmbeddingsTable.embedding] = embeddingPayload
                row[ScopedAgentMessageEmbeddingsTable.updatedAt] = now
            }
        }
    }

    override suspend fun searchRelevantContext(
        userId: UserId,
        agentId: AgentId,
        queryEmbedding: List<Double>,
        limit: Int,
        minScore: Double,
    ): Result<List<RetrievedContextChunk>> = runDb {
        if (queryEmbedding.isEmpty()) return@runDb emptyList()

        val ranked = ScopedAgentMessageEmbeddingsTable.selectAll()
            .where {
                (ScopedAgentMessageEmbeddingsTable.userId eq userId.value) and
                    (ScopedAgentMessageEmbeddingsTable.agentId eq agentId.value)
            }
            .mapNotNull { row ->
                val candidateEmbedding = parseEmbedding(row[ScopedAgentMessageEmbeddingsTable.embedding])
                if (candidateEmbedding.isEmpty()) return@mapNotNull null
                val score = cosineSimilarity(queryEmbedding, candidateEmbedding)
                if (score < minScore) return@mapNotNull null
                SearchCandidate(
                    messageId = AgentMessageId(row[ScopedAgentMessageEmbeddingsTable.messageId]),
                    text = row[ScopedAgentMessageEmbeddingsTable.textChunk],
                    score = score,
                    createdAt = row[ScopedAgentMessageEmbeddingsTable.createdAt],
                )
            }
            .sortedWith(compareByDescending<SearchCandidate> { it.score }.thenByDescending { it.createdAt })
            .take(limit)

        ranked.map {
            RetrievedContextChunk(
                messageId = it.messageId,
                text = it.text,
                score = it.score,
                createdAt = it.createdAt,
            )
        }
    }

    private suspend fun <T> runDb(block: () -> T): Result<T> = runCatching {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            transaction {
                block()
            }
        }
    }.onFailure { throwable ->
        log.e(throwable) { "Repository operation failed" }
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            Database.connect(
                url = config.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = config.dbUser,
                password = config.dbPassword,
            )

            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    ScopedAgentStateTable,
                    ScopedAgentsTable,
                    ScopedAgentMessagesTable,
                    ScopedAgentContextMemoryTable,
                    ScopedAgentMessageEmbeddingsTable,
                )
            }
            initialized = true
            log.i { "PostgreSQL scoped agent repository initialized at ${config.jdbcUrl}" }
        }
    }

    private fun readStateTx(userId: UserId): AgentState {
        val meta = loadMetaTx(userId)
        val contextMemoryByAgent = ScopedAgentContextMemoryTable.selectAll()
            .where { ScopedAgentContextMemoryTable.userId eq userId.value }
            .associateBy { it[ScopedAgentContextMemoryTable.agentId] }

        val messageRows = ScopedAgentMessagesTable.selectAll()
            .where { ScopedAgentMessagesTable.userId eq userId.value }
            .orderBy(ScopedAgentMessagesTable.createdAt, SortOrder.ASC)
            .orderBy(ScopedAgentMessagesTable.id, SortOrder.ASC)
            .groupBy { it[ScopedAgentMessagesTable.agentId] }
            .mapValues { (_, rows) -> rows.map(::toMessage) }

        val agents = ScopedAgentsTable.selectAll()
            .where { ScopedAgentsTable.userId eq userId.value }
            .orderBy(ScopedAgentsTable.position, SortOrder.ASC)
            .map { row ->
                toAgent(
                    row = row,
                    messages = messageRows[row[ScopedAgentsTable.id]].orEmpty(),
                    contextMemory = contextMemoryByAgent[row[ScopedAgentsTable.id]],
                )
            }

        val activeIdRaw = meta[ScopedAgentStateTable.activeAgentId]
        val activeExists = activeIdRaw != null && agents.any { it.id.value == activeIdRaw }
        val activeId = when {
            activeExists -> AgentId(activeIdRaw!!)
            agents.isEmpty() -> null
            else -> {
                val fallback = agents.first().id.value
                ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) { row ->
                    row[activeAgentId] = fallback
                }
                AgentId(fallback)
            }
        }

        return AgentState(
            agents = agents,
            activeAgentId = activeId,
            version = meta[ScopedAgentStateTable.version],
        )
    }

    private fun loadMetaTx(userId: UserId): ResultRow {
        val existing = ScopedAgentStateTable.selectAll()
            .where { ScopedAgentStateTable.userId eq userId.value }
            .singleOrNull()
        if (existing != null) return existing

        val now = nowMillis()
        ScopedAgentStateTable.insert { row ->
            row[ScopedAgentStateTable.userId] = userId.value
            row[activeAgentId] = null
            row[nextAgentCounter] = 1
            row[version] = 1
            row[updatedAt] = now
        }

        return ScopedAgentStateTable.selectAll()
            .where { ScopedAgentStateTable.userId eq userId.value }
            .singleOrNull()
            ?: error("Scoped agent state metadata row is missing")
    }

    private fun bumpVersionTx(userId: UserId, now: Long = nowMillis()) {
        val meta = loadMetaTx(userId)
        ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) { row ->
            row[version] = meta[ScopedAgentStateTable.version] + 1
            row[updatedAt] = now
        }
    }

    private fun toAgent(
        row: ResultRow,
        messages: List<AgentMessage>,
        contextMemory: ResultRow?,
    ): Agent {
        return Agent(
            id = AgentId(row[ScopedAgentsTable.id]),
            title = row[ScopedAgentsTable.title],
            model = row[ScopedAgentsTable.model],
            maxOutputTokens = row[ScopedAgentsTable.maxOutputTokens],
            temperature = row[ScopedAgentsTable.temperature],
            stopSequences = row[ScopedAgentsTable.stopSequences],
            agentMode = row[ScopedAgentsTable.agentMode],
            status = AgentStatus(row[ScopedAgentsTable.status]),
            contextSummary = contextMemory?.get(ScopedAgentContextMemoryTable.summaryText).orEmpty(),
            summarizedUntilCreatedAt = contextMemory?.get(ScopedAgentContextMemoryTable.summarizedUntilCreatedAt) ?: 0L,
            messages = messages,
        )
    }

    private fun toMessage(row: ResultRow): AgentMessage {
        val usage = if (row[ScopedAgentMessagesTable.usageTotalTokens] != null) {
            AgentUsage(
                inputTokens = row[ScopedAgentMessagesTable.usageInputTokens] ?: 0,
                outputTokens = row[ScopedAgentMessagesTable.usageOutputTokens] ?: 0,
                totalTokens = row[ScopedAgentMessagesTable.usageTotalTokens] ?: 0,
            )
        } else {
            null
        }

        return AgentMessage(
            id = AgentMessageId(row[ScopedAgentMessagesTable.id]),
            role = when (row[ScopedAgentMessagesTable.role]) {
                ROLE_ASSISTANT -> AgentMessageRole.ASSISTANT
                else -> AgentMessageRole.USER
            },
            text = row[ScopedAgentMessagesTable.text],
            status = row[ScopedAgentMessagesTable.status],
            createdAt = row[ScopedAgentMessagesTable.createdAt],
            provider = row[ScopedAgentMessagesTable.provider],
            model = row[ScopedAgentMessagesTable.model],
            usage = usage,
            latencyMs = row[ScopedAgentMessagesTable.latencyMs],
        )
    }

    private fun toContextMemory(row: ResultRow): AgentContextMemory {
        return AgentContextMemory(
            agentId = AgentId(row[ScopedAgentContextMemoryTable.agentId]),
            summaryText = row[ScopedAgentContextMemoryTable.summaryText],
            summarizedUntilCreatedAt = row[ScopedAgentContextMemoryTable.summarizedUntilCreatedAt],
            updatedAt = row[ScopedAgentContextMemoryTable.updatedAt],
        )
    }

    private fun serializeEmbedding(values: List<Double>): String {
        return values.joinToString(separator = ",") { it.toString() }
    }

    private fun parseEmbedding(raw: String): List<Double> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toDoubleOrNull() }
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0

        var dot = 0.0
        var aNorm = 0.0
        var bNorm = 0.0
        for (index in a.indices) {
            val av = a[index]
            val bv = b[index]
            dot += av * bv
            aNorm += av * av
            bNorm += bv * bv
        }

        if (aNorm <= 0.0 || bNorm <= 0.0) return 0.0
        return dot / (sqrt(aNorm) * sqrt(bNorm))
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}

private object ScopedAgentStateTable : Table("scoped_agent_state") {
    val userId = varchar("user_id", 128)
    val activeAgentId = varchar("active_agent_id", 64).nullable()
    val nextAgentCounter = integer("next_agent_counter")
    val version = long("version")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

private object ScopedAgentsTable : Table("scoped_agents") {
    val userId = varchar("user_id", 128)
    val id = varchar("id", 64)
    val title = varchar("title", 255)
    val model = varchar("model", 128)
    val maxOutputTokens = varchar("max_output_tokens", 32)
    val temperature = varchar("temperature", 32)
    val stopSequences = text("stop_sequences")
    val agentMode = varchar("agent_mode", 32)
    val status = varchar("status", 255)
    val position = integer("position")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId, id)
}

private object ScopedAgentMessagesTable : Table("scoped_agent_messages") {
    val userId = varchar("user_id", 128)
    val id = varchar("id", 64)
    val agentId = varchar("agent_id", 64)
    val role = varchar("role", 32)
    val text = text("text")
    val status = varchar("status", 32)
    val createdAt = long("created_at")
    val provider = varchar("provider", 64).nullable()
    val model = varchar("model", 128).nullable()
    val usageInputTokens = integer("usage_input_tokens").nullable()
    val usageOutputTokens = integer("usage_output_tokens").nullable()
    val usageTotalTokens = integer("usage_total_tokens").nullable()
    val latencyMs = long("latency_ms").nullable()

    override val primaryKey = PrimaryKey(userId, id)
}

private object ScopedAgentContextMemoryTable : Table("scoped_agent_context_memory") {
    val userId = varchar("user_id", 128)
    val agentId = varchar("agent_id", 64)
    val summaryText = text("summary_text")
    val summarizedUntilCreatedAt = long("summarized_until_created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId, agentId)
}

private object ScopedAgentMessageEmbeddingsTable : Table("scoped_agent_message_embeddings") {
    val userId = varchar("user_id", 128)
    val agentId = varchar("agent_id", 64)
    val messageId = varchar("message_id", 64)
    val chunkIndex = integer("chunk_index")
    val textChunk = text("text_chunk")
    val embedding = text("embedding")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId, agentId, messageId, chunkIndex)
}

private data class SearchCandidate(
    val messageId: AgentMessageId,
    val text: String,
    val score: Double,
    val createdAt: Long,
)

private const val MAX_TITLE_LENGTH = 20
private const val STATUS_DONE = "done"
private const val STATUS_PROCESSING = "processing"
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
