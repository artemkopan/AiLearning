package io.artemkopan.ai.backend.di

import io.artemkopan.ai.backend.config.AppConfig
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

@OptIn(KoinExperimentalAPI::class)
class KoinModulesVerifyTest {

    private val testConfig = AppConfig(
        port = 8080,
        projectsRoot = "/tmp/projects",
        corsOrigin = "localhost:8081",
    )

    @Test
    fun `verify terminalModule`() {
        terminalModule.verify(
            extraTypes = listOf(
                AppConfig::class,
            )
        )
    }

    @Test
    fun `verify all modules together`() {
        val allModules = module {
            includes(appModules(testConfig))
        }
        allModules.verify()
    }

    @Test
    fun `koin application starts successfully`() {
        val koinApp = koinApplication {
            modules(appModules(testConfig))
        }
        koinApp.koin.getAll<Any>()
        koinApp.close()
    }
}
