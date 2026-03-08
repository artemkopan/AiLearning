package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.model.TaskStep
import io.artemkopan.ai.core.domain.model.TaskStepStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
internal data class TaskStepJson(
    val index: Int,
    val phase: String,
    val description: String,
    val expectedAction: String,
    val status: String,
    val result: String = "",
)

internal fun List<TaskStep>.toTaskStepsJson(json: Json): String =
    json.encodeToString(ListSerializer(TaskStepJson.serializer()), map {
        TaskStepJson(it.index, it.phase.name.lowercase(), it.description, it.expectedAction, it.status.name.lowercase(), it.result)
    })

internal fun String.toTaskSteps(json: Json): List<TaskStep> {
    if (isBlank()) return emptyList()
    return json.decodeFromString(ListSerializer(TaskStepJson.serializer()), this).map {
        TaskStep(
            index = it.index,
            phase = runCatching { TaskPhase.valueOf(it.phase.uppercase()) }.getOrDefault(TaskPhase.PLANNING),
            description = it.description,
            expectedAction = it.expectedAction,
            status = runCatching { TaskStepStatus.valueOf(it.status.uppercase()) }.getOrDefault(TaskStepStatus.PENDING),
            result = it.result,
        )
    }
}
