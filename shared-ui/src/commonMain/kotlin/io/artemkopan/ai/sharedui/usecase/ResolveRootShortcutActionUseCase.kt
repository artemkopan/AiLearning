package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutAction
import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutEvent
import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutKey
import org.koin.core.annotation.Factory

@Factory
class ResolveRootShortcutActionUseCase {
    operator fun invoke(event: RootShortcutEvent): RootShortcutAction? {
        return when {
            event.isCtrlPressed && event.key == RootShortcutKey.ENTER -> RootShortcutAction.SUBMIT
            event.isAltPressed && event.key == RootShortcutKey.DIRECTION_DOWN -> RootShortcutAction.SELECT_NEXT_AGENT
            event.isAltPressed && event.key == RootShortcutKey.DIRECTION_UP -> RootShortcutAction.SELECT_PREVIOUS_AGENT
            event.isAltPressed && event.key == RootShortcutKey.N -> RootShortcutAction.CREATE_AGENT
            else -> null
        }
    }
}
