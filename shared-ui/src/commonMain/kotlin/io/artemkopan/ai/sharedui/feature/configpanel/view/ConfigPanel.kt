package io.artemkopan.ai.sharedui.feature.configpanel.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedcontract.ModelOptionDto
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkTextField
import io.artemkopan.ai.sharedui.ui.component.cyberpunkTextFieldColors
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
    var isExpanded by remember { mutableStateOf(false) }

    CyberpunkPanel(
        title = "CONFIG",
        onHeaderClick = { isExpanded = !isExpanded },
        showContent = isExpanded,
        headerAction = {
            Text(
                text = if (isExpanded) "[-]" else "[+]",
                style = MaterialTheme.typography.labelSmall,
                color = CyberpunkColors.Yellow,
            )
        },
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
        ?: models.find { it.name == selectedModelId }
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
                val isSelected = option.id == selectedModel?.id ||
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
