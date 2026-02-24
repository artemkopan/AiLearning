package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.AgentDraft
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.model.AgentUsage
import io.artemkopan.ai.core.domain.repository.AgentRepository
import co.touchlab.kermit.Logger
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

class PostgresAgentRepository(
    private val config: AppConfig,
) : AgentRepository {

    private val log = Logger.withTag("PostgresAgentRepository")
    private val initLock = Any()

    @Volatile
    private var initialized = false

    override suspend fun getState(): Result<AgentState> = runDb {
        readStateTx()
    }

    override suspend fun createAgent(): Result<AgentState> = runDb {
        val meta = loadMetaTx()
        val counter = meta[AgentStateTable.nextAgentCounter]
        val newId = "agent-$counter"
        val position = (AgentsTable.position.maxOrNullTx() ?: -1) + 1
        val now = nowMillis()

        AgentsTable.insert { row ->
            row[id] = newId
            row[title] = "Agent $counter"
            row[prompt] = ""
            row[model] = ""
            row[maxOutputTokens] = ""
            row[temperature] = ""
            row[stopSequences] = ""
            row[agentMode] = "default"
            row[status] = STATUS_DONE
            row[responseText] = null
            row[responseProvider] = null
            row[responseModel] = null
            row[usageInputTokens] = null
            row[usageOutputTokens] = null
            row[usageTotalTokens] = null
            row[responseLatencyMs] = null
            row[AgentsTable.position] = position
            row[createdAt] = now
            row[updatedAt] = now
        }

        AgentStateTable.update({ AgentStateTable.id eq STATE_ROW_ID }) { row ->
            row[activeAgentId] = newId
            row[nextAgentCounter] = counter + 1
            row[version] = meta[AgentStateTable.version] + 1
            row[updatedAt] = now
        }

        readStateTx()
    }

    override suspend fun selectAgent(agentId: AgentId): Result<AgentState> = runDb {
        val exists = AgentsTable.selectAll()
            .where { AgentsTable.id eq agentId.value }
            .any()
        require(exists) { "Agent not found: ${agentId.value}" }

        val meta = loadMetaTx()
        val now = nowMillis()
        AgentStateTable.update({ AgentStateTable.id eq STATE_ROW_ID }) { row ->
            row[activeAgentId] = agentId.value
            row[version] = meta[AgentStateTable.version] + 1
            row[updatedAt] = now
        }
        readStateTx()
    }

    override suspend fun updateAgentDraft(agentId: AgentId, draft: AgentDraft): Result<AgentState> = runDb {
        val exists = AgentsTable.selectAll().where { AgentsTable.id eq agentId.value }.singleOrNull()
        require(exists != null) { "Agent not found: ${agentId.value}" }

        val now = nowMillis()
        AgentsTable.update({ AgentsTable.id eq agentId.value }) { row ->
            row[model] = draft.model
            row[maxOutputTokens] = draft.maxOutputTokens
            row[temperature] = draft.temperature
            row[stopSequences] = draft.stopSequences
            row[agentMode] = draft.agentMode.ifBlank { "default" }
            row[updatedAt] = now
        }

        bumpVersionTx(now)
        readStateTx()
    }

    override suspend fun closeAgent(agentId: AgentId): Result<AgentState> = runDb {
        val allBefore = AgentsTable.selectAll()
            .orderBy(AgentsTable.position, SortOrder.ASC)
            .map { it[AgentsTable.id] }
        val closedIndex = allBefore.indexOf(agentId.value)
        require(closedIndex >= 0) { "Agent not found: ${agentId.value}" }

        AgentMessagesTable.deleteWhere { AgentMessagesTable.agentId eq agentId.value }
        AgentsTable.deleteWhere { id eq agentId.value }

        val remaining = AgentsTable.selectAll()
            .orderBy(AgentsTable.position, SortOrder.ASC)
            .map { it[AgentsTable.id] }

        val meta = loadMetaTx()
        val currentActive = meta[AgentStateTable.activeAgentId]
        val newActive = when {
            remaining.isEmpty() -> null
            currentActive == agentId.value -> {
                if (closedIndex >= remaining.size) remaining.last() else remaining[closedIndex]
            }
            currentActive != null && remaining.contains(currentActive) -> currentActive
            else -> remaining.first()
        }

        val now = nowMillis()
        AgentStateTable.update({ AgentStateTable.id eq STATE_ROW_ID }) { row ->
            row[activeAgentId] = newActive
            row[version] = meta[AgentStateTable.version] + 1
            row[updatedAt] = now
        }

        readStateTx()
    }

    override suspend fun updateAgentStatus(agentId: AgentId, status: AgentStatus): Result<AgentState> = runDb {
        val updated = AgentsTable.update({ AgentsTable.id eq agentId.value }) { row ->
            row[AgentsTable.status] = status.value
            row[updatedAt] = nowMillis()
        }
        require(updated > 0) { "Agent not found: ${agentId.value}" }
        bumpVersionTx()
        readStateTx()
    }

    override suspend fun appendMessage(agentId: AgentId, message: AgentMessage): Result<AgentState> = runDb {
        val existing = AgentsTable.selectAll().where { AgentsTable.id eq agentId.value }.singleOrNull()
        require(existing != null) { "Agent not found: ${agentId.value}" }
        val currentMaxCreatedAt = AgentMessagesTable.selectAll()
            .where { AgentMessagesTable.agentId eq agentId.value }
            .map { it[AgentMessagesTable.createdAt] }
            .maxOrNull()
        val requestedCreatedAt = message.createdAt.takeIf { it > 0 } ?: nowMillis()
        val persistedCreatedAt = currentMaxCreatedAt
            ?.let { maxOf(requestedCreatedAt, it + 1) }
            ?: requestedCreatedAt

        AgentMessagesTable.insert { row ->
            row[id] = message.id.value
            row[AgentMessagesTable.agentId] = agentId.value
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
            val currentTitle = existing[AgentsTable.title]
            if (currentTitle.startsWith("Agent ")) {
                val newTitle = message.text.lineSequence().firstOrNull()?.trim().orEmpty().take(MAX_TITLE_LENGTH)
                if (newTitle.isNotBlank()) {
                    AgentsTable.update({ AgentsTable.id eq agentId.value }) { row ->
                        row[title] = newTitle
                        row[updatedAt] = nowMillis()
                    }
                }
            }
        }

        bumpVersionTx()
        readStateTx()
    }

    override suspend fun updateMessage(
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
        val updated = AgentMessagesTable.update({
            (AgentMessagesTable.agentId eq agentId.value) and (AgentMessagesTable.id eq messageId.value)
        }) { row ->
            if (text != null) row[AgentMessagesTable.text] = text
            if (status != null) row[AgentMessagesTable.status] = status
            if (provider != null) row[AgentMessagesTable.provider] = provider
            if (model != null) row[AgentMessagesTable.model] = model
            if (usageInputTokens != null) row[AgentMessagesTable.usageInputTokens] = usageInputTokens
            if (usageOutputTokens != null) row[AgentMessagesTable.usageOutputTokens] = usageOutputTokens
            if (usageTotalTokens != null) row[AgentMessagesTable.usageTotalTokens] = usageTotalTokens
            if (latencyMs != null) row[AgentMessagesTable.latencyMs] = latencyMs
        }
        require(updated > 0) { "Message not found: ${messageId.value}" }

        bumpVersionTx()
        readStateTx()
    }

    override suspend fun findMessage(agentId: AgentId, messageId: AgentMessageId): Result<AgentMessage?> = runDb {
        AgentMessagesTable.selectAll().where {
            (AgentMessagesTable.agentId eq agentId.value) and (AgentMessagesTable.id eq messageId.value)
        }.singleOrNull()?.let(::toMessage)
    }

    override suspend fun hasProcessingMessage(agentId: AgentId): Result<Boolean> = runDb {
        AgentMessagesTable.selectAll().where {
            (AgentMessagesTable.agentId eq agentId.value) and (AgentMessagesTable.status eq STATUS_PROCESSING)
        }.any()
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
                SchemaUtils.create(AgentStateTable, AgentsTable, AgentMessagesTable)
                val existing = AgentStateTable.selectAll().where { AgentStateTable.id eq STATE_ROW_ID }.singleOrNull()
                if (existing == null) {
                    val now = nowMillis()
                    AgentStateTable.insert { row ->
                        row[id] = STATE_ROW_ID
                        row[activeAgentId] = null
                        row[nextAgentCounter] = 1
                        row[version] = 1
                        row[updatedAt] = now
                    }
                }
            }
            initialized = true
            log.i { "PostgreSQL agent repository initialized at ${config.jdbcUrl}" }
        }
    }

    private fun readStateTx(): AgentState {
        val meta = loadMetaTx()
        val messageRows = AgentMessagesTable.selectAll()
            .orderBy(AgentMessagesTable.createdAt, SortOrder.ASC)
            .orderBy(AgentMessagesTable.id, SortOrder.ASC)
            .groupBy { it[AgentMessagesTable.agentId] }
            .mapValues { (_, rows) -> rows.map(::toMessage) }

        val agents = AgentsTable.selectAll()
            .orderBy(AgentsTable.position, SortOrder.ASC)
            .map { row -> toAgent(row, messageRows[row[AgentsTable.id]].orEmpty()) }

        val activeIdRaw = meta[AgentStateTable.activeAgentId]
        val activeExists = activeIdRaw != null && agents.any { it.id.value == activeIdRaw }
        val activeId = when {
            activeExists -> AgentId(activeIdRaw!!)
            agents.isEmpty() -> null
            else -> {
                val fallback = agents.first().id.value
                AgentStateTable.update({ AgentStateTable.id eq STATE_ROW_ID }) { row ->
                    row[activeAgentId] = fallback
                }
                AgentId(fallback)
            }
        }

        return AgentState(
            agents = agents,
            activeAgentId = activeId,
            version = meta[AgentStateTable.version],
        )
    }

    private fun loadMetaTx(): ResultRow {
        return AgentStateTable.selectAll()
            .where { AgentStateTable.id eq STATE_ROW_ID }
            .singleOrNull()
            ?: error("Agent state metadata row is missing")
    }

    private fun bumpVersionTx(now: Long = nowMillis()) {
        val meta = loadMetaTx()
        AgentStateTable.update({ AgentStateTable.id eq STATE_ROW_ID }) { row ->
            row[version] = meta[AgentStateTable.version] + 1
            row[updatedAt] = now
        }
    }

    private fun toAgent(row: ResultRow, messages: List<AgentMessage>): Agent {
        return Agent(
            id = AgentId(row[AgentsTable.id]),
            title = row[AgentsTable.title],
            model = row[AgentsTable.model],
            maxOutputTokens = row[AgentsTable.maxOutputTokens],
            temperature = row[AgentsTable.temperature],
            stopSequences = row[AgentsTable.stopSequences],
            agentMode = row[AgentsTable.agentMode],
            status = AgentStatus(row[AgentsTable.status]),
            messages = messages,
        )
    }

    private fun toMessage(row: ResultRow): AgentMessage {
        val usage = if (row[AgentMessagesTable.usageTotalTokens] != null) {
            AgentUsage(
                inputTokens = row[AgentMessagesTable.usageInputTokens] ?: 0,
                outputTokens = row[AgentMessagesTable.usageOutputTokens] ?: 0,
                totalTokens = row[AgentMessagesTable.usageTotalTokens] ?: 0,
            )
        } else {
            null
        }

        return AgentMessage(
            id = AgentMessageId(row[AgentMessagesTable.id]),
            role = when (row[AgentMessagesTable.role]) {
                ROLE_ASSISTANT -> AgentMessageRole.ASSISTANT
                else -> AgentMessageRole.USER
            },
            text = row[AgentMessagesTable.text],
            status = row[AgentMessagesTable.status],
            createdAt = row[AgentMessagesTable.createdAt],
            provider = row[AgentMessagesTable.provider],
            model = row[AgentMessagesTable.model],
            usage = usage,
            latencyMs = row[AgentMessagesTable.latencyMs],
        )
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}

private object AgentStateTable : Table("agent_state") {
    val id = integer("id")
    val activeAgentId = varchar("active_agent_id", 64).nullable()
    val nextAgentCounter = integer("next_agent_counter")
    val version = long("version")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

private object AgentsTable : Table("agents") {
    val id = varchar("id", 64)
    val title = varchar("title", 255)
    val prompt = text("prompt")
    val model = varchar("model", 128)
    val maxOutputTokens = varchar("max_output_tokens", 32)
    val temperature = varchar("temperature", 32)
    val stopSequences = text("stop_sequences")
    val agentMode = varchar("agent_mode", 32)
    val status = varchar("status", 255)
    val responseText = text("response_text").nullable()
    val responseProvider = varchar("response_provider", 64).nullable()
    val responseModel = varchar("response_model", 128).nullable()
    val usageInputTokens = integer("usage_input_tokens").nullable()
    val usageOutputTokens = integer("usage_output_tokens").nullable()
    val usageTotalTokens = integer("usage_total_tokens").nullable()
    val responseLatencyMs = long("response_latency_ms").nullable()
    val position = integer("position")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

private object AgentMessagesTable : Table("agent_messages") {
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

    override val primaryKey = PrimaryKey(id)
}

private fun org.jetbrains.exposed.sql.Column<Int>.maxOrNullTx(): Int? {
    return this.table.selectAll()
        .map { it[this] }
        .maxOrNull()
}

private const val MAX_TITLE_LENGTH = 20
private const val STATE_ROW_ID = 1
private const val STATUS_DONE = "done"
private const val STATUS_PROCESSING = "processing"
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
