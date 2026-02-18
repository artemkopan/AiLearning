package io.artemkopan.ai.sharedui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.state.UiAction
import io.artemkopan.ai.sharedui.state.UiState

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
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AiAssistant Prompt", style = MaterialTheme.typography.headlineSmall)

                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.prompt,
                        onValueChange = { onAction(UiAction.PromptChanged(it)) },
                        label = { Text("Prompt") },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        minLines = 8,
                    )

                    Column(
                        modifier = Modifier.width(200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Configuration", style = MaterialTheme.typography.labelLarge)

                        OutlinedTextField(
                            value = state.maxOutputTokens,
                            onValueChange = { onAction(UiAction.MaxOutputTokensChanged(it)) },
                            label = { Text("Max Output Tokens") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )

                        OutlinedTextField(
                            value = state.stopSequences,
                            onValueChange = { onAction(UiAction.StopSequencesChanged(it)) },
                            label = { Text("Stop Sequences") },
                            placeholder = { Text("comma, separated") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }

                Button(
                    onClick = { onAction(UiAction.Submit) },
                    enabled = !state.isLoading,
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating...")
                    } else {
                        Text("Send")
                    }
                }

                if (state.isLoading) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Generating response...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                state.response?.let { response ->
                    Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(response.text)
                            Text(
                                "Provider: ${response.provider}, Model: ${response.model}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            response.usage?.let { usage ->
                                Text(
                                    "Tokens — input: ${usage.inputTokens}, output: ${usage.outputTokens}, total: ${usage.totalTokens}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        state.errorPopup?.let { error ->
            AlertDialog(
                onDismissRequest = { onAction(UiAction.DismissError) },
                confirmButton = {
                    Button(onClick = { onAction(UiAction.DismissError) }) {
                        Text("Close")
                    }
                },
                title = { Text(error.title) },
                text = { Text(error.message) },
            )
        }
    }
}
