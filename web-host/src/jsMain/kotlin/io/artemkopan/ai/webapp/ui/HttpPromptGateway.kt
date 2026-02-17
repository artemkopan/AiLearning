package io.artemkopan.ai.webapp.ui

import io.artemkopan.ai.sharedcontract.ErrorResponseDto
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto
import io.artemkopan.ai.sharedui.gateway.PromptGateway
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit

class HttpPromptGateway(
    private val backendBaseUrl: String,
) : PromptGateway {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun generate(request: GenerateRequestDto): Result<GenerateResponseDto> {
        return runCatching {
            val init = RequestInit(
                method = "POST",
                headers = js("({ 'Content-Type': 'application/json' })"),
                body = json.encodeToString(GenerateRequestDto.serializer(), request),
            )
            val response = window.fetch("$backendBaseUrl/api/v1/generate", init).await()
            val bodyText = response.text().await()

            if (!response.ok) {
                val msg = runCatching { json.decodeFromString(ErrorResponseDto.serializer(), bodyText).message }
                    .getOrDefault("Request failed. Please try again.")
                throw RuntimeException(msg)
            }

            json.decodeFromString(GenerateResponseDto.serializer(), bodyText)
        }.recoverCatching { throwable ->
            throw RuntimeException(throwable.message ?: "Request failed. Please try again.")
        }
    }
}
