package io.artemkopan.ai.backend.agent.persistence

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
    private val config: AppConfig,
) {
    private val initMutex = Mutex()
    @Volatile private var initialized = false

    suspend fun <T> runDb(block: () -> T): Result<T> = runCatching {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            transaction { block() }
        }
    }

    fun nowMillis(): Long = System.currentTimeMillis()

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
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
                    ScopedAgentTasksTable,
                    ScopedAgentTaskTransitionsTable,
                )
            }
            initialized = true
        }
    }
}
