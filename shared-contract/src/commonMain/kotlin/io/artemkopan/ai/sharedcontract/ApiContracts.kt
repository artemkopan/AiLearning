package io.artemkopan.ai.sharedcontract

import kotlinx.serialization.Serializable

@Serializable
data class ProjectInfo(
    val name: String,
    val path: String,
)

@Serializable
data class CreateChatRequest(
    val projectPath: String,
)

@Serializable
data class CreateChatResponse(
    val chatId: String,
)

@Serializable
data class ChatInfo(
    val chatId: String,
    val projectPath: String,
    val status: ChatStatus,
    val since: String,
)

@Serializable
enum class ChatStatus {
    idle, running, done, failed, attention
}

@Serializable
data class StatusEvent(
    val type: String = "status",
    val chatId: String,
    val status: ChatStatus,
    val eventId: String? = null,
    val exitCode: Int? = null,
    val title: String? = null,
    val since: String,
)

@Serializable
data class StatusUpdateRequest(
    val chatId: String,
    val status: ChatStatus,
    val eventId: String? = null,
    val title: String? = null,
    val body: String? = null,
    val exitCode: Int? = null,
)

@Serializable
data class ChatCreatedEvent(
    val type: String = "chat_created",
    val chat: ChatInfo,
)

@Serializable
data class ErrorResponseDto(
    val code: String,
    val message: String,
)
