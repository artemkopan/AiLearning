package io.artemkopan.ai.sharedui.usecase

import androidx.compose.ui.text.input.TextFieldValue
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.SlashTokenBounds
import org.koin.core.annotation.Factory

@Factory
class FindSlashTokenBoundsUseCase {
    operator fun invoke(value: TextFieldValue): SlashTokenBounds? {
        if (!value.selection.collapsed) return null
        val selectionStart = value.selection.start
        val text = value.text
        if (selectionStart < 0 || selectionStart > text.length) return null

        var tokenStart = selectionStart
        while (tokenStart > 0 && !text[tokenStart - 1].isWhitespace()) {
            tokenStart -= 1
        }

        var tokenEnd = selectionStart
        while (tokenEnd < text.length && !text[tokenEnd].isWhitespace()) {
            tokenEnd += 1
        }

        if (tokenStart >= text.length) return null
        if (text[tokenStart] != '/') return null

        val rawToken = text.substring(tokenStart, tokenEnd)
        val query = rawToken.removePrefix("/")
        return SlashTokenBounds(
            start = tokenStart,
            endExclusive = tokenEnd,
            query = query,
        )
    }
}
