package io.artemkopan.ai.webapp

import co.touchlab.kermit.Logger
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.WebSocket

private val log = Logger.withTag("XtermBridge")

private const val RECONNECT_BASE_MS = 500
private const val RECONNECT_MAX_MS = 10_000

class XtermBridge(private val backendUrl: String) {

    private var currentTerminal: dynamic = null
    private var currentFitAddon: dynamic = null
    private var currentWebSocket: WebSocket? = null
    private var currentChatId: String? = null
    private var intentionalClose = false
    private var reconnectDelay = RECONNECT_BASE_MS
    private var reconnectTimer: Int? = null

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
        intentionalClose = false

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

        // Terminal input -> current WebSocket
        terminal.onData { data: String ->
            val ws = currentWebSocket
            if (ws != null && ws.readyState == WebSocket.OPEN) {
                ws.send(data)
            }
        }

        // Handle browser resize
        window.onresize = {
            currentFitAddon?.fit()
            sendResize()
        }

        // Drag-and-drop file upload
        setupDragAndDrop(container, chatId)

        // Connect WebSocket (will auto-reconnect on drop)
        connectWebSocket(chatId)
    }

    private fun connectWebSocket(chatId: String) {
        if (intentionalClose || currentChatId != chatId) return

        val wsUrl = backendUrl.replace("http://", "ws://").replace("https://", "wss://")
        val ws = WebSocket("$wsUrl/ws/$chatId")
        currentWebSocket = ws

        ws.onopen = {
            log.i { "WebSocket connected for chat: $chatId" }
            reconnectDelay = RECONNECT_BASE_MS
            sendResize()
        }

        ws.onmessage = { event ->
            val data = event.asDynamic().data
            if (data is String) {
                currentTerminal?.write(data)
            }
        }

        ws.onclose = {
            log.d { "WebSocket closed for chat: $chatId" }
            if (!intentionalClose && currentChatId == chatId) {
                scheduleReconnect(chatId)
            }
        }

        ws.onerror = {
            log.e { "WebSocket error for chat: $chatId" }
        }
    }

    private fun scheduleReconnect(chatId: String) {
        log.i { "Reconnecting in ${reconnectDelay}ms for chat: $chatId" }
        reconnectTimer = window.setTimeout({
            reconnectTimer = null
            connectWebSocket(chatId)
        }, reconnectDelay)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(RECONNECT_MAX_MS)
    }

    private fun setupDragAndDrop(container: dynamic, chatId: String) {
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
                uploadFile(file, chatId)
            }
        }
    }

    private fun uploadFile(file: dynamic, chatId: String) {
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
                val ws = currentWebSocket
                if (ws != null && ws.readyState == WebSocket.OPEN) {
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
        intentionalClose = true
        reconnectTimer?.let { window.clearTimeout(it) }
        reconnectTimer = null
        reconnectDelay = RECONNECT_BASE_MS
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
