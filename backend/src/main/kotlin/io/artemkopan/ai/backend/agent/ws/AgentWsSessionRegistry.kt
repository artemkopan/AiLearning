package io.artemkopan.ai.backend.agent.ws

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentWsSessionRegistry {
    private val mutex = Mutex()
    private val sessionsByUser = mutableMapOf<String, LinkedHashSet<DefaultWebSocketServerSession>>()
    private val userBySession = mutableMapOf<DefaultWebSocketServerSession, String>()

    suspend fun register(userScope: String, session: DefaultWebSocketServerSession) {
        mutex.withLock {
            val scopedSessions = sessionsByUser.getOrPut(userScope) { linkedSetOf() }
            scopedSessions.add(session)
            userBySession[session] = userScope
        }
    }

    suspend fun unregister(session: DefaultWebSocketServerSession) {
        mutex.withLock {
            val userScope = userBySession.remove(session) ?: return
            val scopedSessions = sessionsByUser[userScope] ?: return
            scopedSessions.remove(session)
            if (scopedSessions.isEmpty()) {
                sessionsByUser.remove(userScope)
            }
        }
    }

    suspend fun broadcast(userScope: String, text: String) {
        val snapshot = mutex.withLock { sessionsByUser[userScope]?.toList().orEmpty() }
        snapshot.forEach { session ->
            runCatching { session.send(Frame.Text(text)) }
        }
    }
}
