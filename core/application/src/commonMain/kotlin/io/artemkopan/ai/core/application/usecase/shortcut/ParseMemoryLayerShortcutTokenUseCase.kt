package io.artemkopan.ai.core.application.usecase.shortcut

class ParseMemoryLayerShortcutTokenUseCase {
    fun execute(text: String): MemoryLayerShortcutToken? {
        val normalized = text.trim().lowercase()
        return when (normalized) {
            SHORT_TERM_TOKEN -> MemoryLayerShortcutToken(raw = SHORT_TERM_TOKEN, layer = MemoryLayerType.SHORT_TERM)
            WORKING_TOKEN -> MemoryLayerShortcutToken(raw = WORKING_TOKEN, layer = MemoryLayerType.WORKING)
            LONG_TERM_TOKEN -> MemoryLayerShortcutToken(raw = LONG_TERM_TOKEN, layer = MemoryLayerType.LONG_TERM)
            else -> null
        }
    }
}

const val SHORT_TERM_TOKEN = "/memory-short-term"
const val WORKING_TOKEN = "/memory-working"
const val LONG_TERM_TOKEN = "/memory-long-term"
