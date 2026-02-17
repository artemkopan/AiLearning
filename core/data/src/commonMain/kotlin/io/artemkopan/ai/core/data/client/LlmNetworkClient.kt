package io.artemkopan.ai.core.data.client

interface LlmNetworkClient {
    suspend fun generate(request: NetworkGenerateRequest): Result<NetworkGenerateResponse>
}
