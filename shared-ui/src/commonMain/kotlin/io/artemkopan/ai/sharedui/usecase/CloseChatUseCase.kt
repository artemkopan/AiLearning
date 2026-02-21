package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.gateway.TerminalGateway

class CloseChatUseCase(private val gateway: TerminalGateway) {
    suspend operator fun invoke(chatId: String): Result<Unit> = gateway.closeChat(chatId)
}
