package io.artemkopan.ai.webapp.ui

import io.artemkopan.ai.sharedcontract.ErrorResponseDto
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto
import io.artemkopan.ai.sharedui.gateway.PromptGateway
import io.github.aakira.napier.Napier
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import kotlin.js.Date

class HttpPromptGateway(
    private val backendBaseUrl: String,
) : PromptGateway {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun generate(request: GenerateRequestDto): Result<GenerateResponseDto> {
        Napier.d(tag = TAG) { "Starting HTTP request: promptLength=${request.prompt.length}" }
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
                Napier.w(tag = TAG) {
                    "HTTP error: status=${response.status}, code=${errorResponse?.code}, requestId=${errorResponse?.requestId}"
                }
                throw RuntimeException(msg)
            }

            val result = json.decodeFromString(GenerateResponseDto.serializer(), bodyText)
            val latencyMs = (Date.now() - startTime).toLong()
            Napier.i(tag = TAG) {
                "HTTP success: requestId=${result.requestId}, serverLatencyMs=${result.latencyMs}, clientLatencyMs=$latencyMs"
            }
            result
        }.recoverCatching { throwable ->
            val latencyMs = (Date.now() - startTime).toLong()
            Napier.e(tag = TAG, throwable = throwable) { "HTTP request failed after ${latencyMs}ms" }
            throw RuntimeException(throwable.message ?: "Request failed. Please try again.")
        }
    }

    private companion object {
        const val TAG = "HttpPromptGateway"
    }
}
