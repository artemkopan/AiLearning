package io.artemkopan.ai.sharedui.gateway

import io.ktor.client.HttpClient

const val DEFAULT_BACKEND_BASE_URL = "http://localhost:18080"

expect fun resolveBackendBaseUrl(): String

expect fun createPlatformHttpClient(baseUrl: String): HttpClient
