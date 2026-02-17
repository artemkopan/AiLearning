package io.artemkopan.ai.sharedui.gateway

import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto

interface PromptGateway {
    suspend fun generate(request: GenerateRequestDto): Result<GenerateResponseDto>
}
