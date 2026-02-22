package io.artemkopan.ai.sharedui.gateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun resolveBackendBaseUrl(): String = DEFAULT_BACKEND_BASE_URL

actual fun createPlatformHttpClient(baseUrl: String): HttpClient = HttpClient(Js) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(WebSockets)
    defaultRequest { url(baseUrl) }
}
