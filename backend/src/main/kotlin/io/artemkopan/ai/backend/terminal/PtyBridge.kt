package io.artemkopan.ai.backend.terminal

import co.touchlab.kermit.Logger
import com.pty4j.PtyProcessBuilder
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val log = Logger.withTag("PtyBridge")

@Serializable
private data class ResizeMessage(
    val type: String,
    val cols: Int,
    val rows: Int,
)

class PtyBridge(
    private val chatManager: ChatManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handleSession(chatId: String, wsSession: DefaultWebSocketServerSession) {
        if (!chatManager.sessionExists(chatId)) {
            log.w { "Session $chatId does not exist" }
            wsSession.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Session $chatId not found"))
            return
        }

        log.i { "Starting PTY bridge for $chatId" }

        val env = mapOf(
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
        ) + System.getenv()

        val pty = PtyProcessBuilder()
            .setCommand(arrayOf("tmux", "attach-session", "-t", chatId))
            .setEnvironment(env)
            .setInitialColumns(120)
            .setInitialRows(40)
            .start()

        val inputStream = pty.inputStream
        val outputStream = pty.outputStream

        // PTY -> WS: read from PTY output, send to WebSocket
        val ptyToWs = wsSession.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead < 0) break
                    val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    wsSession.send(text)
                }
            } catch (e: Exception) {
                log.d { "PTY read ended for $chatId: ${e.message}" }
            }
        }

        // WS -> PTY: read from WebSocket, write to PTY input
        try {
            for (frame in wsSession.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        // Check for control messages (resize)
                        if (text.startsWith("{\"type\":\"resize\"")) {
                            try {
                                val resize = json.decodeFromString<ResizeMessage>(text)
                                if (resize.type == "resize") {
                                    withContext(Dispatchers.IO) {
                                        pty.winSize = com.pty4j.WinSize(resize.cols, resize.rows)
                                    }
                                }
                            } catch (_: Exception) {
                                // Not a valid resize message, treat as terminal input
                                withContext(Dispatchers.IO) {
                                    outputStream.write(text.toByteArray(Charsets.UTF_8))
                                    outputStream.flush()
                                }
                            }
                        } else {
                            withContext(Dispatchers.IO) {
                                outputStream.write(text.toByteArray(Charsets.UTF_8))
                                outputStream.flush()
                            }
                        }
                    }
                    is Frame.Binary -> {
                        withContext(Dispatchers.IO) {
                            outputStream.write(frame.data)
                            outputStream.flush()
                        }
                    }
                    else -> { /* ignore */ }
                }
            }
        } catch (e: Exception) {
            log.d { "WS read ended for $chatId: ${e.message}" }
        } finally {
            ptyToWs.cancelAndJoin()
            pty.destroyForcibly()
            log.i { "PTY bridge closed for $chatId" }
        }
    }
}
