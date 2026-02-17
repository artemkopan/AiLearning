package io.artemkopan.ai.backend.di

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.core.application.usecase.GenerateTextUseCase
import io.artemkopan.ai.core.application.usecase.MapFailureToUserMessageUseCase
import io.artemkopan.ai.core.application.usecase.ResolveGenerationOptionsUseCase
import io.artemkopan.ai.core.application.usecase.ValidatePromptUseCase
import io.artemkopan.ai.core.data.client.GeminiNetworkClient
import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.data.repository.GeminiLlmRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AppContainer(config: AppConfig) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = Unit
            }
            level = LogLevel.NONE
        }
    }

    private val llmNetworkClient: LlmNetworkClient = GeminiNetworkClient(
        httpClient = httpClient,
        apiKey = config.geminiApiKey,
    )

    private val repository: LlmRepository = GeminiLlmRepository(llmNetworkClient)

    private val validatePromptUseCase = ValidatePromptUseCase()
    private val resolveGenerationOptionsUseCase = ResolveGenerationOptionsUseCase(defaultModel = config.defaultModel)

    val generateTextUseCase = GenerateTextUseCase(
        repository = repository,
        validatePromptUseCase = validatePromptUseCase,
        resolveGenerationOptionsUseCase = resolveGenerationOptionsUseCase,
    )

    val mapFailureToUserMessageUseCase = MapFailureToUserMessageUseCase()
}
