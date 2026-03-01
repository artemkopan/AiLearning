package io.artemkopan.ai.backend.provider

import io.artemkopan.ai.sharedcontract.ModelOptionDto

interface LlmModelCatalog {
    fun curatedModels(fallbackContextWindowTokens: Int): List<ModelOptionDto>
}
