package io.artemkopan.ai.core.data.repository

import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.domain.repository.LlmRepository

class GeminiLlmRepository(
    networkClient: LlmNetworkClient,
) : LlmRepository by DefaultLlmRepository(networkClient)
