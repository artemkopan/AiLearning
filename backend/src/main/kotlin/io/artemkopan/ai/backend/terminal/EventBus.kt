package io.artemkopan.ai.backend.terminal

import io.artemkopan.ai.sharedcontract.StatusEvent
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Collections

class EventBus {
    private val sessions = Collections.synchronizedSet(mutableSetOf<DefaultWebSocketSession>())
    private val json = Json { explicitNulls = false }

    suspend fun register(session: DefaultWebSocketSession) {
        sessions.add(session)
    }

    fun unregister(session: DefaultWebSocketSession) {
        sessions.remove(session)
    }

    suspend fun broadcast(event: StatusEvent) {
        val text = json.encodeToString(event)
        val frame = Frame.Text(text)
        val snapshot = synchronized(sessions) { sessions.toList() }
        for (session in snapshot) {
            try {
                session.send(frame.copy())
            } catch (_: Exception) {
                sessions.remove(session)
            }
        }
    }
}
