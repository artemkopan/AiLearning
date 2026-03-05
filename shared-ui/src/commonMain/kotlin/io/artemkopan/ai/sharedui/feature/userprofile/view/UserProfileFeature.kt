package io.artemkopan.ai.sharedui.feature.userprofile.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.feature.userprofile.model.ProfilePreset
import io.artemkopan.ai.sharedui.feature.userprofile.viewmodel.UserProfileViewModel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkPanel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkTextField
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors

@Composable
fun UserProfileFeature(
    viewModel: UserProfileViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    CyberpunkPanel(
        title = "USER PROFILE",
        accentColor = CyberpunkColors.NeonGreen,
        modifier = modifier,
    ) {
        Text(
            text = "PRESETS:",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.NeonGreen,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.presets.forEach { preset ->
                PresetChip(
                    preset = preset,
                    isActive = !state.isDirty && isPresetActive(state.communicationStyle, state.responseFormat, preset),
                    onClick = { viewModel.onApplyPreset(preset) },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "COMMUNICATION STYLE:",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.NeonGreen,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            COMMUNICATION_STYLES.forEach { style ->
                ChipOption(
                    label = style.uppercase(),
                    selected = state.communicationStyle == style,
                    onClick = { viewModel.onCommunicationStyleChanged(style) },
                )
            }
        }

        Text(
            text = "RESPONSE FORMAT:",
            style = MaterialTheme.typography.bodySmall,
            color = CyberpunkColors.NeonGreen,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RESPONSE_FORMATS.forEach { format ->
                ChipOption(
                    label = format.uppercase(),
                    selected = state.responseFormat == format,
                    onClick = { viewModel.onResponseFormatChanged(format) },
                )
            }
        }

        CyberpunkTextField(
            value = state.restrictions,
            onValueChange = viewModel::onRestrictionsChanged,
            label = "RESTRICTIONS",
            placeholder = "semicolon-separated",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 1,
            maxLines = 3,
        )

        CyberpunkTextField(
            value = state.customInstructions,
            onValueChange = viewModel::onCustomInstructionsChanged,
            label = "CUSTOM INSTRUCTIONS",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 1,
            maxLines = 4,
        )

        if (state.isDirty) {
            Text(
                text = "[ SAVE ]",
                style = MaterialTheme.typography.labelMedium,
                color = CyberpunkColors.NeonGreen,
                modifier = Modifier.clickable { viewModel.onSaveProfile() },
            )
        }
    }
}

@Composable
private fun ChipOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) CyberpunkColors.Red else CyberpunkColors.TextSecondary,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun PresetChip(
    preset: ProfilePreset,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = preset.name,
        style = MaterialTheme.typography.bodySmall,
        color = if (isActive) CyberpunkColors.Cyan else CyberpunkColors.TextSecondary,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun isPresetActive(
    communicationStyle: String,
    responseFormat: String,
    preset: ProfilePreset,
): Boolean {
    return communicationStyle == preset.communicationStyle &&
        responseFormat == preset.responseFormat
}

private val COMMUNICATION_STYLES = listOf("concise", "detailed", "socratic", "casual", "formal")
private val RESPONSE_FORMATS = listOf("plain_text", "markdown", "structured", "code_focused")
