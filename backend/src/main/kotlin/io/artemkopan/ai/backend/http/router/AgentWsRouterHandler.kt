package io.artemkopan.ai.backend.http.router

import io.artemkopan.ai.backend.agent.ws.AgentWsMessageHandler
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.core.annotation.Single

@Single(binds = [RouterHandler::class])
class AgentWsRouterHandler(
    private val wsHandler: Lazy<AgentWsMessageHandler>,
) : RouterHandler {
    override fun Routing.invoke() {
        webSocket("/api/v1/agents/ws") {
            val userScope = call.resolveUserScope()
            wsHandler.value.onConnected(userScope, this)
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        wsHandler.value.onTextMessage(userScope, this, frame.readText())
                    }
                }
            } finally {
                wsHandler.value.onDisconnected(this)
            }
        }
    }
}
