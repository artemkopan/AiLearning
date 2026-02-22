package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.gateway.TerminalGateway

class CreateChatUseCase(private val gateway: TerminalGateway) {
    suspend operator fun invoke(projectPath: String = ""): Result<String> = gateway.createChat(projectPath)
}
