package io.artemkopan.ai.sharedui.gateway

import io.artemkopan.ai.sharedcontract.ChatInfo
import io.artemkopan.ai.sharedcontract.ProjectInfo

interface TerminalGateway {
    suspend fun getProjects(): Result<List<ProjectInfo>>
    suspend fun getChats(): Result<List<ChatInfo>>
    suspend fun createChat(projectPath: String = ""): Result<String>
    suspend fun closeChat(chatId: String): Result<Unit>
}
