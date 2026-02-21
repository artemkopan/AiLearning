package io.artemkopan.ai.sharedui.gateway

import io.ktor.client.HttpClient

const val BACKEND_BASE_URL = "http://localhost:8080"

expect fun createPlatformHttpClient(baseUrl: String): HttpClient
