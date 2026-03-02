package io.artemkopan.ai.backend.http.router

import io.artemkopan.ai.backend.http.AgentStatsHttpMapper
import io.artemkopan.ai.core.application.usecase.stats.GetAgentStatsUseCase
import io.artemkopan.ai.sharedcontract.AgentStatsResponseDto
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.annotation.Single

@Single(binds = [RouterHandler::class])
class AgentStatsRouterHandler(
    private val getAgentStatsUseCase: GetAgentStatsUseCase,
    private val statsMapper: AgentStatsHttpMapper,
) : RouterHandler {
    override fun Routing.invoke() {
        get("/api/v1/agents/stats") {
            val userScope = call.resolveUserScope()
            val stats = getAgentStatsUseCase.execute(userScope).getOrElse { throwable ->
                throw throwable
            }
            call.respond(
                HttpStatusCode.OK,
                AgentStatsResponseDto(
                    agents = stats.map { statsMapper.toDto(it) }
                )
            )
        }
    }
}
