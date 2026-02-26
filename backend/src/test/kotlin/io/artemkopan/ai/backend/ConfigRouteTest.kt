package io.artemkopan.ai.backend

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.http.module
import io.artemkopan.ai.core.domain.model.LlmEmbedding
import io.artemkopan.ai.core.domain.model.LlmGeneration
import io.artemkopan.ai.core.domain.model.LlmGenerationInput
import io.artemkopan.ai.core.domain.model.LlmModelMetadata
import io.artemkopan.ai.core.domain.repository.LlmRepository
import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.ModelMetadataDto
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigRouteTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val testConfig = AppConfig(
        port = 8080,
        geminiApiKey = "test-api-key",
        defaultModel = "gemini-2.5-flash",
        defaultContextWindowTokens = 1_000_000,
        corsOrigin = "localhost:8081",
        dbHost = "localhost",
        dbPort = 5432,
        dbName = "ai_learning_test",
        dbUser = "postgres",
        dbPassword = "postgres",
        dbSsl = false,
    )

    @Test
    fun `config endpoint returns dynamic token limits`() = testApplication {
        application {
            module(
                config = testConfig,
                llmRepositoryOverride = FakeLlmRepository(
                    metadataByModel = mapOf(
                        "gemini-3-flash-preview" to Result.success(
                            LlmModelMetadata(
                                model = "gemini-3-flash-preview",
                                provider = "gemini",
                                inputTokenLimit = 2_000_000,
                                outputTokenLimit = 8_192,
                            )
                        ),
                        "gemini-2.5-flash" to Result.success(
                            LlmModelMetadata(
                                model = "gemini-2.5-flash",
                                provider = "gemini",
                                inputTokenLimit = 1_048_576,
                                outputTokenLimit = 8_192,
                            )
                        ),
                        "gemini-2.5-flash-lite" to Result.success(
                            LlmModelMetadata(
                                model = "gemini-2.5-flash-lite",
                                provider = "gemini",
                                inputTokenLimit = 524_288,
                                outputTokenLimit = 8_192,
                            )
                        ),
                        "gemini-flash-latest" to Result.success(
                            LlmModelMetadata(
                                model = "gemini-flash-latest",
                                provider = "gemini",
                                inputTokenLimit = 750_000,
                                outputTokenLimit = 8_192,
                            )
                        ),
                    )
                ),
            )
        }

        val response = client.get("/api/v1/config")
        assertEquals(200, response.status.value)

        val dto = json.decodeFromString(AgentConfigDto.serializer(), response.bodyAsText())
        assertEquals(2_000_000, dto.models.first { it.id == "gemini-3-flash-preview" }.contextWindowTokens)
        assertEquals(1_048_576, dto.models.first { it.id == "gemini-2.5-flash" }.contextWindowTokens)
        assertEquals(524_288, dto.models.first { it.id == "gemini-2.5-flash-lite" }.contextWindowTokens)
        assertEquals(750_000, dto.models.first { it.id == "gemini-flash-latest" }.contextWindowTokens)
    }

    @Test
    fun `config endpoint falls back to default when one model metadata lookup fails`() = testApplication {
        application {
            module(
                config = testConfig,
                llmRepositoryOverride = FakeLlmRepository(
                    metadataByModel = mapOf(
                        "gemini-3-flash-preview" to Result.success(
                            LlmModelMetadata(
                                model = "gemini-3-flash-preview",
                                provider = "gemini",
                                inputTokenLimit = 2_000_000,
                                outputTokenLimit = 8_192,
                            )
                        ),
                        "gemini-2.5-flash" to Result.failure(IllegalStateException("upstream failed")),
                        "gemini-2.5-flash-lite" to Result.success(
                            LlmModelMetadata(
                                model = "gemini-2.5-flash-lite",
                                provider = "gemini",
                                inputTokenLimit = 524_288,
                                outputTokenLimit = 8_192,
                            )
                        ),
                        "gemini-flash-latest" to Result.success(
                            LlmModelMetadata(
                                model = "gemini-flash-latest",
                                provider = "gemini",
                                inputTokenLimit = 750_000,
                                outputTokenLimit = 8_192,
                            )
                        ),
                    )
                ),
            )
        }

        val response = client.get("/api/v1/config")
        assertEquals(200, response.status.value)

        val dto = json.decodeFromString(AgentConfigDto.serializer(), response.bodyAsText())
        assertEquals(1_000_000, dto.models.first { it.id == "gemini-2.5-flash" }.contextWindowTokens)
        assertEquals(2_000_000, dto.models.first { it.id == "gemini-3-flash-preview" }.contextWindowTokens)
    }

    @Test
    fun `model metadata endpoint returns metadata for selected model`() = testApplication {
        application {
            module(
                config = testConfig,
                llmRepositoryOverride = FakeLlmRepository(
                    metadataByModel = mapOf(
                        "gemini-3-flash-preview" to Result.success(
                            LlmModelMetadata(
                                model = "gemini-3-flash-preview",
                                provider = "gemini",
                                inputTokenLimit = 2_000_000,
                                outputTokenLimit = 8_192,
                            )
                        ),
                    )
                ),
            )
        }

        val response = client.get("/api/v1/models/metadata?model=gemini-3-flash-preview")
        assertEquals(200, response.status.value)

        val dto = json.decodeFromString(ModelMetadataDto.serializer(), response.bodyAsText())
        assertEquals("gemini-3-flash-preview", dto.model)
        assertEquals("gemini", dto.provider)
        assertEquals(2_000_000, dto.inputTokenLimit)
        assertEquals(8_192, dto.outputTokenLimit)
    }

    private class FakeLlmRepository(
        private val metadataByModel: Map<String, Result<LlmModelMetadata>>,
    ) : LlmRepository {
        override suspend fun generate(input: LlmGenerationInput): Result<LlmGeneration> {
            return Result.failure(UnsupportedOperationException("Not used in this test"))
        }

        override suspend fun embed(text: String, model: String): Result<LlmEmbedding> {
            return Result.failure(UnsupportedOperationException("Not used in this test"))
        }

        override suspend fun getModelMetadata(model: String): Result<LlmModelMetadata> {
            return metadataByModel[model] ?: Result.failure(IllegalArgumentException("Missing model: $model"))
        }
    }
}
