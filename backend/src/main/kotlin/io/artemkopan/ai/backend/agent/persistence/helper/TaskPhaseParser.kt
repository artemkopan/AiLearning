package io.artemkopan.ai.backend.agent.persistence.helper

import io.artemkopan.ai.core.domain.model.TaskPhase

/**
 * Parses task phase from DB string.
 * Handles legacy "paused" value (mapped to WAITING_FOR_APPROVAL).
 */
internal fun parseTaskPhaseFromDb(value: String): TaskPhase {
    val normalized = value.trim().uppercase()
    return when (normalized) {
        "PAUSED" -> TaskPhase.WAITING_FOR_APPROVAL
        else -> TaskPhase.valueOf(normalized)
    }
}
