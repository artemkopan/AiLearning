package io.artemkopan.ai.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import co.touchlab.kermit.Logger
import io.artemkopan.ai.sharedui.di.sharedUiFeatureModules
import io.artemkopan.ai.sharedui.factory.SharedUiViewModelFactory
import io.artemkopan.ai.sharedui.feature.root.view.AiAssistantScreen
import io.artemkopan.ai.webapp.ui.WsAgentGateway
import kotlinx.browser.window
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

private fun resolveBackendUrl(): String {
    // In production, use same origin. For local dev, override via query param: ?backend=http://localhost:8080
    val params = window.location.search
    val backendParam = params.removePrefix("?")
        .split("&")
        .map { it.split("=", limit = 2) }
        .find { it.firstOrNull() == "backend" }
        ?.getOrNull(1)

    return backendParam ?: "http://localhost:8080"
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val backendUrl = resolveBackendUrl()
    Logger.withTag("Main").i { "Frontend initialized. Backend URL: $backendUrl" }

    val gateway = WsAgentGateway(backendBaseUrl = backendUrl)

    CanvasBasedWindow(title = "AiAssistant") {
        KoinApplication(application = {
            modules(sharedUiFeatureModules(gateway))
        }) {
            val factory = koinInject<SharedUiViewModelFactory>()
            AiAssistantScreen(factory)
        }
    }
}
