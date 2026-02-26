package io.artemkopan.ai.core.data.client

interface LlmNetworkClient {
    suspend fun generate(request: NetworkGenerateRequest): Result<NetworkGenerateResponse>
    suspend fun embed(request: NetworkEmbedRequest): Result<NetworkEmbedResponse>
    suspend fun getModelMetadata(model: String): Result<NetworkModelMetadata>
}
