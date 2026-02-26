package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentConfigDto

class NormalizeModelUseCase {
    operator fun invoke(value: String, config: AgentConfigDto?): String {
        val trimmed = value.trim()
        if (config == null) return trimmed

        config.models.firstOrNull { it.id == trimmed }?.let { return it.id }
        config.models.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        if (trimmed.isNotEmpty()) return trimmed

        return config.models.firstOrNull()?.id
            ?: config.defaultModel.trim()
    }
}
