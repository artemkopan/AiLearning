package io.artemkopan.ai.core.data.client

import io.artemkopan.ai.core.data.error.DataError
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GeminiNetworkClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) : LlmNetworkClient {
    companion object {
        private const val ProviderApiKeyHeader = "x-goog-api-key"
    }

    private val log = Logger.withTag("GeminiNetworkClient")
    private val requestJson = Json {
        explicitNulls = false
        encodeDefaults = false
    }
    private val responseJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun generate(request: NetworkGenerateRequest): Result<NetworkGenerateResponse> {
        if (apiKey.isBlank()) {
            return missingApiKeyResult()
        }

        log.d { "Starting generation request: model=${request.model}, promptLength=${request.prompt.length}" }
        val startTime = currentTimeMillis()

        return runCatching {
            val endpoint = "$baseUrl/models/${request.model}:generateContent"
            val requestBody = GeminiGenerateRequest(
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
            val response: GeminiGenerateResponse = httpClient.post(endpoint) {
                header(ProviderApiKeyHeader, apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
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
            val mappedError = mapProviderError(
                throwable = throwable,
                notFoundMessage = "Model '${request.model}' was not found.",
            )
            log.e(throwable) {
                "Generation failed: model=${request.model}, latencyMs=$latencyMs, error=${mappedError.message}"
            }
            throw mappedError
        }
    }

    override suspend fun embed(request: NetworkEmbedRequest): Result<NetworkEmbedResponse> {
        if (apiKey.isBlank()) {
            return missingApiKeyResult()
        }

        val text = request.text.trim()
        if (text.isEmpty()) {
            return Result.failure(DataError.EmptyResponseError("Embedding input is blank."))
        }

        log.d { "Starting embedding request: model=${request.model}, textLength=${text.length}" }
        val startTime = currentTimeMillis()

        return runCatching {
            val endpoint = "$baseUrl/models/${request.model}:embedContent"
            val requestBody = GeminiEmbedRequest(
                content = GeminiContent(parts = listOf(GeminiPart(text = text))),
            )

            val httpResponse = httpClient.post(endpoint) {
                header(ProviderApiKeyHeader, apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val rawBody = httpResponse.bodyAsText()
            val response = responseJson.decodeFromString(GeminiEmbedResponse.serializer(), rawBody)

            val values = response.embedding?.values.orEmpty()
                .ifEmpty { response.embeddings?.firstOrNull()?.values.orEmpty() }
            if (values.isEmpty()) {
                log.w {
                    "Embedding response had no vector values: model=${request.model}, body=${rawBody.take(500)}"
                }
                throw DataError.EmptyResponseError("Provider returned an empty embedding.")
            }

            val latencyMs = currentTimeMillis() - startTime
            log.i {
                "Embedding completed: model=${request.model}, latencyMs=$latencyMs, vectorSize=${values.size}"
            }

            NetworkEmbedResponse(
                values = values,
                provider = "gemini",
                model = request.model,
            )
        }.recoverCatching { throwable ->
            val latencyMs = currentTimeMillis() - startTime
            val mappedError = mapProviderError(
                throwable = throwable,
                notFoundMessage = "Embedding model '${request.model}' is not available for Gemini API v1beta embedContent. " +
                    "Set CONTEXT_EMBEDDING_MODEL=gemini-embedding-001 or another supported model.",
            )
            log.e(throwable) {
                "Embedding failed: model=${request.model}, latencyMs=$latencyMs, error=${mappedError.message}"
            }
            throw mappedError
        }
    }

    override suspend fun getModelMetadata(model: String): Result<NetworkModelMetadata> {
        if (apiKey.isBlank()) {
            return missingApiKeyResult()
        }

        val normalizedModel = model.trim()
        if (normalizedModel.isEmpty()) {
            return Result.failure(DataError.EmptyResponseError("Model id must not be blank."))
        }

        log.d { "Starting model metadata request: model=$normalizedModel" }
        val startTime = currentTimeMillis()

        return runCatching {
            val endpoint = "$baseUrl/models/$normalizedModel"
            val response: GeminiModelResponse = httpClient.get(endpoint) {
                header(ProviderApiKeyHeader, apiKey)
            }.body()

            val inputLimit = response.inputTokenLimit ?: 0
            if (inputLimit <= 0) {
                throw DataError.EmptyResponseError(
                    "Model '$normalizedModel' returned missing or invalid inputTokenLimit."
                )
            }
            val outputLimit = response.outputTokenLimit ?: 0
            val latencyMs = currentTimeMillis() - startTime
            log.i {
                "Model metadata completed: model=$normalizedModel, latencyMs=$latencyMs, " +
                    "inputTokenLimit=$inputLimit, outputTokenLimit=$outputLimit"
            }

            NetworkModelMetadata(
                model = response.name?.substringAfter("models/").orEmpty().ifBlank { normalizedModel },
                provider = "gemini",
                inputTokenLimit = inputLimit,
                outputTokenLimit = outputLimit,
            )
        }.recoverCatching { throwable ->
            val latencyMs = currentTimeMillis() - startTime
            val mappedError = mapProviderError(
                throwable = throwable,
                notFoundMessage = "Model '$normalizedModel' was not found.",
            )
            log.e(throwable) {
                "Model metadata failed: model=$normalizedModel, latencyMs=$latencyMs, error=${mappedError.message}"
            }
            throw mappedError
        }
    }

    private fun mapProviderError(throwable: Throwable, notFoundMessage: String): DataError = when (throwable) {
        is DataError -> throwable
        is ClientRequestException -> when (throwable.response.status) {
            HttpStatusCode.TooManyRequests -> DataError.RateLimitError("Provider rate limit exceeded.", throwable)
            HttpStatusCode.NotFound -> DataError.NetworkError(notFoundMessage, throwable)
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                DataError.AuthenticationError("Provider authentication failed.", throwable)
            else -> DataError.NetworkError("Provider call failed: ${throwable.response.status}.", throwable)
        }
        is ServerResponseException -> DataError.NetworkError("Provider server error: ${throwable.response.status}.", throwable)
        else -> DataError.NetworkError("Provider call failed.", throwable)
    }

    private fun <T> missingApiKeyResult(): Result<T> {
        log.e { "API key is missing" }
        return Result.failure(DataError.AuthenticationError("Missing GEMINI_API_KEY configuration."))
    }

    private fun shQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
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

@Serializable
private data class GeminiEmbedRequest(
    val content: GeminiContent,
)

@Serializable
private data class GeminiEmbedResponse(
    val embedding: GeminiEmbedding? = null,
    val embeddings: List<GeminiEmbedding>? = null,
)

@Serializable
private data class GeminiEmbedding(
    val values: List<Double> = emptyList(),
)

@Serializable
private data class GeminiModelResponse(
    val name: String? = null,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
)
