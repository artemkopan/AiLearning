package io.artemkopan.ai.webapp

import co.touchlab.kermit.Logger
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.WebSocket

private val log = Logger.withTag("XtermBridge")

class XtermBridge(private val backendUrl: String) {

    private var currentTerminal: dynamic = null
    private var currentFitAddon: dynamic = null
    private var currentWebSocket: WebSocket? = null
    private var currentChatId: String? = null

    fun switchChat(chatId: String?) {
        if (chatId == currentChatId) return
        detach()
        if (chatId != null) {
            attach(chatId)
        }
    }

    private fun attach(chatId: String) {
        log.i { "Attaching terminal for chat: $chatId" }
        currentChatId = chatId

        val container = document.getElementById("terminal-container") ?: run {
            log.e { "terminal-container element not found" }
            return
        }
        container.asDynamic().style.display = "block"

        // Create xterm.js Terminal
        val Terminal = js("window.Terminal")
        if (Terminal == undefined) {
            log.e { "xterm.js Terminal not loaded" }
            return
        }

        val termOptions = js("{}")
        termOptions.cursorBlink = true
        termOptions.theme = js("{}")
        termOptions.theme.background = "#0A0A0F"
        termOptions.theme.foreground = "#E0E0E0"
        termOptions.theme.cursor = "#F9F002"
        termOptions.theme.selectionBackground = "#F9F00240"
        termOptions.fontFamily = "'JetBrains Mono', monospace"
        termOptions.fontSize = 14

        val terminal = js("new Terminal(termOptions)")
        currentTerminal = terminal

        // FitAddon
        val FitAddon = js("window.FitAddon")?.FitAddon
        if (FitAddon != undefined) {
            val fitAddon = js("new FitAddon()")
            currentFitAddon = fitAddon
            terminal.loadAddon(fitAddon)
        }

        terminal.open(container)
        currentFitAddon?.fit()

        // Connect WebSocket
        val wsUrl = backendUrl.replace("http://", "ws://").replace("https://", "wss://")
        val ws = WebSocket("$wsUrl/ws/$chatId")
        currentWebSocket = ws

        ws.onopen = {
            log.i { "WebSocket connected for chat: $chatId" }
            sendResize()
        }

        ws.onmessage = { event ->
            val data = event.asDynamic().data
            if (data is String) {
                terminal.write(data)
            }
        }

        ws.onclose = {
            log.d { "WebSocket closed for chat: $chatId" }
        }

        ws.onerror = { event ->
            log.e { "WebSocket error for chat: $chatId" }
        }

        // Terminal input -> WebSocket
        terminal.onData { data: String ->
            if (ws.readyState == WebSocket.OPEN) {
                ws.send(data)
            }
        }

        // Handle browser resize
        window.onresize = {
            currentFitAddon?.fit()
            sendResize()
        }

        // Drag-and-drop file upload
        setupDragAndDrop(container, chatId, ws)
    }

    private fun setupDragAndDrop(container: dynamic, chatId: String, ws: WebSocket) {
        container.addEventListener("dragover") { event: dynamic ->
            event.preventDefault()
            event.dataTransfer.dropEffect = "copy"
            container.style.outline = "2px solid #F9F002"
        }

        container.addEventListener("dragleave") { _: dynamic ->
            container.style.outline = ""
        }

        container.addEventListener("drop") { event: dynamic ->
            event.preventDefault()
            container.style.outline = ""

            val files = event.dataTransfer.files
            val len = files.length as Int
            for (i in 0 until len) {
                val file = files[i]
                uploadFile(file, chatId, ws)
            }
        }
    }

    private fun uploadFile(file: dynamic, chatId: String, ws: WebSocket) {
        val fileName = file.name as String
        log.i { "Uploading file: $fileName for chat: $chatId" }

        val formData = js("new FormData()")
        formData.append("file", file)

        val fetchOptions = js("{}")
        fetchOptions.method = "POST"
        fetchOptions.body = formData

        js("fetch")(
            "$backendUrl/api/chats/$chatId/upload",
            fetchOptions
        ).then { response: dynamic ->
            if (response.ok) {
                response.json()
            } else {
                log.e { "Upload failed: ${response.status}" }
                null
            }
        }.then { json: dynamic ->
            if (json != null) {
                val path = json.path as String
                log.i { "File uploaded: $path" }
                if (ws.readyState == WebSocket.OPEN) {
                    ws.send("$path ")
                }
            }
        }.catch { error: dynamic ->
            log.e { "Upload error: $error" }
        }
    }

    private fun detach() {
        currentChatId?.let { id ->
            log.d { "Detaching terminal for chat: $id" }
        }
        currentWebSocket?.close()
        currentWebSocket = null
        currentTerminal?.dispose()
        currentTerminal = null
        currentFitAddon = null
        currentChatId = null

        val container = document.getElementById("terminal-container")
        container?.asDynamic()?.style?.display = "none"
        container?.innerHTML = ""
    }

    private fun sendResize() {
        val terminal = currentTerminal ?: return
        val ws = currentWebSocket ?: return
        if (ws.readyState != WebSocket.OPEN) return

        val cols = terminal.cols as? Int ?: return
        val rows = terminal.rows as? Int ?: return
        ws.send(JSON.stringify(js("({type:'resize',cols:cols,rows:rows})")))
    }
}
