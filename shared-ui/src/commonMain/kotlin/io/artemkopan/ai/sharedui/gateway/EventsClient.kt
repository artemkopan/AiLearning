package io.artemkopan.ai.sharedui.gateway

import io.artemkopan.ai.sharedcontract.ChatCreatedEvent
import io.artemkopan.ai.sharedcontract.StatusEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface WsEvent {
    data class Status(val event: StatusEvent) : WsEvent
    data class ChatCreated(val event: ChatCreatedEvent) : WsEvent
}

class EventsClient(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun connect(onEvent: (WsEvent) -> Unit) {
        client.webSocket("/events") {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val parsed = parseEvent(text) ?: continue
                    onEvent(parsed)
                }
            }
        }
    }

    private fun parseEvent(text: String): WsEvent? {
        val obj = json.decodeFromString<JsonObject>(text)
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        return when (type) {
            "status" -> WsEvent.Status(json.decodeFromString<StatusEvent>(text))
            "chat_created" -> WsEvent.ChatCreated(json.decodeFromString<ChatCreatedEvent>(text))
            else -> null
        }
    }
}
