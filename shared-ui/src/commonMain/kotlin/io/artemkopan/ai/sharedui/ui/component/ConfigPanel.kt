package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ConfigPanel(
    maxOutputTokens: String,
    onMaxOutputTokensChanged: (String) -> Unit,
    stopSequences: String,
    onStopSequencesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CyberpunkPanel(
        title = "CONFIG",
        modifier = modifier,
    ) {
        CyberpunkTextField(
            value = maxOutputTokens,
            onValueChange = onMaxOutputTokensChanged,
            label = "MAX TOKENS",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        CyberpunkTextField(
            value = stopSequences,
            onValueChange = onStopSequencesChanged,
            label = "STOP SEQ",
            placeholder = "comma, separated",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}
