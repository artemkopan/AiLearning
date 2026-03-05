package io.artemkopan.ai.backend.agent.persistence.helper

import co.touchlab.kermit.Logger
import io.artemkopan.ai.backend.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.annotation.Single

@Single
internal class PostgresDbRuntime(
    private val config: Lazy<AppConfig>,
    private val log: Lazy<Logger>,
) {
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    suspend fun <T> runDb(block: () -> T): Result<T> = runCatching {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            transaction {
                block()
            }
        }
    }.onFailure { throwable ->
        log.value.e(throwable) { "Repository operation failed" }
    }

    fun nowMillis(): Long = System.currentTimeMillis()

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            Database.connect(
                url = config.value.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = config.value.dbUser,
                password = config.value.dbPassword,
            )

            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    ScopedAgentStateTable,
                    ScopedAgentsTable,
                    ScopedAgentMessagesTable,
                    ScopedAgentContextMemoryTable,
                    ScopedAgentMessageEmbeddingsTable,
                    ScopedAgentFactsTable,
                    ScopedAgentBranchesTable,
                    ScopedUserProfileTable,
                )
            }
            initialized = true
            log.value.i { "PostgreSQL scoped agent repository initialized at ${config.value.jdbcUrl}" }
        }
    }
}
