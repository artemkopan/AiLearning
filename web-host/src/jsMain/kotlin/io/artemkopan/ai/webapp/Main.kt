package io.artemkopan.ai.webapp

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import androidx.lifecycle.viewmodel.compose.viewModel
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.ui.AiAssistantScreen
import io.artemkopan.ai.webapp.ui.HttpPromptGateway
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.browser.window

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
    Napier.base(DebugAntilog())

    val backendUrl = resolveBackendUrl()
    Napier.i(tag = "Main") { "Frontend initialized. Backend URL: $backendUrl" }

    CanvasBasedWindow(title = "AiAssistant") {
        val gateway = remember { HttpPromptGateway(backendBaseUrl = backendUrl) }
        val viewModel = viewModel { AppViewModel(gateway) }

        AiAssistantScreen(viewModel)
    }
}
