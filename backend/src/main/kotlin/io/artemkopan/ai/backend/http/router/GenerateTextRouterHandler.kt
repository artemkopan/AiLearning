package io.artemkopan.ai.backend.http.router

import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.usecase.GenerateTextUseCase
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto
import io.artemkopan.ai.sharedcontract.TokenUsageDto
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

@Single(binds = [RouterHandler::class])
class GenerateTextRouterHandler(
    private val generateTextUseCase: GenerateTextUseCase,
) : RouterHandler {
    private val logger = LoggerFactory.getLogger(GenerateTextRouterHandler::class.java)

    override fun Routing.invoke() {
        post("/api/v1/generate") {
            val requestId = call.ensureRequestId()
            val startedAt = System.currentTimeMillis()
            val payload = call.receive<GenerateRequestDto>()
            logger.info(
                "POST /api/v1/generate requestId={} body: prompt='{}', model={}, temperature={}, maxOutputTokens={}, stopSequences={}",
                requestId,
                payload.prompt,
                payload.model,
                payload.temperature,
                payload.maxOutputTokens,
                payload.stopSequences,
            )

            val result = generateTextUseCase.execute(
                GenerateCommand(
                    prompt = payload.prompt,
                    model = payload.model ?: "deepseek-chat",
                    temperature = payload.temperature ?: 0.7,
                    maxOutputTokens = payload.maxOutputTokens,
                    stopSequences = payload.stopSequences,
                )
            )

            result.fold(
                onSuccess = { output ->
                    val latencyMs = System.currentTimeMillis() - startedAt
                    logger.info(
                        "POST /api/v1/generate success requestId={} latencyMs={} response: provider={}, model={}, tokens(in={}, out={}, total={}), text='{}'",
                        requestId,
                        latencyMs,
                        output.provider,
                        output.model,
                        output.usage?.inputTokens,
                        output.usage?.outputTokens,
                        output.usage?.totalTokens,
                        output.text,
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        GenerateResponseDto(
                            text = output.text,
                            provider = output.provider,
                            model = output.model,
                            usage = output.usage?.let {
                                TokenUsageDto(
                                    inputTokens = it.inputTokens,
                                    outputTokens = it.outputTokens,
                                    totalTokens = it.totalTokens,
                                )
                            },
                            requestId = requestId,
                            latencyMs = latencyMs,
                        )
                    )
                },
                onFailure = { throwable ->
                    logger.error("POST /api/v1/generate failed requestId={}", requestId, throwable)
                    throw throwable
                }
            )
        }
    }
}
