package io.artemkopan.ai.backend

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.config.EnvSource
import io.artemkopan.ai.backend.http.module
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

fun main() {
    Napier.base(DebugAntilog())

    val logger = LoggerFactory.getLogger("io.artemkopan.ai.backend.Startup")
    val loadedEnv = EnvSource.loadDetailed()
    val config = AppConfig.fromEnv(loadedEnv.values)

    val loadedFiles = if (loadedEnv.loadedFiles.isEmpty()) {
        "none"
    } else {
        loadedEnv.loadedFiles.joinToString(", ")
    }

    Napier.i(tag = "Startup") { "Napier logging initialized" }
    logger.info("Loaded .env files: {}", loadedFiles)
    logger.info(
        "Loaded config: port={}, geminiModel={}, corsOrigin={}, geminiApiKey={}",
        config.port,
        config.defaultModel,
        config.corsOrigin,
        maskSecret(config.geminiApiKey),
    )

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

private fun maskSecret(value: String): String {
    if (value.isBlank()) return "<missing>"
    if (value.length <= 8) return "****"
    return "${value.take(4)}...${value.takeLast(4)}"
}
