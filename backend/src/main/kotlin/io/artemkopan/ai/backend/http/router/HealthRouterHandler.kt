package io.artemkopan.ai.backend.http.router

import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

@Single(binds = [RouterHandler::class])
class HealthRouterHandler : RouterHandler {
    private val logger = LoggerFactory.getLogger(HealthRouterHandler::class.java)

    override fun Routing.invoke() {
        get("/health") {
            logger.info("GET /health")
            call.respond(mapOf("status" to "ok"))
        }
    }
}
