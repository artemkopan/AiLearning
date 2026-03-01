package io.artemkopan.ai.backend.agent.ws

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentWsProcessingRegistry {
    private val wsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val processingJobs = mutableMapOf<String, ProcessingJob>()
    private val jobsMutex = Mutex()

    suspend fun isAgentBusy(userScope: String, agentId: String): Boolean {
        return jobsMutex.withLock { processingJobs[jobKey(userScope, agentId)] != null }
    }

    suspend fun registerProcessing(userScope: String, agentId: String, messageId: String, job: Job) {
        jobsMutex.withLock {
            processingJobs[jobKey(userScope, agentId)] = ProcessingJob(
                messageId = messageId,
                job = job,
                stopRequested = false,
            )
        }
    }

    suspend fun requestStop(userScope: String, agentId: String, messageId: String) {
        val job = jobsMutex.withLock {
            val current = processingJobs[jobKey(userScope, agentId)] ?: return@withLock null
            if (current.messageId != messageId) return@withLock null
            current.stopRequested = true
            current.job
        }
        job?.cancel()
    }

    suspend fun isStopRequested(userScope: String, agentId: String, messageId: String): Boolean {
        return jobsMutex.withLock {
            val current = processingJobs[jobKey(userScope, agentId)] ?: return@withLock false
            current.messageId == messageId && current.stopRequested
        }
    }

    suspend fun clearProcessing(userScope: String, agentId: String, messageId: String) {
        jobsMutex.withLock {
            val current = processingJobs[jobKey(userScope, agentId)] ?: return@withLock
            if (current.messageId == messageId) {
                processingJobs.remove(jobKey(userScope, agentId))
            }
        }
    }

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = wsScope.launch(block = block)

    fun close() {
        wsScope.cancel()
    }

    private fun jobKey(userScope: String, agentId: String): String = "$userScope::$agentId"
}

private data class ProcessingJob(
    val messageId: String,
    val job: Job,
    var stopRequested: Boolean,
)
