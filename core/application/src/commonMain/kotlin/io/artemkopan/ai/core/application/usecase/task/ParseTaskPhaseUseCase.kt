package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.domain.model.TaskPhase

/**
 * Parses task phase from string (e.g. from WS command DTO).
 * Handles legacy "paused" value (mapped to WAITING_FOR_APPROVAL).
 */
fun parseTaskPhaseFromString(value: String): TaskPhase {
    val normalized = value.trim().uppercase()
    return when (normalized) {
        "PAUSED" -> TaskPhase.WAITING_FOR_APPROVAL
        else -> TaskPhase.valueOf(normalized)
    }
}
