package io.artemkopan.ai.backend.di

import io.artemkopan.ai.backend.agent.persistence.PostgresAgentRepository
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresMappingHelpers
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresStateHelpers
import io.artemkopan.ai.backend.agent.persistence.operation.*
import io.artemkopan.ai.backend.agent.ws.*
import io.artemkopan.ai.backend.agent.ws.usecase.*
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.http.AgentStatsHttpMapper
import io.artemkopan.ai.backend.http.router.*
import io.artemkopan.ai.backend.provider.DeepSeekModelCatalog
import io.artemkopan.ai.backend.provider.LlmModelCatalog
import io.artemkopan.ai.core.application.mapper.DomainErrorMapper
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.application.usecase.context.*
import io.artemkopan.ai.core.application.usecase.shortcut.ExpandStatsShortcutsInPromptUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ParseStatsShortcutTokensUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ResolveStatsShortcutsUseCase
import io.artemkopan.ai.core.application.usecase.stats.BuildAgentStatsSnippetUseCase
import io.artemkopan.ai.core.application.usecase.stats.GetAgentStatsUseCase
import io.artemkopan.ai.core.data.client.DeepSeekNetworkClient
import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.data.repository.DefaultLlmRepository
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import co.touchlab.kermit.Logger as KermitLogger

private inline fun <reified T : Any> Scope.lazyGet(): Lazy<T> = lazy(LazyThreadSafetyMode.NONE) { get<T>() }

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(get())
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
            }
            install(Logging) {
                val httpClientLogger = LoggerFactory.getLogger("io.artemkopan.ai.backend.httpclient")
                logger = object : io.ktor.client.plugins.logging.Logger {
                    override fun log(message: String) {
                        httpClientLogger.info(message)
                    }
                }
                level = LogLevel.ALL
                sanitizeHeader { header ->
                    header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                            header.equals("x-goog-api-key", ignoreCase = true)
                }
            }
        }
    }
}

val dataModule = module {
    single<LlmNetworkClient> {
        val config = get<AppConfig>()
        DeepSeekNetworkClient(
            httpClient = get(),
            apiKey = config.deepseekApiKey,
            baseUrl = config.deepseekBaseUrl,
        )
    }

    single<LlmRepository> {
        DefaultLlmRepository(get())
    }

    single<LlmModelCatalog> {
        DeepSeekModelCatalog()
    }

    single<KermitLogger> { KermitLogger.withTag("PostgresAgentRepository") }
    single { PostgresDbRuntime(config = lazyGet(), log = lazyGet()) }
    single { PostgresMappingHelpers(config = lazyGet()) }
    single { PostgresStateHelpers(runtime = lazyGet(), mapping = lazyGet()) }

    single { GetStateOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { CreateAgentOperation(runtime = lazyGet(), stateHelpers = lazyGet(), config = lazyGet()) }
    single { SelectAgentOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { UpdateAgentDraftOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { CloseAgentOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { UpdateAgentStatusOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { AppendMessageOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { UpdateMessageOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { FindMessageOperation(runtime = lazyGet(), mapping = lazyGet()) }
    single { HasProcessingMessageOperation(runtime = lazyGet()) }
    single { GetContextMemoryOperation(runtime = lazyGet(), mapping = lazyGet()) }
    single { UpsertContextMemoryOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { ListMessagesAfterOperation(runtime = lazyGet(), mapping = lazyGet()) }
    single { UpsertMessageEmbeddingOperation(runtime = lazyGet()) }
    single { SearchRelevantContextOperation(runtime = lazyGet()) }
    single { GetAgentFactsOperation(runtime = lazyGet()) }
    single { UpsertAgentFactsOperation(runtime = lazyGet()) }
    single { CreateBranchOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { SwitchBranchOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { DeleteBranchOperation(runtime = lazyGet(), stateHelpers = lazyGet()) }
    single { GetBranchesOperation(runtime = lazyGet()) }

    single<AgentRepository> {
        PostgresAgentRepository(
            getStateOperation = lazyGet(),
            createAgentOperation = lazyGet(),
            selectAgentOperation = lazyGet(),
            updateAgentDraftOperation = lazyGet(),
            closeAgentOperation = lazyGet(),
            updateAgentStatusOperation = lazyGet(),
            appendMessageOperation = lazyGet(),
            updateMessageOperation = lazyGet(),
            findMessageOperation = lazyGet(),
            hasProcessingMessageOperation = lazyGet(),
            getContextMemoryOperation = lazyGet(),
            upsertContextMemoryOperation = lazyGet(),
            listMessagesAfterOperation = lazyGet(),
            upsertMessageEmbeddingOperation = lazyGet(),
            searchRelevantContextOperation = lazyGet(),
            getAgentFactsOperation = lazyGet(),
            upsertAgentFactsOperation = lazyGet(),
            createBranchOperation = lazyGet(),
            switchBranchOperation = lazyGet(),
            deleteBranchOperation = lazyGet(),
            getBranchesOperation = lazyGet(),
        )
    }
}

val applicationModule = module {
    single { DomainErrorMapper() }
    single { EstimatePromptTokensUseCase() }
    single { ValidatePromptUseCase(estimatePromptTokensUseCase = get()) }
    single {
        val config = get<AppConfig>()
        ResolveGenerationOptionsUseCase(defaultModel = config.defaultModel)
    }
    single { ResolveAgentModeUseCase() }
    single {
        GenerateTextUseCase(
            repository = get(),
            validatePromptUseCase = get(),
            resolveGenerationOptionsUseCase = get(),
            resolveAgentModeUseCase = get(),
            errorMapper = get(),
        )
    }
    single { GetAgentStateUseCase(repository = get()) }
    single { CreateAgentUseCase(repository = get()) }
    single { SelectAgentUseCase(repository = get()) }
    single { UpdateAgentDraftUseCase(repository = get()) }
    single { CloseAgentUseCase(repository = get()) }
    single { SetAgentStatusUseCase(repository = get()) }
    single { BuildContextPromptUseCase() }
    single { ShouldSummarizeUseCase() }
    single { BuildSummaryPromptUseCase() }
    single { PersistContextSummaryUseCase(repository = get()) }
    single { FullHistoryContextPreparationStrategy() }
    single {
        val config = get<AppConfig>()
        RollingSummaryContextPreparationStrategy(
            repository = get(),
            generateTextUseCase = get(),
            shouldSummarizeUseCase = get(),
            buildSummaryPromptUseCase = get(),
            persistContextSummaryUseCase = get(),
            summaryMaxOutputTokens = config.contextSummaryMaxOutputTokens,
            summaryModelOverride = config.contextSummaryModel,
        )
    }
    single { SlidingWindowContextPreparationStrategy() }
    single { StickyFactsContextPreparationStrategy(repository = get()) }
    single { BranchingContextPreparationStrategy() }
    single { BuildFactsExtractionPromptUseCase() }
    single {
        val config = get<AppConfig>()
        ExtractAndPersistFactsUseCase(
            repository = get(),
            generateTextUseCase = get(),
            buildFactsExtractionPromptUseCase = get(),
            factsModelOverride = config.contextSummaryModel,
        )
    }
    single {
        ContextPreparationStrategyRegistry(
            fullHistoryStrategy = get(),
            rollingSummaryStrategy = get(),
            slidingWindowStrategy = get(),
            stickyFactsStrategy = get(),
            branchingStrategy = get(),
        )
    }
    single { PrepareAgentContextUseCase(strategyRegistry = get()) }
    single {
        val config = get<AppConfig>()
        IndexMessageEmbeddingsUseCase(
            repository = get(),
            llmRepository = get(),
            enabled = config.contextEmbeddingEnabled,
            embeddingModel = config.contextEmbeddingModel,
            chunkSizeChars = config.contextEmbeddingChunkChars,
        )
    }
    single {
        val config = get<AppConfig>()
        RetrieveRelevantContextUseCase(
            repository = get(),
            llmRepository = get(),
            enabled = config.contextEmbeddingEnabled,
            embeddingModel = config.contextEmbeddingModel,
            topK = config.contextRetrievalTopK,
            minScore = config.contextRetrievalMinScore,
        )
    }
    single {
        GetAgentStatsUseCase(
            repository = get(),
            buildContextPromptUseCase = get(),
            estimatePromptTokensUseCase = get(),
            resolveAgentModeUseCase = get(),
        )
    }
    single { BuildAgentStatsSnippetUseCase() }
    single { ParseStatsShortcutTokensUseCase() }
    single { ResolveStatsShortcutsUseCase(getAgentStatsUseCase = get(), buildAgentStatsSnippetUseCase = get()) }
    single {
        ExpandStatsShortcutsInPromptUseCase(
            parseStatsShortcutTokensUseCase = get(),
            resolveStatsShortcutsUseCase = get()
        )
    }
    single {
        StartAgentMessageUseCase(
            repository = get(),
            prepareAgentContextUseCase = get(),
            buildContextPromptUseCase = get(),
            retrieveRelevantContextUseCase = get(),
            indexMessageEmbeddingsUseCase = get(),
            expandStatsShortcutsInPromptUseCase = get(),
            extractAndPersistFactsUseCase = get(),
        )
    }
    single {
        CompleteAgentMessageUseCase(
            repository = get(),
            indexMessageEmbeddingsUseCase = get(),
        )
    }
    single { FailAgentMessageUseCase(repository = get()) }
    single { StopAgentMessageUseCase(repository = get()) }
    single { MapFailureToUserMessageUseCase() }
}

val httpModule = module {
    single { AgentStatsHttpMapper() }

    single { HealthRouterHandler() } bind RouterHandler::class
    single {
        AgentConfigRouterHandler(
            llmRepository = get(),
            modelCatalog = get(),
            config = get(),
        )
    } bind RouterHandler::class
    single { ModelMetadataRouterHandler(llmRepository = get()) } bind RouterHandler::class
    single {
        AgentStatsRouterHandler(
            getAgentStatsUseCase = get(),
            statsMapper = get(),
        )
    } bind RouterHandler::class
    single { AgentWsRouterHandler(wsHandler = lazyGet()) } bind RouterHandler::class
    single { GenerateTextRouterHandler(generateTextUseCase = get()) } bind RouterHandler::class

    single<List<RouterHandler>> {
        getAll<RouterHandler>()
    }
}

val wsModule = module {
    single { AgentWsSessionRegistry() }
    single { AgentWsMapper() }
    single {
        AgentWsOutboundService(
            sessionRegistry = get(),
            mapper = get(),
            json = get(),
            mapFailureToUserMessageUseCase = get()
        )
    }
    single { AgentWsProcessingRegistry() }
    single<org.slf4j.Logger> { LoggerFactory.getLogger(AgentWsMessageHandler::class.java) }

    single<AgentWsMessageUseCase<*>>(named("subscribeAgentsWsUseCase")) {
        SubscribeAgentsWsUseCase(getAgentStateUseCase = get(), outboundService = get())
    }
    single<AgentWsMessageUseCase<*>>(named("createAgentWsUseCase")) {
        CreateAgentWsUseCase(createAgentUseCase = get(), outboundService = get())
    }
    single<AgentWsMessageUseCase<*>>(named("selectAgentWsUseCase")) {
        SelectAgentWsUseCase(selectAgentUseCase = get(), outboundService = get())
    }
    single<AgentWsMessageUseCase<*>>(named("updateAgentDraftWsUseCase")) {
        UpdateAgentDraftWsUseCase(
            updateAgentDraftUseCase = get(),
            outboundService = get()
        )
    }
    single<AgentWsMessageUseCase<*>>(named("closeAgentWsUseCase")) {
        CloseAgentWsUseCase(closeAgentUseCase = get(), outboundService = get())
    }
    single<AgentWsMessageUseCase<*>>(named("sendAgentMessageWsUseCase")) {
        SendAgentMessageWsUseCase(
            processingRegistry = get(),
            startAgentMessageUseCase = get(),
            completeAgentMessageUseCase = get(),
            failAgentMessageUseCase = get(),
            generateTextUseCase = get(),
            parseStatsShortcutTokensUseCase = get(),
            resolveStatsShortcutsUseCase = get(),
            agentRepository = get(),
            outboundService = get(),
        )
    }
    single<AgentWsMessageUseCase<*>>(named("stopAgentMessageWsUseCase")) {
        StopAgentMessageWsUseCase(
            processingRegistry = get(),
            stopAgentMessageUseCase = get(),
            outboundService = get(),
        )
    }
    single<AgentWsMessageUseCase<*>>(named("submitAgentDeprecatedWsUseCase")) {
        SubmitAgentDeprecatedWsUseCase(outboundService = get())
    }
    single<AgentWsMessageUseCase<*>>(named("createBranchWsUseCase")) {
        CreateBranchWsUseCase(agentRepository = get(), outboundService = get())
    }
    single<AgentWsMessageUseCase<*>>(named("switchBranchWsUseCase")) {
        SwitchBranchWsUseCase(agentRepository = get(), outboundService = get())
    }
    single<AgentWsMessageUseCase<*>>(named("deleteBranchWsUseCase")) {
        DeleteBranchWsUseCase(agentRepository = get(), outboundService = get())
    }

    single<Map<KClass<out AgentWsClientMessageDto>, AgentWsMessageUseCase<out AgentWsClientMessageDto>>> {
        @Suppress("UNCHECKED_CAST")
        run {
            val handlers = getAll<AgentWsMessageUseCase<*>>()
            val mapped = handlers.associateBy { it.messageType }
            require(mapped.size == handlers.size) {
                "Duplicate WS message handler registration detected. Ensure one handler per DTO type."
            }
            val expectedTypes = AgentWsClientMessageDto::class.sealedSubclasses.toSet()
            val missingTypes = expectedTypes - mapped.keys
            require(missingTypes.isEmpty()) {
                val missing = missingTypes.joinToString { type -> type.qualifiedName ?: type.toString() }
                "Missing WS message handlers for DTO types: $missing"
            }
            mapped
        }
    }

    single {
        AgentWsMessageHandler(
            getAgentStateUseCase = get(),
            sessionRegistry = get(),
            outboundService = get(),
            handlersByMessageType = get(),
            json = get(),
            logger = get(),
        )
    }
}

fun appModules(config: AppConfig) = listOf(
    module { single { config } },
    networkModule,
    dataModule,
    applicationModule,
    httpModule,
    wsModule,
)
