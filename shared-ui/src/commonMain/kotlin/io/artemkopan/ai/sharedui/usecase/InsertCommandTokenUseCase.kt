package io.artemkopan.ai.sharedui.usecase

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.SlashTokenBounds
import org.koin.core.annotation.Factory

@Factory
class InsertCommandTokenUseCase {
    operator fun invoke(
        value: TextFieldValue,
        token: String,
        slashTokenBounds: SlashTokenBounds?,
    ): TextFieldValue {
        val text = value.text
        return if (slashTokenBounds != null) {
            val trailingSpace = when {
                slashTokenBounds.endExclusive >= text.length -> " "
                text[slashTokenBounds.endExclusive].isWhitespace() -> ""
                else -> " "
            }
            val replacement = token + trailingSpace
            val updated = text.replaceRange(slashTokenBounds.start, slashTokenBounds.endExclusive, replacement)
            val cursor = slashTokenBounds.start + replacement.length
            TextFieldValue(text = updated, selection = TextRange(cursor))
        } else {
            val selectionStart = value.selection.start.coerceIn(0, text.length)
            val selectionEnd = value.selection.end.coerceIn(0, text.length)
            val start = minOf(selectionStart, selectionEnd)
            val end = maxOf(selectionStart, selectionEnd)
            val leadingSpace = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
            val trailingSpace = if (end < text.length && !text[end].isWhitespace()) " " else ""
            val replacement = "$leadingSpace$token$trailingSpace"
            val updated = text.replaceRange(start, end, replacement)
            val cursor = start + replacement.length
            TextFieldValue(text = updated, selection = TextRange(cursor))
        }
    }
}
