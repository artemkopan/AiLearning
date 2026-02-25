package io.artemkopan.ai.backend.http

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsMessageHandler
import io.artemkopan.ai.backend.agent.ws.AgentWsSessionRegistry
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.di.appModules
import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.usecase.CloseAgentUseCase
import io.artemkopan.ai.core.application.usecase.CompleteAgentMessageUseCase
import io.artemkopan.ai.core.application.usecase.CreateAgentUseCase
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.usecase.GenerateTextUseCase
import io.artemkopan.ai.core.application.usecase.MapFailureToUserMessageUseCase
import io.artemkopan.ai.core.application.usecase.SelectAgentUseCase
import io.artemkopan.ai.core.application.usecase.StartAgentMessageUseCase
import io.artemkopan.ai.core.application.usecase.StopAgentMessageUseCase
import io.artemkopan.ai.core.application.usecase.UpdateAgentDraftUseCase
import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.ErrorResponseDto
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto
import io.artemkopan.ai.sharedcontract.ModelOptionDto
import io.artemkopan.ai.sharedcontract.TokenUsageDto
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.util.UUID

private val RequestIdKey = io.ktor.util.AttributeKey<String>("requestId")

fun Application.module(config: AppConfig = AppConfig.fromEnv()) {
    val logger = log
    logger.info(
        "Application config: port={}, geminiModel={}, corsOrigin={}, dbHost={}, dbPort={}, dbName={}, geminiApiKeyPresent={}",
        config.port,
        config.defaultModel,
        config.corsOrigin,
        config.dbHost,
        config.dbPort,
        config.dbName,
        config.geminiApiKey.isNotBlank(),
    )

    install(Koin) {
        slf4jLogger()
        modules(appModules(config))
    }

    val generateTextUseCase by inject<GenerateTextUseCase>()
    val mapFailureToUserMessageUseCase by inject<MapFailureToUserMessageUseCase>()
    val getAgentStateUseCase by inject<GetAgentStateUseCase>()
    val createAgentUseCase by inject<CreateAgentUseCase>()
    val selectAgentUseCase by inject<SelectAgentUseCase>()
    val updateAgentDraftUseCase by inject<UpdateAgentDraftUseCase>()
    val closeAgentUseCase by inject<CloseAgentUseCase>()
    val startAgentMessageUseCase by inject<StartAgentMessageUseCase>()
    val completeAgentMessageUseCase by inject<CompleteAgentMessageUseCase>()
    val stopAgentMessageUseCase by inject<StopAgentMessageUseCase>()

    val appJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "type"
    }

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

    val wsSessionRegistry = AgentWsSessionRegistry()
    val wsMapper = AgentWsMapper()
    val wsHandler = AgentWsMessageHandler(
        getAgentStateUseCase = getAgentStateUseCase,
        createAgentUseCase = createAgentUseCase,
        selectAgentUseCase = selectAgentUseCase,
        updateAgentDraftUseCase = updateAgentDraftUseCase,
        closeAgentUseCase = closeAgentUseCase,
        startAgentMessageUseCase = startAgentMessageUseCase,
        completeAgentMessageUseCase = completeAgentMessageUseCase,
        stopAgentMessageUseCase = stopAgentMessageUseCase,
        generateTextUseCase = generateTextUseCase,
        mapFailureToUserMessageUseCase = mapFailureToUserMessageUseCase,
        sessionRegistry = wsSessionRegistry,
        mapper = wsMapper,
        json = appJson,
        logger = logger,
    )

    routing {
        get("/health") {
            logger.info("GET /health")
            call.respond(mapOf("status" to "ok"))
        }

        get("/api/v1/config") {
            call.respond(
                HttpStatusCode.OK,
                AgentConfigDto(
                    models = listOf(
                        ModelOptionDto(
                            id = "gemini-3-flash-preview",
                            name = "Gemini 3 Flash Preview",
                            provider = "gemini",
                            contextWindowTokens = config.defaultContextWindowTokens,
                        ),
                        ModelOptionDto(
                            id = "gemini-2.5-flash",
                            name = "Gemini 2.5 Flash",
                            provider = "gemini",
                            contextWindowTokens = config.defaultContextWindowTokens,
                        ),
                        ModelOptionDto(
                            id = "gemini-2.5-flash-lite",
                            name = "Gemini 2.5 Flash Lite",
                            provider = "gemini",
                            contextWindowTokens = config.defaultContextWindowTokens,
                        ),
                        ModelOptionDto(
                            id = "gemini-flash-latest",
                            name = "Gemini Flash Latest",
                            provider = "gemini",
                            contextWindowTokens = config.defaultContextWindowTokens,
                        ),
                    ),
                    defaultModel = config.defaultModel,
                    defaultContextWindowTokens = config.defaultContextWindowTokens,
                    temperatureMin = 0.0,
                    temperatureMax = 2.0,
                    defaultTemperature = 0.7,
                )
            )
        }

        webSocket("/api/v1/agents/ws") {
            val userScope = call.resolveUserScope()
            wsHandler.onConnected(userScope, this)
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        wsHandler.onTextMessage(userScope, this, frame.readText())
                    }
                }
            } finally {
                wsHandler.onDisconnected(this)
            }
        }

        post("/api/v1/generate") {
            val requestId = call.ensureRequestId()
            val startedAt = System.currentTimeMillis()
            val payload = call.receive<GenerateRequestDto>()
            logger.info(
                "POST /api/v1/generate requestId={} body: prompt='{}', model={}, temperature={}, maxOutputTokens={}, stopSequences={}, agentMode={}",
                requestId,
                payload.prompt,
                payload.model,
                payload.temperature,
                payload.maxOutputTokens,
                payload.stopSequences,
                payload.agentMode,
            )

            val result = generateTextUseCase.execute(
                GenerateCommand(
                    prompt = payload.prompt,
                    model = payload.model,
                    temperature = payload.temperature,
                    maxOutputTokens = payload.maxOutputTokens,
                    stopSequences = payload.stopSequences,
                    agentMode = payload.agentMode?.name?.lowercase(),
                )
            )

            result.fold(
                onSuccess = { output ->
                    val latencyMs = System.currentTimeMillis() - startedAt
                    logger.info(
                        "POST /api/v1/generate success requestId={} latencyMs={} response: provider={}, model={}, tokens(in={}, out={}, total={}), text='{}'",
                        requestId,
                        latencyMs,
                        output.provider,
                        output.model,
                        output.usage?.inputTokens,
                        output.usage?.outputTokens,
                        output.usage?.totalTokens,
                        output.text,
                    )
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

private fun io.ktor.server.application.ApplicationCall.resolveUserScope(): String {
    val raw = request.queryParameters["userId"]
        ?: request.headers["X-User-Id"]
        ?: "anonymous"

    val normalized = raw
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9._-]"), "-")
        .take(64)
        .trim('-')

    return normalized.ifBlank { "anonymous" }
}
