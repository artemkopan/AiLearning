package io.artemkopan.ai.backend.http.router

import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.artemkopan.ai.sharedcontract.ModelMetadataDto
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.annotation.Single

@Single(binds = [RouterHandler::class])
class ModelMetadataRouterHandler(
    private val llmRepository: LlmRepository,
) : RouterHandler {
    override fun Routing.invoke() {
        get("/api/v1/models/metadata") {
            val modelId = call.request.queryParameters["model"]?.trim().orEmpty()
            if (modelId.isEmpty()) {
                throw IllegalArgumentException("Model query parameter is required.")
            }

            val metadata = llmRepository.getModelMetadata(modelId).getOrElse { throwable ->
                throw throwable
            }
            call.respond(
                HttpStatusCode.OK,
                ModelMetadataDto(
                    model = metadata.model,
                    provider = metadata.provider,
                    inputTokenLimit = metadata.inputTokenLimit,
                    outputTokenLimit = metadata.outputTokenLimit,
                )
            )
        }
    }
}
