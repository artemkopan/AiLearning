package io.artemkopan.ai.core.application.model

import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.RetrievedContextChunk

data class AssistantMemoryModel(
    val shortTerm: ShortTermMemoryLayer,
    val working: WorkingMemoryLayer,
    val longTerm: LongTermMemoryLayer,
)

data class ShortTermMemoryLayer(
    val dialogueTurns: List<AgentMessage>,
)

data class WorkingMemoryLayer(
    val taskDataSummary: String,
)

data class LongTermMemoryLayer(
    val profileAndDecisions: String,
    val retrievedKnowledge: List<RetrievedContextChunk>,
)
