package io.artemkopan.ai.backend.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.log
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri

val HttpBodyLogging = createApplicationPlugin("HttpBodyLogging") {
    onCall { call ->
        val body = call.readRequestBodyForLog()
        val headers = call.request.headers.entries()
            .joinToString(", ") { (name, values) -> "$name=${values.joinToString("|")}" }
        val curl = call.toCurlCommand(body)

        call.application.log.info(
            "HTTP REQUEST method={} uri={} headers=[{}] body={}",
            call.request.httpMethod.value,
            call.request.uri,
            headers,
            body,
        )
        call.application.log.info("HTTP REQUEST CURL {}", curl)
    }

    onCallRespond { call, body ->
        call.application.log.info(
            "HTTP RESPONSE method={} uri={} status={} body={}",
            call.request.httpMethod.value,
            call.request.uri,
            call.response.status()?.value ?: 200,
            body.toLogString(),
        )
    }
}

private suspend fun ApplicationCall.readRequestBodyForLog(): String {
    val contentType = request.contentType()
    if (!contentType.isTextLike()) {
        return "<skipped: non-text content-type=$contentType>"
    }

    return runCatching { receiveText() }
        .map { body -> body.ifEmpty { "<empty>" } }
        .getOrElse { throwable ->
            val reason = throwable.message ?: throwable::class.simpleName ?: "unknown"
            "<unavailable: $reason>"
        }
}

private fun ContentType.isTextLike(): Boolean {
    return contentType == "text" ||
        match(ContentType.Application.Json) ||
        match(ContentType.Application.Xml) ||
        match(ContentType.Application.FormUrlEncoded) ||
        contentSubtype.contains("json", ignoreCase = true) ||
        contentSubtype.contains("xml", ignoreCase = true)
}

private fun Any?.toLogString(): String = when (this) {
    null -> "<null>"
    is ByteArray -> this.toString(Charsets.UTF_8)
    is TextContent -> text
    else -> toString()
}

private fun ApplicationCall.toCurlCommand(body: String): String {
    val host = request.headers[HttpHeaders.Host] ?: "localhost"
    val scheme = request.headers["X-Forwarded-Proto"]
        ?.substringBefore(',')
        ?.trim()
        .orEmpty()
        .ifBlank { "http" }
    val url = "$scheme://$host${request.uri}"
    val headerParts = request.headers.entries().flatMap { (name, values) ->
        values.map { value ->
            val sanitizedValue = if (name.isSensitiveHeader()) "<REDACTED>" else value
            "-H ${shQuote("$name: $sanitizedValue")}"
        }
    }

    val commandParts = mutableListOf(
        "curl -X ${shQuote(request.httpMethod.value)} ${shQuote(url)}"
    )
    commandParts += headerParts
    if (body.shouldIncludeInCurlBody()) {
        commandParts += "--data-raw ${shQuote(body)}"
    }

    return commandParts.joinToString(" \\\n  ")
}

private fun String.shouldIncludeInCurlBody(): Boolean = !startsWith("<") || !endsWith(">")

private fun String.isSensitiveHeader(): Boolean {
    return equals(HttpHeaders.Authorization, ignoreCase = true) ||
        equals(HttpHeaders.Cookie, ignoreCase = true) ||
        equals("x-goog-api-key", ignoreCase = true) ||
        equals("x-api-key", ignoreCase = true)
}

private fun shQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
