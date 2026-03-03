package io.artemkopan.ai.sharedui.usecase

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindSlashTokenBoundsUseCaseTest {
    private val useCase = FindSlashTokenBoundsUseCase()

    @Test
    fun `finds bounds when caret is inside slash token`() {
        val value = TextFieldValue(
            text = "/agents-stats now",
            selection = TextRange(7),
        )

        val result = useCase(value)

        checkNotNull(result)
        assertEquals(0, result.start)
        assertEquals(13, result.endExclusive)
        assertEquals("agents-stats", result.query)
    }

    @Test
    fun `returns null when token does not start with slash`() {
        val value = TextFieldValue(
            text = "hello world",
            selection = TextRange(3),
        )

        val result = useCase(value)

        assertNull(result)
    }

    @Test
    fun `returns null when selection is not collapsed`() {
        val value = TextFieldValue(
            text = "/agents-stats",
            selection = TextRange(0, 4),
        )

        val result = useCase(value)

        assertNull(result)
    }
}
