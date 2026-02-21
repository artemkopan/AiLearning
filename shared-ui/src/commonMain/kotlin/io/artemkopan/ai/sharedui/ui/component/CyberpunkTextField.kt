package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun cyberpunkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CyberpunkColors.TextPrimary,
    unfocusedTextColor = CyberpunkColors.TextPrimary,
    cursorColor = CyberpunkColors.Yellow,
    focusedBorderColor = CyberpunkColors.Yellow,
    unfocusedBorderColor = CyberpunkColors.BorderDark,
    focusedLabelColor = CyberpunkColors.Yellow,
    unfocusedLabelColor = CyberpunkColors.TextMuted,
    focusedContainerColor = CyberpunkColors.InputBackground,
    unfocusedContainerColor = CyberpunkColors.InputBackground,
    focusedPlaceholderColor = CyberpunkColors.TextMuted,
    unfocusedPlaceholderColor = CyberpunkColors.TextMuted,
)

@Composable
fun CyberpunkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        colors = cyberpunkTextFieldColors(),
        shape = RoundedCornerShape(2.dp),
    )
}

@Composable
fun CyberpunkTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = cyberpunkTextFieldColors(),
        shape = RoundedCornerShape(2.dp),
    )
}
