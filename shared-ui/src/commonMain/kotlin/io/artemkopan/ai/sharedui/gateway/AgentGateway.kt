package io.artemkopan.ai.sharedui.gateway

import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.artemkopan.ai.sharedcontract.AgentWsServerMessageDto
import kotlinx.coroutines.flow.Flow

interface AgentGateway {
    suspend fun getConfig(): Result<AgentConfigDto>
    suspend fun connect(): Result<Unit>
    fun disconnect()
    suspend fun send(message: AgentWsClientMessageDto): Result<Unit>
    val events: Flow<AgentWsServerMessageDto>
}
