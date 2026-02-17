package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.domain.model.ModelId
import io.artemkopan.ai.core.domain.model.Temperature

data class GenerationOptions(
    val modelId: ModelId,
    val temperature: Temperature,
)

class ResolveGenerationOptionsUseCase(
    private val defaultModel: String,
    private val defaultTemperature: Double = 0.7,
) {
    fun execute(model: String?, temperature: Double?): Result<GenerationOptions> {
        val normalizedModel = model?.trim().orEmpty().ifBlank { defaultModel }
        val normalizedTemperature = temperature ?: defaultTemperature

        if (normalizedTemperature !in 0.0..1.0) {
            return Result.failure(AppError.Validation("Temperature must be between 0.0 and 1.0."))
        }

        return Result.success(
            GenerationOptions(
                modelId = ModelId(normalizedModel),
                temperature = Temperature(normalizedTemperature),
            )
        )
    }
}
