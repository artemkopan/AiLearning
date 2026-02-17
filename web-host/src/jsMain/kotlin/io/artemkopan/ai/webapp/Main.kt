package io.artemkopan.ai.webapp

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import io.artemkopan.ai.sharedui.state.AppState
import io.artemkopan.ai.sharedui.ui.AiAssistantScreen
import io.artemkopan.ai.webapp.ui.HttpPromptGateway

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    println("[AiAssistant][UI] Frontend initialized. Backend URL: http://localhost:8080")

    CanvasBasedWindow(title = "AiAssistant") {
        val gateway = remember { HttpPromptGateway(backendBaseUrl = "http://localhost:8080") }
        val appState = remember { AppState(gateway) }

        AiAssistantScreen(appState)
    }
}
