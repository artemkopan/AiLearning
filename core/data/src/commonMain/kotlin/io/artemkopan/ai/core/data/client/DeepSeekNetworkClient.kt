package io.artemkopan.ai.core.data.client

import co.touchlab.kermit.Logger
import io.artemkopan.ai.core.data.error.DataError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DeepSeekNetworkClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.deepseek.com",
) : LlmNetworkClient {
    private val log = Logger.withTag("DeepSeekNetworkClient")

    override suspend fun generate(request: NetworkGenerateRequest): Result<NetworkGenerateResponse> {
        if (apiKey.isBlank()) {
            return missingApiKeyResult()
        }

        val resolvedModel = resolveModelId(request.model)
        log.d { "Starting generation request: model=$resolvedModel, promptLength=${request.prompt.length}" }
        val startTime = currentTimeMillis()

        return runCatching {
            val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"
            val requestBody = DeepSeekChatCompletionsRequest(
                model = resolvedModel,
                messages = buildMessages(
                    prompt = request.prompt,
                    systemInstruction = request.systemInstruction,
                ),
                temperature = request.temperature,
                maxTokens = request.maxOutputTokens,
                stop = request.stopSequences?.takeIf { it.isNotEmpty() },
            )

            val response: DeepSeekChatCompletionsResponse = httpClient.post(endpoint) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val text = response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()
                .orEmpty()

            if (text.isBlank()) {
                log.w { "Provider returned empty response: model=$resolvedModel" }
                throw DataError.EmptyResponseError("Provider returned an empty response.")
            }

            val usage = response.usage
            val latencyMs = currentTimeMillis() - startTime
            log.i {
                "Generation completed: model=$resolvedModel, latencyMs=$latencyMs, " +
                    "inputTokens=${usage?.promptTokens ?: 0}, outputTokens=${usage?.completionTokens ?: 0}"
            }

            NetworkGenerateResponse(
                text = text,
                provider = ProviderName,
                model = response.model?.trim().orEmpty().ifBlank { resolvedModel },
                usage = usage?.let {
                    NetworkTokenUsage(
                        inputTokens = it.promptTokens ?: 0,
                        outputTokens = it.completionTokens ?: 0,
                        totalTokens = it.totalTokens ?: 0,
                    )
                },
            )
        }.recoverCatching { throwable ->
            val latencyMs = currentTimeMillis() - startTime
            val mappedError = mapProviderError(
                throwable = throwable,
                notFoundMessage = "Model '$resolvedModel' was not found.",
            )
            log.e(throwable) {
                "Generation failed: model=$resolvedModel, latencyMs=$latencyMs, error=${mappedError.message}"
            }
            throw mappedError
        }
    }

    override suspend fun embed(request: NetworkEmbedRequest): Result<NetworkEmbedResponse> {
        return Result.failure(
            DataError.NetworkError(
                "Embeddings are not supported by DeepSeek in this build. " +
                    "Set CONTEXT_EMBEDDING_ENABLED=false."
            )
        )
    }

    override suspend fun getModelMetadata(model: String): Result<NetworkModelMetadata> {
        if (apiKey.isBlank()) {
            return missingApiKeyResult()
        }

        val resolvedModel = resolveModelId(model)
        if (resolvedModel.isBlank()) {
            return Result.failure(DataError.EmptyResponseError("Model id must not be blank."))
        }

        val staticMetadata = StaticModelMetadata[resolvedModel]
            ?: return Result.failure(DataError.NetworkError("Model '$resolvedModel' was not found."))

        return Result.success(
            NetworkModelMetadata(
                model = resolvedModel,
                provider = ProviderName,
                inputTokenLimit = staticMetadata.inputTokenLimit,
                outputTokenLimit = staticMetadata.outputTokenLimit,
            )
        )
    }

    private fun resolveModelId(rawModel: String): String {
        val trimmed = rawModel.trim()
        if (trimmed.isEmpty()) return DefaultModel

        val lowered = trimmed.lowercase()
        if (lowered.startsWith("gemini") || lowered.startsWith("models/gemini")) {
            return DefaultModel
        }

        return trimmed
    }

    private fun buildMessages(prompt: String, systemInstruction: String?): List<DeepSeekMessage> {
        val messages = mutableListOf<DeepSeekMessage>()
        val normalizedSystemInstruction = systemInstruction?.trim().orEmpty()
        if (normalizedSystemInstruction.isNotEmpty()) {
            messages += DeepSeekMessage(
                role = "system",
                content = normalizedSystemInstruction,
            )
        }
        messages += DeepSeekMessage(
            role = "user",
            content = prompt,
        )
        return messages
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
        return Result.failure(
            DataError.AuthenticationError(
                "Missing DEEPSEEK_API_KEY configuration. " +
                    "Legacy fallback GEMINI_API_KEY is also supported."
            )
        )
    }

    private data class StaticMetadata(
        val inputTokenLimit: Int,
        val outputTokenLimit: Int,
    )

    companion object {
        private const val ProviderName = "deepseek"
        private const val DefaultModel = "deepseek-chat"

        private val StaticModelMetadata = mapOf(
            "deepseek-chat" to StaticMetadata(
                inputTokenLimit = 64_000,
                outputTokenLimit = 8_192,
            ),
            "deepseek-reasoner" to StaticMetadata(
                inputTokenLimit = 64_000,
                outputTokenLimit = 8_192,
            ),
        )
    }
}

@Serializable
private data class DeepSeekChatCompletionsRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
)

@Serializable
private data class DeepSeekMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class DeepSeekChatCompletionsResponse(
    val model: String? = null,
    val choices: List<DeepSeekChoice>? = null,
    val usage: DeepSeekUsage? = null,
)

@Serializable
private data class DeepSeekChoice(
    val message: DeepSeekAssistantMessage? = null,
)

@Serializable
private data class DeepSeekAssistantMessage(
    val content: String? = null,
)

@Serializable
private data class DeepSeekUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
)
