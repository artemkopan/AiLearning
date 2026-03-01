package io.artemkopan.ai.backend.http

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsMessageHandler
import io.artemkopan.ai.backend.agent.ws.AgentWsSessionRegistry
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.di.appModules
import io.artemkopan.ai.backend.provider.LlmModelCatalog
import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.application.usecase.stats.GetAgentStatsUseCase
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.artemkopan.ai.sharedcontract.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.util.*

private val RequestIdKey = io.ktor.util.AttributeKey<String>("requestId")

fun Application.module(
    config: AppConfig = AppConfig.fromEnv(),
    llmRepositoryOverride: LlmRepository? = null,
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

    val generateTextUseCase by inject<GenerateTextUseCase>()
    val mapFailureToUserMessageUseCase by inject<MapFailureToUserMessageUseCase>()
    val getAgentStateUseCase by inject<GetAgentStateUseCase>()
    val createAgentUseCase by inject<CreateAgentUseCase>()
    val selectAgentUseCase by inject<SelectAgentUseCase>()
    val updateAgentDraftUseCase by inject<UpdateAgentDraftUseCase>()
    val closeAgentUseCase by inject<CloseAgentUseCase>()
    val startAgentMessageUseCase by inject<StartAgentMessageUseCase>()
    val completeAgentMessageUseCase by inject<CompleteAgentMessageUseCase>()
    val failAgentMessageUseCase by inject<FailAgentMessageUseCase>()
    val stopAgentMessageUseCase by inject<StopAgentMessageUseCase>()
    val getAgentStatsUseCase by inject<GetAgentStatsUseCase>()
    val agentRepository by inject<AgentRepository>()
    val modelCatalog by inject<LlmModelCatalog>()
    val injectedLlmRepository by inject<LlmRepository>()
    val llmRepository = llmRepositoryOverride ?: injectedLlmRepository
    val statsMapper = AgentStatsHttpMapper()

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
        failAgentMessageUseCase = failAgentMessageUseCase,
        stopAgentMessageUseCase = stopAgentMessageUseCase,
        generateTextUseCase = generateTextUseCase,
        mapFailureToUserMessageUseCase = mapFailureToUserMessageUseCase,
        agentRepository = agentRepository,
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
            val models = resolveConfiguredModels(
                repository = llmRepository,
                modelCatalog = modelCatalog,
                fallbackContextWindowTokens = config.defaultContextWindowTokens,
                logger = logger,
            )
            call.respond(
                HttpStatusCode.OK,
                AgentConfigDto(
                    models = models,
                    defaultModel = config.defaultModel,
                    defaultContextWindowTokens = config.defaultContextWindowTokens,
                    temperatureMin = 0.0,
                    temperatureMax = 2.0,
                    defaultTemperature = 0.7,
                )
            )
        }

        get("/api/v1/models/metadata") {
            val modelId = call.request.queryParameters["model"]?.trim().orEmpty()
            if (modelId.isEmpty()) {
                throw AppError.Validation("Model query parameter is required.")
            }

            val metadata = llmRepository.getModelMetadata(modelId).getOrElse { throwable ->
                throw throwable
            }
            call.respond(
                HttpStatusCode.OK,
                ModelMetadataDto(
                    model = metadata.model,
                    provider = metadata.provider,
                    inputTokenLimit = metadata.inputTokenLimit,
                    outputTokenLimit = metadata.outputTokenLimit,
                )
            )
        }

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

private suspend fun resolveConfiguredModels(
    repository: LlmRepository,
    modelCatalog: LlmModelCatalog,
    fallbackContextWindowTokens: Int,
    logger: org.slf4j.Logger,
): List<ModelOptionDto> = coroutineScope {
    modelCatalog.curatedModels(fallbackContextWindowTokens)
        .map { option ->
            async {
                val resolvedContextWindow = repository.getModelMetadata(option.id)
                    .map { metadata -> metadata.inputTokenLimit }
                    .getOrElse { throwable ->
                        logger.warn(
                            "Model metadata lookup failed for model={}; falling back to defaultContextWindowTokens={}; reason={}",
                            option.id,
                            fallbackContextWindowTokens,
                            throwable.message,
                        )
                        fallbackContextWindowTokens
                    }
                option.copy(contextWindowTokens = resolvedContextWindow)
            }
        }
        .awaitAll()
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
