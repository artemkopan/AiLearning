package io.artemkopan.ai.backend.terminal

import co.touchlab.kermit.Logger
import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.sharedcontract.ChatInfo
import java.util.UUID

private val log = Logger.withTag("ChatManager")

class ChatManager(
    private val shell: Shell,
    private val statusManager: StatusManager,
    private val config: AppConfig,
) {
    suspend fun createChat(projectPath: String): String {
        val chatId = "chat-${UUID.randomUUID().toString().take(8)}"
        log.i { "Creating chat $chatId for project: $projectPath" }

        val result = shell.exec(
            "tmux", "new-session", "-d", "-s", chatId,
            "-c", projectPath,
        )
        if (result.exitCode != 0) {
            log.e { "Failed to create tmux session: ${result.stderr}" }
            throw RuntimeException("Failed to create tmux session: ${result.stderr}")
        }

        // Enable mouse support and let xterm.js handle scrollback natively
        shell.exec("tmux", "set-option", "-t", chatId, "mouse", "on")
        shell.exec("tmux", "set-option", "-t", chatId, "terminal-overrides", "xterm*:smcup@:rmcup@")

        statusManager.initStatus(chatId, projectPath)
        log.i { "Chat $chatId created successfully" }
        return chatId
    }

    suspend fun listChats(): List<ChatInfo> {
        val result = shell.exec("tmux", "list-sessions", "-F", "#{session_name}")
        if (result.exitCode != 0) {
            // No sessions — tmux returns non-zero
            return emptyList()
        }

        return result.stdout.lines()
            .filter { it.startsWith("chat-") }
            .map { sessionName ->
                val status = statusManager.getStatus(sessionName)
                ChatInfo(
                    chatId = sessionName,
                    projectPath = status?.projectPath ?: config.projectsRoot,
                    status = status?.status ?: io.artemkopan.ai.sharedcontract.ChatStatus.idle,
                    since = status?.since ?: "",
                )
            }
    }

    suspend fun sessionExists(chatId: String): Boolean {
        val result = shell.exec("tmux", "has-session", "-t", chatId)
        return result.exitCode == 0
    }

    suspend fun closeChat(chatId: String) {
        log.i { "Closing chat $chatId" }
        shell.exec("tmux", "kill-session", "-t", chatId)
        statusManager.removeStatus(chatId)
    }
}
