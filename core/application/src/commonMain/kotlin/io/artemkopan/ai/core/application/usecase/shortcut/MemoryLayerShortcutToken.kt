package io.artemkopan.ai.core.application.usecase.shortcut

enum class MemoryLayerType {
    SHORT_TERM,
    WORKING,
    LONG_TERM,
}

data class MemoryLayerShortcutToken(
    val raw: String,
    val layer: MemoryLayerType,
)
