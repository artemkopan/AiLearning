package io.artemkopan.ai.sharedui.feature.root.model

data class RootShortcutEvent(
    val key: RootShortcutKey,
    val isCtrlPressed: Boolean,
    val isAltPressed: Boolean,
)

enum class RootShortcutKey {
    ENTER,
    DIRECTION_DOWN,
    DIRECTION_UP,
    N,
    OTHER,
}

enum class RootShortcutAction {
    SUBMIT,
    SELECT_NEXT_AGENT,
    SELECT_PREVIOUS_AGENT,
    CREATE_AGENT,
}
