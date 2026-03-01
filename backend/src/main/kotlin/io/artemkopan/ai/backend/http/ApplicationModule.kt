package io.artemkopan.ai.backend.http

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.di.appModules
import io.artemkopan.ai.backend.http.router.RouterHandler
import io.artemkopan.ai.backend.http.router.ensureRequestId
import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.usecase.MapFailureToUserMessageUseCase
import io.artemkopan.ai.sharedcontract.ErrorResponseDto
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.module(
    config: AppConfig = AppConfig.fromEnv(),
) {
    val logger = log
    logger.info(
        "Application config: port={}, defaultModel={}, deepseekBaseUrl={}, apiKeySource={}, corsOrigin={}, dbHost={}, dbPort={}, dbName={}, activeApiKeyPresent={}",
        config.port,
        config.defaultModel,
        config.deepseekBaseUrl,
        config.activeProviderApiKeySource,
        config.corsOrigin,
        config.dbHost,
        config.dbPort,
        config.dbName,
        config.activeProviderApiKey.isNotBlank(),
    )

    install(Koin) {
        slf4jLogger()
        modules(appModules(config))
    }

    val mapFailureToUserMessageUseCase by inject<MapFailureToUserMessageUseCase>()
    val routerHandlers by inject<List<RouterHandler>>()

    val appJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    install(DoubleReceive)
    install(HttpBodyLogging)
    install(CallLogging)
    install(ContentNegotiation) {
        json(appJson)
    }
    install(WebSockets)
    install(CORS) {
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHost(config.corsOrigin, schemes = listOf("http", "https"))
    }
    install(StatusPages) {
        exception<Throwable> { call, throwable ->
            val requestId = call.ensureRequestId()
            val userMessage = mapFailureToUserMessageUseCase.execute(throwable)
                .getOrDefault("Something went wrong. Please try again.")

            val (status, code) = when (throwable) {
                is AppError.Validation -> HttpStatusCode.BadRequest to "validation_error"
                is AppError.RateLimited -> HttpStatusCode.TooManyRequests to "rate_limited"
                is AppError.UpstreamUnavailable -> HttpStatusCode.BadGateway to "provider_error"
                else -> HttpStatusCode.InternalServerError to "internal_error"
            }

            call.respond(
                status,
                ErrorResponseDto(
                    code = code,
                    message = userMessage,
                    requestId = requestId,
                )
            )
        }
    }

    routing {
        routerHandlers.forEach { handler ->
            handler.run {
                this@routing.invoke()
            }
        }
    }
}
