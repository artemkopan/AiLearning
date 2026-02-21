package io.artemkopan.ai.backend

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.config.EnvSource
import io.artemkopan.ai.backend.http.module
import co.touchlab.kermit.Logger
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("io.artemkopan.ai.backend.Startup")
    val loadedEnv = EnvSource.loadDetailed()
    val config = AppConfig.fromEnv(loadedEnv.values)

    val loadedFiles = if (loadedEnv.loadedFiles.isEmpty()) {
        "none"
    } else {
        loadedEnv.loadedFiles.joinToString(", ")
    }

    Logger.withTag("Startup").i { "Logging initialized" }
    logger.info("Loaded .env files: {}", loadedFiles)
    logger.info(
        "Loaded config: port={}, geminiModel={}, corsOrigin={}, geminiApiKey={}",
        config.port,
        config.defaultModel,
        config.corsOrigin,
        maskSecret(config.geminiApiKey),
    )

    try {
        config.validate()
        logger.info("Configuration validated successfully")
    } catch (e: IllegalArgumentException) {
        logger.error("Configuration validation failed: {}", e.message)
        throw e
    }

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

private fun maskSecret(value: String): String {
    if (value.isBlank()) return "<missing>"
    if (value.length <= 8) return "****"
    return "${value.take(4)}...${value.takeLast(4)}"
}
