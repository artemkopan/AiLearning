package io.artemkopan.ai.backend.http

import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

val HttpClientCurlLogging = createClientPlugin("HttpClientCurlLogging") {
    val logger = LoggerFactory.getLogger("io.artemkopan.ai.backend.httpclient.curl")

    onRequest { request, content ->
        val bodyText = extractBodyText(content)
        val curl = buildCurlCommand(request, bodyText)
        logger.info("HTTP CLIENT REQUEST\n{}", curl)
        if (bodyText != null) {
            logger.info("HTTP CLIENT REQUEST BODY\n{}", bodyText.truncate(4000))
        }
    }

    onResponse { response ->
        val body = runCatching { response.bodyAsText() }.getOrElse { "<unavailable>" }
        logger.info(
            "HTTP CLIENT RESPONSE status={} url={} body={}",
            response.status.value,
            response.request.url,
            body.truncate(2000),
        )
    }
}

private fun buildCurlCommand(request: HttpRequestBuilder, bodyText: String?): String {
    val url = URLBuilder().takeFrom(request.url).buildString()
    val method = request.method.value

    val parts = mutableListOf("curl -X ${shQuote(method)} ${shQuote(url)}")

    request.headers.build().forEach { name, values ->
        values.forEach { value ->
            val sanitized = if (name.isSensitiveHeader()) "<REDACTED>" else value
            parts += "-H ${shQuote("$name: $sanitized")}"
        }
    }

    if (bodyText != null) {
        parts += "--data-raw ${shQuote(bodyText)}"
    }

    return parts.joinToString(" \\\n  ")
}

private fun extractBodyText(content: Any?): String? {
    if (content == null) return null
    return when (content) {
        is io.ktor.http.content.TextContent -> content.text
        is io.ktor.http.content.OutputStreamContent -> null
        is String -> content
        else -> null
    }
}

private fun String.isSensitiveHeader(): Boolean {
    return equals(HttpHeaders.Authorization, ignoreCase = true) ||
        equals(HttpHeaders.Cookie, ignoreCase = true) ||
        equals("x-goog-api-key", ignoreCase = true) ||
        equals("x-api-key", ignoreCase = true)
}

private fun shQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

private fun String.truncate(maxLength: Int): String {
    return if (length <= maxLength) this else take(maxLength) + "...<truncated ${length - maxLength} chars>"
}
