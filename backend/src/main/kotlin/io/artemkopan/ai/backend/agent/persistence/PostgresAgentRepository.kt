package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.AgentDraft
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentResponse
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
            row[status] = "Ready"
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
            row[title] = resolveTitle(agentId.value, draft.prompt)
            row[prompt] = draft.prompt
            row[model] = draft.model
            row[maxOutputTokens] = draft.maxOutputTokens
            row[temperature] = draft.temperature
            row[stopSequences] = draft.stopSequences
            row[agentMode] = draft.agentMode.ifBlank { "default" }
            row[status] = "Ready"
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

    override suspend fun saveGenerationResult(
        agentId: AgentId,
        response: AgentResponse,
        status: AgentStatus,
    ): Result<AgentState> = runDb {
        val updated = AgentsTable.update({ AgentsTable.id eq agentId.value }) { row ->
            row[responseText] = response.text
            row[responseProvider] = response.provider
            row[responseModel] = response.model
            row[usageInputTokens] = response.usage?.inputTokens
            row[usageOutputTokens] = response.usage?.outputTokens
            row[usageTotalTokens] = response.usage?.totalTokens
            row[responseLatencyMs] = response.latencyMs
            row[AgentsTable.status] = status.value
            row[updatedAt] = nowMillis()
        }
        require(updated > 0) { "Agent not found: ${agentId.value}" }
        bumpVersionTx()
        readStateTx()
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
                SchemaUtils.create(AgentStateTable, AgentsTable)
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
        val agents = AgentsTable.selectAll()
            .orderBy(AgentsTable.position, SortOrder.ASC)
            .map(::toAgent)

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

    private fun toAgent(row: ResultRow): Agent {
        val usage = if (row[AgentsTable.usageTotalTokens] != null) {
            AgentUsage(
                inputTokens = row[AgentsTable.usageInputTokens] ?: 0,
                outputTokens = row[AgentsTable.usageOutputTokens] ?: 0,
                totalTokens = row[AgentsTable.usageTotalTokens] ?: 0,
            )
        } else {
            null
        }

        val response = row[AgentsTable.responseText]?.let {
            AgentResponse(
                text = it,
                provider = row[AgentsTable.responseProvider].orEmpty(),
                model = row[AgentsTable.responseModel].orEmpty(),
                usage = usage,
                latencyMs = row[AgentsTable.responseLatencyMs] ?: 0,
            )
        }

        return Agent(
            id = AgentId(row[AgentsTable.id]),
            title = row[AgentsTable.title],
            prompt = row[AgentsTable.prompt],
            model = row[AgentsTable.model],
            maxOutputTokens = row[AgentsTable.maxOutputTokens],
            temperature = row[AgentsTable.temperature],
            stopSequences = row[AgentsTable.stopSequences],
            agentMode = row[AgentsTable.agentMode],
            status = AgentStatus(row[AgentsTable.status]),
            response = response,
        )
    }

    private fun resolveTitle(agentId: String, prompt: String): String {
        val firstLine = prompt.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isNotEmpty()) return firstLine.take(MAX_TITLE_LENGTH)

        val suffix = agentId.substringAfter("agent-", missingDelimiterValue = "")
        return if (suffix.isNotBlank()) "Agent $suffix" else "Agent"
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

private fun org.jetbrains.exposed.sql.Column<Int>.maxOrNullTx(): Int? {
    return this.table.selectAll()
        .map { it[this] }
        .maxOrNull()
}

private const val MAX_TITLE_LENGTH = 20
private const val STATE_ROW_ID = 1
