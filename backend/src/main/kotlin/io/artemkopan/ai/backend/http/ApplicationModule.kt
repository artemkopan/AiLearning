package io.artemkopan.ai.backend.http

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.di.appModules
import io.artemkopan.ai.sharedcontract.ErrorResponseDto
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.module(config: AppConfig = AppConfig.fromEnv()) {
    install(Koin) {
        slf4jLogger()
        modules(appModules(config))
    }

    install(CallLogging)

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        )
    }

    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 30_000
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(CORS) {
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHost(config.corsOrigin, schemes = listOf("http", "https"))
    }

    install(StatusPages) {
        exception<Throwable> { call, throwable ->
            val message = throwable.message ?: "Internal server error"
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(code = "internal_error", message = message),
            )
        }
    }

    configureRoutes()
}
