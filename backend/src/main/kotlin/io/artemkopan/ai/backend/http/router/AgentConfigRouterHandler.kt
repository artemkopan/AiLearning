package io.artemkopan.ai.backend.http.router

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.provider.LlmModelCatalog
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.ModelOptionDto
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class AgentConfigRouterHandler(
    private val llmRepository: LlmRepository,
    private val modelCatalog: LlmModelCatalog,
    private val config: AppConfig,
) : RouterHandler {
    private val logger = LoggerFactory.getLogger(AgentConfigRouterHandler::class.java)

    override fun Routing.invoke() {
        get("/api/v1/config") {
            val models = resolveConfiguredModels(
                repository = llmRepository,
                modelCatalog = modelCatalog,
                fallbackContextWindowTokens = config.defaultContextWindowTokens,
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
    }

    private suspend fun resolveConfiguredModels(
        repository: LlmRepository,
        modelCatalog: LlmModelCatalog,
        fallbackContextWindowTokens: Int,
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
}
