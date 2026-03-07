package io.artemkopan.ai.backend.di

import co.touchlab.kermit.LoggerConfig
import io.artemkopan.ai.backend.agent.ws.AgentWsMessageHandler
import io.artemkopan.ai.backend.agent.ws.usecase.AgentWsMessageUseCase
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(KoinExperimentalAPI::class)
class KoinModulesVerifyTest {

    private val testConfig = AppConfig(
        port = 8080,
        geminiApiKey = "test-api-key",
        defaultModel = "gemini-2.5-flash",
        corsOrigin = "localhost:8081",
        dbHost = "localhost",
        dbPort = 5432,
        dbName = "ai_learning_test",
        dbUser = "postgres",
        dbPassword = "postgres",
        dbSsl = false,
    )

    @Test
    fun `verify networkModule`() {
        networkModule.verify(
            extraTypes = listOf(
                HttpClientEngine::class,
                HttpClientConfig::class,
            )
        )
    }

    @Test
    fun `verify dataModule`() {
        dataModule.verify(
            extraTypes = listOf(
                AppConfig::class,
                HttpClient::class,
                Json::class,
                Lazy::class,
                LoggerConfig::class,
            )
        )
    }

    @Test
    fun `verify applicationModule`() {
        applicationModule.verify(
            extraTypes = listOf(
                AppConfig::class,
                LlmRepository::class,
                AgentRepository::class,
                io.artemkopan.ai.core.domain.repository.UserProfileRepository::class,
                io.artemkopan.ai.core.domain.repository.TaskRepository::class,
                Boolean::class,
            )
        )
    }

    @Test
    fun `verify all modules together`() {
        val allModules = module {
            includes(appModules(testConfig))
        }
        allModules.verify(
            extraTypes = listOf(
                HttpClientEngine::class,
                HttpClientConfig::class,
                Boolean::class,
                Lazy::class,
                LoggerConfig::class,
            )
        )
    }

    @Test
    fun `koin application starts successfully`() {
        val koinApp = koinApplication {
            modules(appModules(testConfig))
        }

        // Verify all definitions can be instantiated
        koinApp.koin.getAll<Any>()

        koinApp.close()
    }

    @Test
    fun `ws handler bindings cover all client dto types`() {
        val koinApp = koinApplication {
            modules(appModules(testConfig))
        }

        try {
            val expectedTypes = AgentWsClientMessageDto::class.sealedSubclasses.toSet()
            val handlers = koinApp.koin.getAll<AgentWsMessageUseCase<*>>()
            assertEquals(expectedTypes.size, handlers.size)

            val dispatchMap: Map<KClass<out AgentWsClientMessageDto>, AgentWsMessageUseCase<out AgentWsClientMessageDto>> =
                koinApp.koin.get()
            assertEquals(expectedTypes, dispatchMap.keys.toSet())

            koinApp.koin.get<AgentWsMessageHandler>()
        } finally {
            koinApp.close()
        }
    }
}
