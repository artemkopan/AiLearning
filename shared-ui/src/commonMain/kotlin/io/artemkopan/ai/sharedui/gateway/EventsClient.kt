package io.artemkopan.ai.sharedui.gateway

import io.artemkopan.ai.sharedcontract.StatusEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json

class EventsClient(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun connect(onEvent: (StatusEvent) -> Unit) {
        client.webSocket("/events") {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val event = json.decodeFromString<StatusEvent>(frame.readText())
                    onEvent(event)
                }
            }
        }
    }
}
