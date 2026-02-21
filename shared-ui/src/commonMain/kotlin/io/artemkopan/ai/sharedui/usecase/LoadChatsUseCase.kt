package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.ChatInfo
import io.artemkopan.ai.sharedui.gateway.TerminalGateway

class LoadChatsUseCase(private val gateway: TerminalGateway) {
    suspend operator fun invoke(): Result<List<ChatInfo>> = gateway.getChats()
}
