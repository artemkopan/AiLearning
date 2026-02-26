package io.artemkopan.ai.core.data.client

import io.artemkopan.ai.core.data.error.DataError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeminiNetworkClientTest {

    @Test
    fun `getModelMetadata parses token limits`() {
        runBlocking {
            val httpClient = mockHttpClient(
                expectedModel = "gemini-2.5-flash",
                status = HttpStatusCode.OK,
                body = """
                    {
                      "name": "models/gemini-2.5-flash",
                      "inputTokenLimit": 1048576,
                      "outputTokenLimit": 8192
                    }
                """.trimIndent(),
            )
            val client = GeminiNetworkClient(
                httpClient = httpClient,
                apiKey = "test-key",
                baseUrl = "https://example.test/v1beta",
            )

            val result = client.getModelMetadata("gemini-2.5-flash")
            assertTrue(result.isSuccess)

            val metadata = result.getOrThrow()
            assertEquals("gemini-2.5-flash", metadata.model)
            assertEquals("gemini", metadata.provider)
            assertEquals(1_048_576, metadata.inputTokenLimit)
            assertEquals(8_192, metadata.outputTokenLimit)
        }
    }

    @Test
    fun `getModelMetadata fails when inputTokenLimit is missing`() {
        runBlocking {
            val httpClient = mockHttpClient(
                expectedModel = "gemini-2.5-flash",
                status = HttpStatusCode.OK,
                body = """{"name":"models/gemini-2.5-flash","outputTokenLimit":8192}""",
            )
            val client = GeminiNetworkClient(
                httpClient = httpClient,
                apiKey = "test-key",
                baseUrl = "https://example.test/v1beta",
            )

            val result = client.getModelMetadata("gemini-2.5-flash")
            assertTrue(result.isFailure)
            assertIs<DataError.EmptyResponseError>(result.exceptionOrNull())
        }
    }

    @Test
    fun `getModelMetadata maps not found to network error`() {
        runBlocking {
            val httpClient = mockHttpClient(
                expectedModel = "gemini-unknown",
                status = HttpStatusCode.NotFound,
                body = """{"error":"not found"}""",
            )
            val client = GeminiNetworkClient(
                httpClient = httpClient,
                apiKey = "test-key",
                baseUrl = "https://example.test/v1beta",
            )

            val result = client.getModelMetadata("gemini-unknown")
            assertTrue(result.isFailure)
            assertIs<DataError.NetworkError>(result.exceptionOrNull())
        }
    }

    @Test
    fun `getModelMetadata maps unauthorized to authentication error`() {
        runBlocking {
            val httpClient = mockHttpClient(
                expectedModel = "gemini-2.5-flash",
                status = HttpStatusCode.Unauthorized,
                body = """{"error":"unauthorized"}""",
            )
            val client = GeminiNetworkClient(
                httpClient = httpClient,
                apiKey = "test-key",
                baseUrl = "https://example.test/v1beta",
            )

            val result = client.getModelMetadata("gemini-2.5-flash")
            assertTrue(result.isFailure)
            assertIs<DataError.AuthenticationError>(result.exceptionOrNull())
        }
    }

    @Test
    fun `getModelMetadata maps rate limit to rate limit error`() {
        runBlocking {
            val httpClient = mockHttpClient(
                expectedModel = "gemini-2.5-flash",
                status = HttpStatusCode.TooManyRequests,
                body = """{"error":"rate limited"}""",
            )
            val client = GeminiNetworkClient(
                httpClient = httpClient,
                apiKey = "test-key",
                baseUrl = "https://example.test/v1beta",
            )

            val result = client.getModelMetadata("gemini-2.5-flash")
            assertTrue(result.isFailure)
            assertIs<DataError.RateLimitError>(result.exceptionOrNull())
        }
    }

    private fun mockHttpClient(
        expectedModel: String,
        status: HttpStatusCode,
        body: String,
    ): HttpClient {
        val engine = MockEngine { request ->
            assertEquals("/v1beta/models/$expectedModel", request.url.encodedPath)
            assertEquals("test-key", request.headers["x-goog-api-key"])
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        return HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    }
                )
            }
        }
    }
}
