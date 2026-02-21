package io.artemkopan.ai.sharedui.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.ModelOptionDto
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@OptIn(ExperimentalMaterial3Api::class)
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
    models: List<ModelOptionDto>,
    temperaturePlaceholder: String,
    modifier: Modifier = Modifier,
) {
    CyberpunkPanel(
        title = "CONFIG",
        modifier = modifier,
    ) {
        if (models.isNotEmpty()) {
            ModelDropdown(
                selectedModelId = model,
                models = models,
                onModelSelected = onModelChanged,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CyberpunkTextField(
                value = model,
                onValueChange = onModelChanged,
                label = "MODEL",
                placeholder = "server default",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

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
            placeholder = temperaturePlaceholder,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    selectedModelId: String,
    models: List<ModelOptionDto>,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = models.find { it.id == selectedModelId }
    val displayText = selectedModel?.name ?: models.firstOrNull()?.name.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = {
                Text(
                    "MODEL",
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = cyberpunkTextFieldColors(),
            shape = RoundedCornerShape(2.dp),
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = CyberpunkColors.CardDark,
        ) {
            models.forEach { option ->
                val isSelected = option.id == selectedModelId ||
                    (selectedModelId.isEmpty() && option == models.first())
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) CyberpunkColors.Yellow else CyberpunkColors.TextPrimary,
                        )
                    },
                    onClick = {
                        onModelSelected(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
