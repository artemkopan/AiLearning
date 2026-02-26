package io.artemkopan.ai.core.data.repository

import io.artemkopan.ai.core.data.client.LlmNetworkClient
import io.artemkopan.ai.core.data.client.NetworkEmbedRequest
import io.artemkopan.ai.core.data.client.NetworkEmbedResponse
import io.artemkopan.ai.core.data.client.NetworkGenerateRequest
import io.artemkopan.ai.core.data.client.NetworkGenerateResponse
import io.artemkopan.ai.core.data.client.NetworkModelMetadata
import io.artemkopan.ai.core.data.error.DataError
import io.artemkopan.ai.core.domain.error.DomainError
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeminiLlmRepositoryTest {

    @Test
    fun `getModelMetadata maps network metadata to domain metadata`() {
        runBlocking {
            val repository = GeminiLlmRepository(
                networkClient = FakeLlmNetworkClient(
                    modelMetadataResult = Result.success(
                        NetworkModelMetadata(
                            model = "gemini-2.5-flash",
                            provider = "gemini",
                            inputTokenLimit = 1_048_576,
                            outputTokenLimit = 8_192,
                        )
                    )
                )
            )

            val result = repository.getModelMetadata("gemini-2.5-flash")
            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()
            assertEquals("gemini-2.5-flash", metadata.model)
            assertEquals("gemini", metadata.provider)
            assertEquals(1_048_576, metadata.inputTokenLimit)
            assertEquals(8_192, metadata.outputTokenLimit)
        }
    }

    @Test
    fun `getModelMetadata maps rate limit to domain rate limited`() {
        runBlocking {
            val repository = GeminiLlmRepository(
                networkClient = FakeLlmNetworkClient(
                    modelMetadataResult = Result.failure(DataError.RateLimitError("too many requests"))
                )
            )

            val result = repository.getModelMetadata("gemini-2.5-flash")
            assertTrue(result.isFailure)
            assertIs<DomainError.RateLimited>(result.exceptionOrNull())
        }
    }

    @Test
    fun `getModelMetadata maps network error to provider unavailable`() {
        runBlocking {
            val repository = GeminiLlmRepository(
                networkClient = FakeLlmNetworkClient(
                    modelMetadataResult = Result.failure(DataError.NetworkError("provider unavailable"))
                )
            )

            val result = repository.getModelMetadata("gemini-2.5-flash")
            assertTrue(result.isFailure)
            assertIs<DomainError.ProviderUnavailable>(result.exceptionOrNull())
        }
    }

    private class FakeLlmNetworkClient(
        private val modelMetadataResult: Result<NetworkModelMetadata>,
    ) : LlmNetworkClient {
        override suspend fun generate(request: NetworkGenerateRequest): Result<NetworkGenerateResponse> {
            return Result.failure(UnsupportedOperationException("Not used in this test"))
        }

        override suspend fun embed(request: NetworkEmbedRequest): Result<NetworkEmbedResponse> {
            return Result.failure(UnsupportedOperationException("Not used in this test"))
        }

        override suspend fun getModelMetadata(model: String): Result<NetworkModelMetadata> {
            return modelMetadataResult
        }
    }
}
