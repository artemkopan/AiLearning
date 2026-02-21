package io.artemkopan.ai.core.data.client

import io.artemkopan.ai.core.data.error.DataError
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class GeminiNetworkClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) : LlmNetworkClient {

    private val log = Logger.withTag("GeminiNetworkClient")

    override suspend fun generate(request: NetworkGenerateRequest): Result<NetworkGenerateResponse> {
        if (apiKey.isBlank()) {
            log.e { "API key is missing" }
            return Result.failure(DataError.AuthenticationError("Missing GEMINI_API_KEY configuration."))
        }

        log.d { "Starting generation request: model=${request.model}, promptLength=${request.prompt.length}" }
        val startTime = currentTimeMillis()

        return runCatching {
            val response: GeminiGenerateResponse = httpClient.post(
                "$baseUrl/models/${request.model}:generateContent"
            ) {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiGenerateRequest(
                        contents = listOf(
                            GeminiContent(parts = listOf(GeminiPart(text = request.prompt)))
                        ),
                        generationConfig = GeminiGenerationConfig(
                            temperature = request.temperature,
                            maxOutputTokens = request.maxOutputTokens,
                            stopSequences = request.stopSequences,
                        ),
                        systemInstruction = request.systemInstruction?.let {
                            GeminiContent(parts = listOf(GeminiPart(text = it)))
                        },
                    )
                )
            }.body()

            val text = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim()
                .orEmpty()

            if (text.isBlank()) {
                log.w { "Provider returned empty response" }
                throw DataError.EmptyResponseError("Provider returned an empty response.")
            }

            val latencyMs = currentTimeMillis() - startTime
            val usage = response.usageMetadata
            log.i {
                "Generation completed: model=${request.model}, latencyMs=$latencyMs, " +
                    "inputTokens=${usage?.promptTokenCount ?: 0}, outputTokens=${usage?.candidatesTokenCount ?: 0}"
            }

            NetworkGenerateResponse(
                text = text,
                provider = "gemini",
                model = request.model,
                usage = usage?.let {
                    NetworkTokenUsage(
                        inputTokens = it.promptTokenCount ?: 0,
                        outputTokens = it.candidatesTokenCount ?: 0,
                        totalTokens = it.totalTokenCount ?: 0,
                    )
                },
            )
        }.recoverCatching { throwable ->
            val latencyMs = currentTimeMillis() - startTime
            val mappedError = when (throwable) {
                is DataError -> throwable
                is ClientRequestException -> when (throwable.response.status) {
                    HttpStatusCode.TooManyRequests -> DataError.RateLimitError("Provider rate limit exceeded.", throwable)
                    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                        DataError.AuthenticationError("Provider authentication failed.", throwable)
                    else -> DataError.NetworkError("Provider call failed: ${throwable.response.status}.", throwable)
                }
                is ServerResponseException -> DataError.NetworkError("Provider server error: ${throwable.response.status}.", throwable)
                else -> DataError.NetworkError("Provider call failed.", throwable)
            }
            log.e(throwable) {
                "Generation failed: model=${request.model}, latencyMs=$latencyMs, error=${mappedError.message}"
            }
            throw mappedError
        }
    }
}

internal expect fun currentTimeMillis(): Long

@Serializable
private data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
    val systemInstruction: GeminiContent? = null,
)

@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String,
)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
)

@Serializable
private data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsageMetadata? = null,
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiCandidateContent? = null,
)

@Serializable
private data class GeminiCandidateContent(
    val parts: List<GeminiCandidatePart>? = null,
)

@Serializable
private data class GeminiCandidatePart(
    val text: String? = null,
)

@Serializable
private data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
)
