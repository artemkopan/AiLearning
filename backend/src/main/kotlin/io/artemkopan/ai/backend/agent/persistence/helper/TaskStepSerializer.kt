package io.artemkopan.ai.backend.agent.persistence.helper

import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.model.TaskStep
import io.artemkopan.ai.core.domain.model.TaskStepStatus
import kotlinx.serialization.Serializable
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

internal fun List<TaskStep>.toJson(json: Json): String =
    json.encodeToString(map { it.toJsonDto() })

internal fun String.toTaskSteps(json: Json): List<TaskStep> =
    json.decodeFromString<List<TaskStepJson>>(this).map { it.toDomain() }

private fun TaskStep.toJsonDto() = TaskStepJson(
    index = index,
    phase = phase.name.lowercase(),
    description = description,
    expectedAction = expectedAction,
    status = status.name.lowercase(),
    result = result,
)

private fun TaskStepJson.toDomain() = TaskStep(
    index = index,
    phase = TaskPhase.valueOf(phase.uppercase()),
    description = description,
    expectedAction = expectedAction,
    status = TaskStepStatus.valueOf(status.uppercase()),
    result = result,
)
