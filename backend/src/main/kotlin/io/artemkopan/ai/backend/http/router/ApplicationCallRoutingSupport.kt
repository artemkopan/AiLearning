package io.artemkopan.ai.backend.http.router

import io.ktor.server.application.*
import io.ktor.util.*
import java.util.*

private val requestIdKey = AttributeKey<String>("requestId")

internal fun ApplicationCall.ensureRequestId(): String {
    if (attributes.contains(requestIdKey)) return attributes[requestIdKey]

    val newId = UUID.randomUUID().toString()
    attributes.put(requestIdKey, newId)
    return newId
}

internal fun ApplicationCall.resolveUserScope(): String {
    val raw = request.queryParameters["userId"]
        ?: request.headers["X-User-Id"]
        ?: "anonymous"

    val normalized = raw
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9._-]"), "-")
        .take(64)
        .trim('-')

    return normalized.ifBlank { "anonymous" }
}
