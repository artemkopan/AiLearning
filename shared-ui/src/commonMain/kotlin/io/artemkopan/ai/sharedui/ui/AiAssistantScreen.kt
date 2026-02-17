package io.artemkopan.ai.sharedui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.artemkopan.ai.sharedui.state.AppState

@Composable
fun AiAssistantScreen(appState: AppState) {
    val uiState by appState.state.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AiAssistant Prompt", style = MaterialTheme.typography.headlineSmall)

                OutlinedTextField(
                    value = uiState.prompt,
                    onValueChange = appState::onPromptChanged,
                    label = { Text("Prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                )

                Button(
                    onClick = appState::submit,
                    enabled = !uiState.isLoading,
                ) {
                    Text(if (uiState.isLoading) "Generating..." else "Send")
                }

                uiState.response?.let { response ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(response.text)
                            Text(
                                "Provider: ${response.provider}, Model: ${response.model}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        uiState.errorPopup?.let { error ->
            AlertDialog(
                onDismissRequest = appState::dismissError,
                confirmButton = {
                    Button(onClick = appState::dismissError) {
                        Text("Close")
                    }
                },
                title = { Text(error.title) },
                text = { Text(error.message) },
            )
        }
    }
}
