package io.artemkopan.ai.backend.di

import io.artemkopan.ai.backend.agent.ws.AgentWsMessageHandler
import io.artemkopan.ai.backend.agent.ws.usecase.AgentWsMessageUseCase
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.http.router.RouterHandler
import io.artemkopan.ai.core.application.mapper.DomainErrorMapper
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.application.usecase.context.*
import io.artemkopan.ai.core.application.usecase.shortcut.ExpandStatsShortcutsInPromptUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ParseMemoryLayerShortcutTokenUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ParseStatsShortcutTokensUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ResolveStatsShortcutsUseCase
import io.artemkopan.ai.core.application.usecase.stats.BuildAgentStatsSnippetUseCase
import io.artemkopan.ai.core.application.usecase.stats.GetAgentStatsUseCase
import io.artemkopan.ai.core.data.client.DeepSeekNetworkClient
import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.data.repository.DefaultLlmRepository
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
import org.koin.dsl.module
import org.koin.ksp.generated.module
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import co.touchlab.kermit.Logger as KermitLogger

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

    single<KermitLogger> { KermitLogger.withTag("PostgresAgentRepository") }
}

val applicationModule = module {
    single { DomainErrorMapper() }
    factory { EstimatePromptTokensUseCase() }
    factory { ValidatePromptUseCase(estimatePromptTokensUseCase = get()) }
    factory {
        val config = get<AppConfig>()
        ResolveGenerationOptionsUseCase(defaultModel = config.defaultModel)
    }
    factory { ResolveAgentModeUseCase() }
    factory {
        GenerateTextUseCase(
            repository = get(),
            validatePromptUseCase = get(),
            resolveGenerationOptionsUseCase = get(),
            resolveAgentModeUseCase = get(),
            errorMapper = get(),
        )
    }
    factory { GetAgentStateUseCase(repository = get()) }
    factory { CreateAgentUseCase(repository = get()) }
    factory { SelectAgentUseCase(repository = get()) }
    factory { UpdateAgentDraftUseCase(repository = get()) }
    factory { CloseAgentUseCase(repository = get()) }
    factory { SetAgentStatusUseCase(repository = get()) }
    factory { BuildContextPromptUseCase() }
    factory { ShouldSummarizeUseCase() }
    factory { BuildSummaryPromptUseCase() }
    factory { PersistContextSummaryUseCase(repository = get()) }
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
    single {
        val config = get<AppConfig>()
        StickyFactsContextPreparationStrategy(
            repository = get(),
            generateTextUseCase = get(),
            shouldSummarizeUseCase = get(),
            buildSummaryPromptUseCase = get(),
            persistContextSummaryUseCase = get(),
            summaryMaxOutputTokens = config.contextSummaryMaxOutputTokens,
            summaryModelOverride = config.contextSummaryModel,
        )
    }
    single { BranchingContextPreparationStrategy() }
    factory { BuildFactsExtractionPromptUseCase() }
    factory {
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
    factory { PrepareAgentContextUseCase(strategyRegistry = get()) }
    factory {
        val config = get<AppConfig>()
        IndexMessageEmbeddingsUseCase(
            repository = get(),
            llmRepository = get(),
            enabled = config.contextEmbeddingEnabled,
            embeddingModel = config.contextEmbeddingModel,
            chunkSizeChars = config.contextEmbeddingChunkChars,
        )
    }
    factory {
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
    factory {
        GetAgentStatsUseCase(
            repository = get(),
            buildContextPromptUseCase = get(),
            estimatePromptTokensUseCase = get(),
            resolveAgentModeUseCase = get(),
        )
    }
    factory { BuildAgentStatsSnippetUseCase() }
    factory { ParseStatsShortcutTokensUseCase() }
    factory { ParseMemoryLayerShortcutTokenUseCase() }
    factory { ResolveStatsShortcutsUseCase(getAgentStatsUseCase = get(), buildAgentStatsSnippetUseCase = get()) }
    factory {
        ExpandStatsShortcutsInPromptUseCase(
            parseStatsShortcutTokensUseCase = get(),
            resolveStatsShortcutsUseCase = get(),
        )
    }
    factory {
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
    factory {
        CompleteAgentMessageUseCase(
            repository = get(),
            indexMessageEmbeddingsUseCase = get(),
        )
    }
    factory { FailAgentMessageUseCase(repository = get()) }
    factory { StopAgentMessageUseCase(repository = get()) }
    factory { SwitchAgentMemoryLayerUseCase(repository = get()) }
    factory { MapFailureToUserMessageUseCase() }
}

val httpModule = module {
    single<List<RouterHandler>> {
        getAll<RouterHandler>()
    }
}

val wsModule = module {
    single<org.slf4j.Logger> { LoggerFactory.getLogger(AgentWsMessageHandler::class.java) }

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
}

fun appModules(config: AppConfig) = listOf(
    module { single { config } },
    networkModule,
    dataModule,
    applicationModule,
    httpModule,
    wsModule,
    BackendScanModule().module,
)
