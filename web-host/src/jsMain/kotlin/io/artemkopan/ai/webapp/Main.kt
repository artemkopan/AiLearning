package io.artemkopan.ai.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import co.touchlab.kermit.Logger
import io.artemkopan.ai.sharedui.di.sharedModule
import io.artemkopan.ai.sharedui.gateway.BACKEND_BASE_URL
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.ui.screen.TerminalScreen
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Logger.withTag("Main").i { "Frontend initialized. Backend URL: $BACKEND_BASE_URL" }

    val xtermBridge = XtermBridge(BACKEND_BASE_URL)

    CanvasBasedWindow(title = "Terminal") {
        KoinApplication(application = {
            modules(sharedModule)
        }) {
            val viewModel = koinViewModel<AppViewModel>()
            TerminalScreen(
                viewModel = viewModel,
                onActiveChatChanged = { chatId -> xtermBridge.switchChat(chatId) },
            )
        }
    }
}
