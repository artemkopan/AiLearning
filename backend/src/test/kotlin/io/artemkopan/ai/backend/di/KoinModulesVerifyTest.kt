package io.artemkopan.ai.backend.di

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

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
}
