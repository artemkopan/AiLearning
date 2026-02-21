package io.artemkopan.ai.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import co.touchlab.kermit.Logger
import io.artemkopan.ai.sharedui.di.sharedModule
import io.artemkopan.ai.sharedui.gateway.BACKEND_BASE_URL
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.ui.screen.TerminalScreen
import kotlinx.browser.window
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

private val log = Logger.withTag("Main")

fun main() {
    log.i { "Frontend initialized. Backend URL: $BACKEND_BASE_URL" }
    awaitSkikoReady { startApp() }
}

/**
 * Polls until Skiko WASM runtime is fully initialized.
 *
 * Compose 1.8.2 Canvas (Skiko) compiles WASM asynchronously via skiko.js.
 * On page refresh the browser serves WASM from compiled-module cache, so it can
 * finish before web-host.js registers the Kotlin/JS import functions — or vice-versa.
 * Calling CanvasBasedWindow before WASM is ready crashes with
 * "RenderNodeContext… is not a function".
 *
 * Loading order in index.html: web-host.js first (registers Module imports),
 * then skiko.js (instantiates WASM using those imports).
 * This function waits for Module['calledRun'] === true (set by Emscripten after
 * WASM instantiation completes) before starting Compose.
 */
private fun awaitSkikoReady(onReady: () -> Unit) {
    val ready = js("typeof Module !== 'undefined' && Module['calledRun'] === true")
    if (ready == true) {
        log.i { "Skiko WASM runtime initialized" }
        onReady()
    } else {
        log.d { "Waiting for Skiko WASM runtime..." }
        window.setTimeout({ awaitSkikoReady(onReady) }, 50)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun startApp() {
    log.i { "Starting Compose UI" }
    val xtermBridge = XtermBridge(BACKEND_BASE_URL)
    log.d { "XtermBridge created" }

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
