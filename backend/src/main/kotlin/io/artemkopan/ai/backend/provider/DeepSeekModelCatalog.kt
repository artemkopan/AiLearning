package io.artemkopan.ai.backend.provider

import io.artemkopan.ai.sharedcontract.ModelOptionDto

class DeepSeekModelCatalog : LlmModelCatalog {
    override fun curatedModels(fallbackContextWindowTokens: Int): List<ModelOptionDto> = listOf(
        ModelOptionDto(
            id = "deepseek-chat",
            name = "DeepSeek Chat",
            provider = "deepseek",
            contextWindowTokens = fallbackContextWindowTokens,
        ),
        ModelOptionDto(
            id = "deepseek-reasoner",
            name = "DeepSeek Reasoner",
            provider = "deepseek",
            contextWindowTokens = fallbackContextWindowTokens,
        ),
    )
}
