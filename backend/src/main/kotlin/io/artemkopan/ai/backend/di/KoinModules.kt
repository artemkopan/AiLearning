package io.artemkopan.ai.backend.di

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.agent.persistence.PostgresAgentRepository
import io.artemkopan.ai.core.application.usecase.BuildConversationPromptUseCase
import io.artemkopan.ai.core.application.usecase.CompleteAgentMessageUseCase
import io.artemkopan.ai.core.application.mapper.DomainErrorMapper
import io.artemkopan.ai.core.application.usecase.CloseAgentUseCase
import io.artemkopan.ai.core.application.usecase.CreateAgentUseCase
import io.artemkopan.ai.core.application.usecase.GenerateTextUseCase
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.core.application.usecase.MapFailureToUserMessageUseCase
import io.artemkopan.ai.core.application.usecase.ResolveAgentModeUseCase
import io.artemkopan.ai.core.application.usecase.ResolveGenerationOptionsUseCase
import io.artemkopan.ai.core.application.usecase.SelectAgentUseCase
import io.artemkopan.ai.core.application.usecase.SetAgentStatusUseCase
import io.artemkopan.ai.core.application.usecase.StartAgentMessageUseCase
import io.artemkopan.ai.core.application.usecase.StopAgentMessageUseCase
import io.artemkopan.ai.core.application.usecase.UpdateAgentDraftUseCase
import io.artemkopan.ai.core.application.usecase.ValidatePromptUseCase
import io.artemkopan.ai.core.data.client.GeminiNetworkClient
import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.data.repository.GeminiLlmRepository
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

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
                logger = object : Logger {
                    override fun log(message: String) = Unit
                }
                level = LogLevel.NONE
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
    single { ValidatePromptUseCase() }
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
    single { BuildConversationPromptUseCase() }
    single { StartAgentMessageUseCase(repository = get(), buildConversationPromptUseCase = get()) }
    single { CompleteAgentMessageUseCase(repository = get()) }
    single { StopAgentMessageUseCase(repository = get()) }
    single { MapFailureToUserMessageUseCase() }
}

fun appModules(config: AppConfig) = listOf(
    module { single { config } },
    networkModule,
    dataModule,
    applicationModule,
)
