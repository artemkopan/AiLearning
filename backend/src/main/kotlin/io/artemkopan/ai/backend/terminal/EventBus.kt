package io.artemkopan.ai.backend.terminal

import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import java.util.Collections

class EventBus {
    private val sessions = Collections.synchronizedSet(mutableSetOf<DefaultWebSocketSession>())

    suspend fun register(session: DefaultWebSocketSession) {
        sessions.add(session)
    }

    fun unregister(session: DefaultWebSocketSession) {
        sessions.remove(session)
    }

    suspend fun broadcast(jsonText: String) {
        val frame = Frame.Text(jsonText)
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
