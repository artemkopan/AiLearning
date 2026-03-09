package io.artemkopan.ai.sharedui.feature.configpanel.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.artemkopan.ai.sharedui.feature.configpanel.viewmodel.ConfigPanelViewModel

@Composable
fun ConfigPanelFeature(
    viewModel: ConfigPanelViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    ConfigPanel(
        model = state.model,
        onModelChanged = viewModel::onModelChanged,
        maxOutputTokens = state.maxOutputTokens,
        onMaxOutputTokensChanged = viewModel::onMaxOutputTokensChanged,
        temperature = state.temperature,
        onTemperatureChanged = viewModel::onTemperatureChanged,
        stopSequences = state.stopSequences,
        onStopSequencesChanged = viewModel::onStopSequencesChanged,
        invariantsText = state.invariantsText,
        onApplyInvariants = viewModel::onApplyInvariants,
        models = state.models,
        temperaturePlaceholder = state.temperaturePlaceholder,
        modifier = modifier,
    )
}
