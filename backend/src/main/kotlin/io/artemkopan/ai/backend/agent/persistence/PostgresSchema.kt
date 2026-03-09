package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.core.domain.model.DEFAULT_RECENT_MESSAGES_N
import io.artemkopan.ai.core.domain.model.DEFAULT_SUMMARIZE_EVERY_K
import org.jetbrains.exposed.sql.Table

internal object ScopedAgentStateTable : Table("scoped_agent_state") {
    val userId = varchar("user_id", 128)
    val activeAgentId = varchar("active_agent_id", 64).nullable()
    val nextAgentCounter = integer("next_agent_counter")
    val version = long("version")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

internal object ScopedAgentsTable : Table("scoped_agents") {
    val userId = varchar("user_id", 128)
    val id = varchar("id", 64)
    val title = varchar("title", 255)
    val model = varchar("model", 128)
    val maxOutputTokens = varchar("max_output_tokens", 32)
    val temperature = varchar("temperature", 32)
    val stopSequences = text("stop_sequences")
    val agentMode = varchar("agent_mode", 32)
    val contextStrategy = varchar("context_strategy", 64).default(CONTEXT_STRATEGY_ROLLING)
    val contextRecentMessagesN = integer("context_recent_messages_n").default(DEFAULT_RECENT_MESSAGES_N)
    val contextSummarizeEveryK = integer("context_summarize_every_k").default(DEFAULT_SUMMARIZE_EVERY_K)
    val activeBranchId = varchar("active_branch_id", 64).nullable().default(null)
    val invariants = text("invariants").default("")
    val status = varchar("status", 255)
    val position = integer("position")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId, id)
}

internal object ScopedAgentMessagesTable : Table("scoped_agent_messages") {
    val userId = varchar("user_id", 128)
    val id = varchar("id", 64)
    val agentId = varchar("agent_id", 64)
    val branchId = varchar("branch_id", 64).nullable().default(null)
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
    val messageType = varchar("message_type", 32).default("text")
    override val primaryKey = PrimaryKey(userId, id)
}

internal object ScopedAgentTasksTable : Table("scoped_agent_tasks") {
    val userId = varchar("user_id", 128)
    val taskId = varchar("task_id", 64)
    val agentId = varchar("agent_id", 64)
    val title = varchar("title", 512)
    val currentPhase = varchar("current_phase", 32)
    val currentStepIndex = integer("current_step_index").default(0)
    val stepsJson = text("steps_json")
    val planJson = text("plan_json").default("")
    val validationJson = text("validation_json").default("")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId, taskId)
}

internal object ScopedAgentTaskTransitionsTable : Table("scoped_agent_task_transitions") {
    val id = integer("id").autoIncrement()
    val userId = varchar("user_id", 128)
    val taskId = varchar("task_id", 64)
    val fromPhase = varchar("from_phase", 32)
    val toPhase = varchar("to_phase", 32)
    val reason = text("reason")
    val timestamp = long("timestamp")
    override val primaryKey = PrimaryKey(id)
}

internal const val CONTEXT_STRATEGY_ROLLING = "rolling_summary_recent_n"
internal const val STATUS_DONE = "done"
internal const val STATUS_PROCESSING = "processing"
internal const val ROLE_USER = "user"
internal const val ROLE_ASSISTANT = "assistant"
