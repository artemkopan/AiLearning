package io.artemkopan.ai.sharedui.gateway

import io.artemkopan.ai.sharedcontract.ChatConfigDto
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto

interface PromptGateway {
    suspend fun getConfig(): Result<ChatConfigDto>
    suspend fun generate(request: GenerateRequestDto): Result<GenerateResponseDto>
}
