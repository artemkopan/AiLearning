package io.artemkopan.ai.backend.http.router

import io.ktor.server.routing.*

interface RouterHandler {
    fun Routing.invoke()
}
