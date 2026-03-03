package io.artemkopan.ai.sharedui.usecase

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.SlashTokenBounds
import kotlin.test.Test
import kotlin.test.assertEquals

class InsertCommandTokenUseCaseTest {
    private val useCase = InsertCommandTokenUseCase()

    @Test
    fun `replaces slash token and keeps surrounding text`() {
        val value = TextFieldValue(
            text = "/ag now",
            selection = TextRange(3),
        )

        val result = useCase(
            value = value,
            token = "/agents-stats",
            slashTokenBounds = SlashTokenBounds(start = 0, endExclusive = 3, query = "ag"),
        )

        assertEquals("/agents-stats now", result.text)
        assertEquals(13, result.selection.start)
    }

    @Test
    fun `inserts token at caret when no slash token is active`() {
        val value = TextFieldValue(
            text = "hello",
            selection = TextRange(5),
        )

        val result = useCase(
            value = value,
            token = "/agents-stats",
            slashTokenBounds = null,
        )

        assertEquals("hello /agents-stats", result.text)
        assertEquals(result.text.length, result.selection.start)
    }
}
