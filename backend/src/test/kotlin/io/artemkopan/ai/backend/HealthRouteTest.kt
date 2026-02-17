package io.artemkopan.ai.backend

import io.artemkopan.ai.backend.http.module
import io.ktor.client.request.get
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRouteTest {
    @Test
    fun `health endpoint responds ok`() = testApplication {
        application {
            module()
        }

        val response = client.get("/health")
        assertEquals(200, response.status.value)
    }
}
