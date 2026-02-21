package io.artemkopan.ai.webapp.ui

import io.artemkopan.ai.sharedcontract.ChatConfigDto
import io.artemkopan.ai.sharedcontract.ErrorResponseDto
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto
import io.artemkopan.ai.sharedui.gateway.PromptGateway
import co.touchlab.kermit.Logger
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import kotlin.js.Date

class HttpPromptGateway(
    private val backendBaseUrl: String,
) : PromptGateway {

    private val log = Logger.withTag("HttpPromptGateway")

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun getConfig(): Result<ChatConfigDto> {
        log.d { "Fetching chat config" }
        return runCatching {
            val response = window.fetch("$backendBaseUrl/api/v1/config").await()
            val bodyText = response.text().await()

            if (!response.ok) {
                throw RuntimeException("Failed to fetch config: ${response.status}")
            }

            json.decodeFromString(ChatConfigDto.serializer(), bodyText)
        }.onFailure { throwable ->
            log.e(throwable) { "Failed to fetch config" }
        }
    }

    override suspend fun generate(request: GenerateRequestDto): Result<GenerateResponseDto> {
        log.d { "Starting HTTP request: promptLength=${request.prompt.length}" }
        val startTime = Date.now()

        return runCatching {
            val init = RequestInit(
                method = "POST",
                headers = js("({ 'Content-Type': 'application/json' })"),
                body = json.encodeToString(GenerateRequestDto.serializer(), request),
            )
            val response = window.fetch("$backendBaseUrl/api/v1/generate", init).await()
            val bodyText = response.text().await()

            if (!response.ok) {
                val errorResponse = runCatching { json.decodeFromString(ErrorResponseDto.serializer(), bodyText) }
                    .getOrNull()
                val msg = errorResponse?.message ?: "Request failed. Please try again."
                log.w {
                    "HTTP error: status=${response.status}, code=${errorResponse?.code}, requestId=${errorResponse?.requestId}"
                }
                throw RuntimeException(msg)
            }

            val result = json.decodeFromString(GenerateResponseDto.serializer(), bodyText)
            val latencyMs = (Date.now() - startTime).toLong()
            log.i {
                "HTTP success: requestId=${result.requestId}, serverLatencyMs=${result.latencyMs}, clientLatencyMs=$latencyMs"
            }
            result
        }.recoverCatching { throwable ->
            val latencyMs = (Date.now() - startTime).toLong()
            log.e(throwable) { "HTTP request failed after ${latencyMs}ms" }
            throw RuntimeException(throwable.message ?: "Request failed. Please try again.")
        }
    }
}
