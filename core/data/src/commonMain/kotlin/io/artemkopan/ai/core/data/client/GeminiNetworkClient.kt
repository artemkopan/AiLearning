package io.artemkopan.ai.core.data.client

import io.artemkopan.ai.core.domain.error.DomainError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GeminiNetworkClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) : LlmNetworkClient {

    override suspend fun generate(request: NetworkGenerateRequest): Result<NetworkGenerateResponse> {
        if (apiKey.isBlank()) {
            return Result.failure(DomainError.ProviderUnavailable("Missing GEMINI_API_KEY configuration."))
        }

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
                        generationConfig = GeminiGenerationConfig(temperature = request.temperature),
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
                throw DomainError.ProviderUnavailable("Provider returned an empty response.")
            }

            NetworkGenerateResponse(
                text = text,
                provider = "gemini",
                model = request.model,
                usage = response.usageMetadata?.let {
                    NetworkTokenUsage(
                        inputTokens = it.promptTokenCount ?: 0,
                        outputTokens = it.candidatesTokenCount ?: 0,
                        totalTokens = it.totalTokenCount ?: 0,
                    )
                },
            )
        }.recoverCatching { throwable ->
            throw when {
                throwable is DomainError -> throwable
                throwable.message?.contains("429") == true -> DomainError.RateLimited("Provider rate limit exceeded.", throwable)
                throwable.message?.contains("401") == true || throwable.message?.contains("403") == true ->
                    DomainError.ProviderUnavailable("Provider authentication failed.", throwable)
                else -> DomainError.ProviderUnavailable("Provider call failed.", throwable)
            }
        }
    }
}

@Serializable
private data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
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
    @SerialName("promptTokenCount")
    val promptTokenCount: Int? = null,
    @SerialName("candidatesTokenCount")
    val candidatesTokenCount: Int? = null,
    @SerialName("totalTokenCount")
    val totalTokenCount: Int? = null,
)
