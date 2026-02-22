package io.artemkopan.ai.backend.terminal

import io.artemkopan.ai.sharedcontract.ChatStatus
import io.artemkopan.ai.sharedcontract.StatusEvent
import io.artemkopan.ai.sharedcontract.StatusUpdateRequest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class ChatStatusEntry(
    val chatId: String,
    val status: ChatStatus,
    val projectPath: String? = null,
    val eventId: String? = null,
    val title: String? = null,
    val exitCode: Int? = null,
    val since: String = Instant.now().toString(),
)

class StatusManager {
    private val statuses = ConcurrentHashMap<String, ChatStatusEntry>()

    fun initStatus(chatId: String, projectPath: String? = null) {
        statuses[chatId] = ChatStatusEntry(chatId = chatId, status = ChatStatus.idle, projectPath = projectPath)
    }

    fun applyUpdate(request: StatusUpdateRequest): StatusEvent {
        val now = Instant.now().toString()
        val existing = statuses[request.chatId]
        val entry = ChatStatusEntry(
            chatId = request.chatId,
            status = request.status,
            projectPath = existing?.projectPath,
            eventId = request.eventId,
            title = request.title,
            exitCode = request.exitCode,
            since = now,
        )
        statuses[request.chatId] = entry
        return StatusEvent(
            chatId = request.chatId,
            status = request.status,
            eventId = request.eventId,
            exitCode = request.exitCode,
            title = request.title,
            since = now,
        )
    }

    fun getStatus(chatId: String): ChatStatusEntry? = statuses[chatId]

    fun removeStatus(chatId: String) {
        statuses.remove(chatId)
    }

    fun allStatuses(): Map<String, ChatStatusEntry> = statuses.toMap()
}
