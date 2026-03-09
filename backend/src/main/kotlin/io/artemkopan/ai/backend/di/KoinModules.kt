package io.artemkopan.ai.backend.di

import io.artemkopan.ai.backend.agent.persistence.PostgresAgentRepository
import io.artemkopan.ai.backend.agent.persistence.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.PostgresTaskRepository
import io.artemkopan.ai.backend.agent.ws.AgentWsMessageHandler
import io.artemkopan.ai.backend.agent.ws.resolver.AgentWsMessageResolver
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.http.HttpClientCurlLogging
import io.artemkopan.ai.backend.http.router.RouterHandler
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.data.client.DeepSeekNetworkClient
import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.data.repository.DefaultLlmRepository
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.artemkopan.ai.core.domain.repository.TaskRepository
import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ksp.generated.module
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

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
            install(HttpClientCurlLogging)
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

    single { PostgresDbRuntime(get()) }
    single<AgentRepository> { PostgresAgentRepository(get(), get()) }
    single<TaskRepository> { PostgresTaskRepository(get(), get()) }
}

val applicationModule = module {
    factory { GetAgentStateUseCase(repository = get()) }
    factory { CreateAgentUseCase(repository = get()) }
    factory { SelectAgentUseCase(repository = get()) }
    factory { CloseAgentUseCase(repository = get()) }
    factory { GenerateTextUseCase(repository = get()) }
    factory { StartAgentMessageUseCase(repository = get()) }
    factory { CompleteAgentMessageUseCase(repository = get()) }
    factory { FailAgentMessageUseCase(repository = get()) }
    factory { StopAgentMessageUseCase(repository = get()) }
    factory { GetActiveTaskUseCase(repository = get()) }
    factory { CreateTaskUseCase(repository = get()) }
    factory { TransitionTaskPhaseUseCase(repository = get()) }
    factory { UpdateTaskUseCase(repository = get()) }
    factory { SetAgentProcessingUseCase(repository = get()) }
    factory { MapFailureToUserMessageUseCase() }
}

val httpModule = module {
    single<List<RouterHandler>> {
        getAll<RouterHandler>()
    }
}

val wsModule = module {
    single<org.slf4j.Logger> { LoggerFactory.getLogger(AgentWsMessageHandler::class.java) }

    single<Map<KClass<out AgentWsClientMessageDto>, AgentWsMessageResolver<out AgentWsClientMessageDto>>> {
        @Suppress("UNCHECKED_CAST")
        run {
            val handlers = getAll<AgentWsMessageResolver<*>>()
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
