package io.artemkopan.ai.backend.http.router

import io.artemkopan.ai.sharedcontract.AgentStatsResponseDto
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.annotation.Single

@Single(binds = [RouterHandler::class])
class AgentStatsRouterHandler : RouterHandler {
    override fun Routing.invoke() {
        get("/api/v1/agents/stats") {
            call.respond(HttpStatusCode.OK, AgentStatsResponseDto(agents = emptyList()))
        }
    }
}
