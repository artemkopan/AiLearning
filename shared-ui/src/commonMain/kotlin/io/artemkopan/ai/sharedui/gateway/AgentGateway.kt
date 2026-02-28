package io.artemkopan.ai.sharedui.gateway

import io.artemkopan.ai.sharedcontract.*
import kotlinx.coroutines.flow.Flow

interface AgentGateway {
    suspend fun getConfig(): Result<AgentConfigDto>
    suspend fun getAgentStats(): Result<AgentStatsResponseDto>
    suspend fun getModelMetadata(model: String): Result<ModelMetadataDto>
    suspend fun connect(): Result<Unit>
    fun disconnect()
    suspend fun send(message: AgentWsClientMessageDto): Result<Unit>
    val events: Flow<AgentWsServerMessageDto>
}
