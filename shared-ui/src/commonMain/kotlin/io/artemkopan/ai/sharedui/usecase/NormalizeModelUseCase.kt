package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentConfigDto

class NormalizeModelUseCase {
    operator fun invoke(value: String, config: AgentConfigDto?): String {
        val trimmed = value.trim()
        if (config == null) return trimmed
        val fallbackModel = config.defaultModel.trim().ifBlank { config.models.firstOrNull()?.id.orEmpty() }

        config.models.firstOrNull { it.id == trimmed }?.let { return it.id }
        config.models.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        if (trimmed.isNotEmpty()) return fallbackModel

        return config.models.firstOrNull()?.id.ifNullOrBlank(fallbackModel)
    }
}

private fun String?.ifNullOrBlank(fallback: String): String {
    if (this.isNullOrBlank()) return fallback
    return this
}
