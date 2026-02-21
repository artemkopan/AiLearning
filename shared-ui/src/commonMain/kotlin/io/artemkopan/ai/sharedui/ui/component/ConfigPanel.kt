package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ConfigPanel(
    model: String,
    onModelChanged: (String) -> Unit,
    maxOutputTokens: String,
    onMaxOutputTokensChanged: (String) -> Unit,
    temperature: String,
    onTemperatureChanged: (String) -> Unit,
    stopSequences: String,
    onStopSequencesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CyberpunkPanel(
        title = "CONFIG",
        modifier = modifier,
    ) {
        CyberpunkTextField(
            value = model,
            onValueChange = onModelChanged,
            label = "MODEL",
            placeholder = "server default",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        CyberpunkTextField(
            value = maxOutputTokens,
            onValueChange = onMaxOutputTokensChanged,
            label = "MAX TOKENS",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        CyberpunkTextField(
            value = temperature,
            onValueChange = onTemperatureChanged,
            label = "TEMPERATURE",
            placeholder = "0.0 – 2.0",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
