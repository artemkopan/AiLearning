package io.artemkopan.ai.backend.agent.ws

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentWsSessionRegistry {
    private val mutex = Mutex()
    private val sessions = linkedSetOf<DefaultWebSocketServerSession>()

    suspend fun register(session: DefaultWebSocketServerSession) {
        mutex.withLock { sessions.add(session) }
    }

    suspend fun unregister(session: DefaultWebSocketServerSession) {
        mutex.withLock { sessions.remove(session) }
    }

    suspend fun broadcast(text: String) {
        val snapshot = mutex.withLock { sessions.toList() }
        snapshot.forEach { session ->
            runCatching { session.send(Frame.Text(text)) }
        }
    }
}
