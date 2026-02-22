package io.artemkopan.ai.sharedui.gateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun resolveBackendBaseUrl(): String {
    val backendBaseUrl = readEnv("BACKEND_BASE_URL")
    val backendHostPort = readEnv("BACKEND_HOST_PORT")
    return buildBackendBaseUrl(backendBaseUrl, backendHostPort)
}

private fun readEnv(name: String): String? {
    val env = js("typeof process !== 'undefined' && process && process.env ? process.env : null")
    val value = if (env == null) null else env.asDynamic()[name] as String?
    return value?.trim()?.takeIf { it.isNotEmpty() }
}

private fun buildBackendBaseUrl(baseUrl: String?, hostPort: String?): String {
    val normalizedPort = hostPort
        ?.trim()
        ?.takeIf { it.matches(Regex("^\\d+$")) }
    val fallbackPort = normalizedPort ?: "18080"

    if (!baseUrl.isNullOrBlank()) {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val withScheme = if (
            normalizedBase.startsWith("http://") || normalizedBase.startsWith("https://")
        ) {
            normalizedBase
        } else {
            "http://$normalizedBase"
        }

        val hasPort = Regex(":[0-9]+$").containsMatchIn(withScheme)
        return if (normalizedPort != null && !hasPort) "$withScheme:$normalizedPort" else withScheme
    }

    val browserHost = js("typeof window !== 'undefined' && window.location ? window.location.hostname : null") as String?
    val browserProtocol = js("typeof window !== 'undefined' && window.location ? window.location.protocol : null") as String?
    if (!browserHost.isNullOrBlank()) {
        val protocol = if (browserProtocol == "https:") "https" else "http"
        return "$protocol://$browserHost:$fallbackPort"
    }

    return DEFAULT_BACKEND_BASE_URL
}

actual fun createPlatformHttpClient(baseUrl: String): HttpClient = HttpClient(Js) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(WebSockets)
    defaultRequest { url(baseUrl) }
}
