package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutAction
import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutEvent
import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveRootShortcutActionUseCaseTest {
    private val useCase = ResolveRootShortcutActionUseCase()

    @Test
    fun `maps configured shortcuts`() {
        assertEquals(
            RootShortcutAction.SUBMIT,
            useCase(
                RootShortcutEvent(
                    key = RootShortcutKey.ENTER,
                    isCtrlPressed = true,
                    isAltPressed = false,
                )
            )
        )
        assertEquals(
            RootShortcutAction.SELECT_NEXT_AGENT,
            useCase(
                RootShortcutEvent(
                    key = RootShortcutKey.DIRECTION_DOWN,
                    isCtrlPressed = false,
                    isAltPressed = true,
                )
            )
        )
        assertEquals(
            RootShortcutAction.SELECT_PREVIOUS_AGENT,
            useCase(
                RootShortcutEvent(
                    key = RootShortcutKey.DIRECTION_UP,
                    isCtrlPressed = false,
                    isAltPressed = true,
                )
            )
        )
        assertEquals(
            RootShortcutAction.CREATE_AGENT,
            useCase(
                RootShortcutEvent(
                    key = RootShortcutKey.N,
                    isCtrlPressed = false,
                    isAltPressed = true,
                )
            )
        )
    }

    @Test
    fun `returns null for unsupported shortcut`() {
        val result = useCase(
            RootShortcutEvent(
                key = RootShortcutKey.ENTER,
                isCtrlPressed = false,
                isAltPressed = false,
            )
        )

        assertNull(result)
    }
}
