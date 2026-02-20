package io.artemkopan.ai.sharedui.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.state.UiAction
import io.artemkopan.ai.sharedui.state.UiState
import io.artemkopan.ai.sharedui.ui.component.AgentModeSelector
import io.artemkopan.ai.sharedui.ui.component.ConfigPanel
import io.artemkopan.ai.sharedui.ui.component.CyberpunkTextField
import io.artemkopan.ai.sharedui.ui.component.ErrorDialog
import io.artemkopan.ai.sharedui.ui.component.OutputPanel
import io.artemkopan.ai.sharedui.ui.component.StatusPanel
import io.artemkopan.ai.sharedui.ui.component.SubmitButton
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkColors
import io.artemkopan.ai.sharedui.ui.theme.CyberpunkTheme

@Composable
fun AiAssistantScreen(
    viewModel: AppViewModel,
) {
    val uiState by viewModel.state.collectAsState()

    AiAssistantContent(
        state = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
private fun AiAssistantContent(
    state: UiState,
    onAction: (UiAction) -> Unit,
) {
    CyberpunkTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CyberpunkColors.DarkBackground,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScreenHeader()

                PromptWithConfigRow(state = state, onAction = onAction)

                SubmitButton(
                    isLoading = state.isLoading,
                    onClick = { onAction(UiAction.Submit) },
                )

                if (state.isLoading) {
                    StatusPanel(modifier = Modifier.fillMaxWidth())
                }

                state.response?.let { response ->
                    OutputPanel(
                        response = response,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }

        state.errorPopup?.let { error ->
            ErrorDialog(
                error = error,
                onDismiss = { onAction(UiAction.DismissError) },
            )
        }
    }
}

@Composable
private fun ScreenHeader() {
    Column {
        Text(
            text = "AI ASSISTANT",
            style = MaterialTheme.typography.headlineSmall,
            color = CyberpunkColors.Yellow,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            thickness = 2.dp,
            color = CyberpunkColors.Yellow.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun PromptWithConfigRow(
    state: UiState,
    onAction: (UiAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CyberpunkTextField(
            value = state.prompt,
            onValueChange = { onAction(UiAction.PromptChanged(it)) },
            label = "// ENTER PROMPT",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            minLines = 8,
        )

        Column(
            modifier = Modifier.width(220.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentModeSelector(
                selected = state.agentMode,
                onModeSelected = { onAction(UiAction.AgentModeChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            ConfigPanel(
                maxOutputTokens = state.maxOutputTokens,
                onMaxOutputTokensChanged = { onAction(UiAction.MaxOutputTokensChanged(it)) },
                temperature = state.temperature,
                onTemperatureChanged = { onAction(UiAction.TemperatureChanged(it)) },
                stopSequences = state.stopSequences,
                onStopSequencesChanged = { onAction(UiAction.StopSequencesChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
