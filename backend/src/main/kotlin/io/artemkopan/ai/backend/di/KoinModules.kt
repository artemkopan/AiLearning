package io.artemkopan.ai.backend.di

import io.artemkopan.ai.backend.agent.persistence.PostgresAgentRepository
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.core.application.mapper.DomainErrorMapper
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.application.usecase.context.*
import io.artemkopan.ai.core.application.usecase.shortcut.ExpandStatsShortcutsInPromptUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ParseStatsShortcutTokensUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ResolveStatsShortcutsUseCase
import io.artemkopan.ai.core.application.usecase.stats.BuildAgentStatsSnippetUseCase
import io.artemkopan.ai.core.application.usecase.stats.GetAgentStatsUseCase
import io.artemkopan.ai.core.data.client.GeminiNetworkClient
import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.data.repository.GeminiLlmRepository
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.slf4j.LoggerFactory

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
                logger = object : Logger {
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
        GeminiNetworkClient(
            httpClient = get(),
            apiKey = config.geminiApiKey,
        )
    }

    single<LlmRepository> {
        GeminiLlmRepository(get())
    }

    single<AgentRepository> {
        PostgresAgentRepository(config = get())
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
    single { ContextPreparationStrategyRegistry(fullHistoryStrategy = get(), rollingSummaryStrategy = get()) }
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
    single { ExpandStatsShortcutsInPromptUseCase(parseStatsShortcutTokensUseCase = get(), resolveStatsShortcutsUseCase = get()) }
    single {
        StartAgentMessageUseCase(
            repository = get(),
            prepareAgentContextUseCase = get(),
            buildContextPromptUseCase = get(),
            retrieveRelevantContextUseCase = get(),
            indexMessageEmbeddingsUseCase = get(),
            expandStatsShortcutsInPromptUseCase = get(),
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

fun appModules(config: AppConfig) = listOf(
    module { single { config } },
    networkModule,
    dataModule,
    applicationModule,
)
