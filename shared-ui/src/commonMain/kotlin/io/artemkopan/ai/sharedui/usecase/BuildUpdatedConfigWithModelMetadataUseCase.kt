package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.ModelMetadataDto
import io.artemkopan.ai.sharedcontract.ModelOptionDto

class BuildUpdatedConfigWithModelMetadataUseCase {
    operator fun invoke(
        config: AgentConfigDto,
        metadata: ModelMetadataDto,
    ): AgentConfigDto {
        return config.copy(
            models = upsertModelOption(
                models = config.models,
                modelId = metadata.model,
                provider = metadata.provider,
                contextWindowTokens = metadata.inputTokenLimit,
            )
        )
    }

    private fun upsertModelOption(
        models: List<ModelOptionDto>,
        modelId: String,
        provider: String,
        contextWindowTokens: Int,
    ): List<ModelOptionDto> {
        val byId = models.indexOfFirst { it.id == modelId }
        if (byId >= 0) {
            return models.toMutableList().apply {
                this[byId] = this[byId].copy(contextWindowTokens = contextWindowTokens)
            }
        }

        val byName = models.indexOfFirst { it.name == modelId }
        if (byName >= 0) {
            return models.toMutableList().apply {
                this[byName] = this[byName].copy(
                    id = modelId,
                    provider = provider,
                    contextWindowTokens = contextWindowTokens,
                )
            }
        }

        return models + ModelOptionDto(
            id = modelId,
            name = modelId,
            provider = provider,
            contextWindowTokens = contextWindowTokens,
        )
    }
}
