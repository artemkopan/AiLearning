package io.artemkopan.ai.backend.http

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.di.AppContainer
import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.sharedcontract.ErrorResponseDto
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto
import io.artemkopan.ai.sharedcontract.TokenUsageDto
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.UUID

private val RequestIdKey = io.ktor.util.AttributeKey<String>("requestId")

fun Application.module(config: AppConfig = AppConfig.fromEnv()) {
    val container = AppContainer(config)
    val logger = log
    logger.info(
        "Application config: port={}, geminiModel={}, corsOrigin={}, geminiApiKeyPresent={}",
        config.port,
        config.defaultModel,
        config.corsOrigin,
        config.geminiApiKey.isNotBlank(),
    )

    install(CallLogging)
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        )
    }
    install(CORS) {
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHost(config.corsOrigin, schemes = listOf("http", "https"))
    }
    install(StatusPages) {
        exception<Throwable> { call, throwable ->
            val requestId = call.ensureRequestId()
            val userMessage = container.mapFailureToUserMessageUseCase.execute(throwable)
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
        get("/health") {
            logger.info("GET /health")
            call.respond(mapOf("status" to "ok"))
        }

        post("/api/v1/generate") {
            val requestId = call.ensureRequestId()
            val startedAt = System.currentTimeMillis()
            val payload = call.receive<GenerateRequestDto>()
            logger.info("POST /api/v1/generate requestId={} promptLength={}", requestId, payload.prompt.length)

            val result = container.generateTextUseCase.execute(
                GenerateCommand(
                    prompt = payload.prompt,
                    model = payload.model,
                    temperature = payload.temperature,
                )
            )

            result.fold(
                onSuccess = { output ->
                    val latencyMs = System.currentTimeMillis() - startedAt
                    logger.info("POST /api/v1/generate success requestId={} latencyMs={}", requestId, latencyMs)
                    call.respond(
                        HttpStatusCode.OK,
                        GenerateResponseDto(
                            text = output.text,
                            provider = output.provider,
                            model = output.model,
                            usage = output.usage?.let {
                                TokenUsageDto(
                                    inputTokens = it.inputTokens,
                                    outputTokens = it.outputTokens,
                                    totalTokens = it.totalTokens,
                                )
                            },
                            requestId = requestId,
                            latencyMs = latencyMs,
                        )
                    )
                },
                onFailure = { throwable ->
                    logger.error("POST /api/v1/generate failed requestId={}", requestId, throwable)
                    throw throwable
                }
            )
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.ensureRequestId(): String {
    if (attributes.contains(RequestIdKey)) return attributes[RequestIdKey]

    val newId = UUID.randomUUID().toString()
    attributes.put(RequestIdKey, newId)
    return newId
}
