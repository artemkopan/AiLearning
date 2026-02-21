package io.artemkopan.ai.sharedui.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import io.artemkopan.ai.sharedcontract.ChatConfigDto
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.state.ChatState
import io.artemkopan.ai.sharedui.state.UiAction
import io.artemkopan.ai.sharedui.state.UiState
import io.artemkopan.ai.sharedui.ui.component.AgentModeSelector
import io.artemkopan.ai.sharedui.ui.component.ChatSidePanel
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
    val activeChat = state.activeChatId?.let { state.chats[it] }
    val orderedChats = state.chatOrder.mapNotNull { state.chats[it] }

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

                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Left column — chat list
                    ChatSidePanel(
                        chats = orderedChats,
                        activeChatId = state.activeChatId,
                        onChatSelected = { onAction(UiAction.SelectChat(it)) },
                        onChatClosed = { onAction(UiAction.CloseChat(it)) },
                        onNewChatClicked = { onAction(UiAction.CreateChat) },
                        modifier = Modifier.width(160.dp).fillMaxHeight(),
                    )

                    // Center column — prompt + output
                    if (activeChat != null) {
                        CenterPromptColumn(
                            chat = activeChat,
                            onAction = onAction,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }

                    // Right column — settings
                    if (activeChat != null) {
                        SettingsColumn(
                            chat = activeChat,
                            chatConfig = state.chatConfig,
                            onAction = onAction,
                            modifier = Modifier.width(220.dp).fillMaxHeight(),
                        )
                    }
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
private fun CenterPromptColumn(
    chat: ChatState,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val hasResponse = chat.response != null

        CyberpunkTextField(
            value = chat.prompt,
            onValueChange = { onAction(UiAction.PromptChanged(it)) },
            label = "// ENTER PROMPT",
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasResponse) Modifier.heightIn(max = 200.dp) else Modifier.weight(1f)),
            minLines = 8,
        )

        SubmitButton(
            isLoading = chat.isLoading,
            onClick = { onAction(UiAction.Submit) },
        )

        if (chat.isLoading) {
            StatusPanel(modifier = Modifier.fillMaxWidth())
        }

        chat.response?.let { response ->
            OutputPanel(
                response = response,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun SettingsColumn(
    chat: ChatState,
    chatConfig: ChatConfigDto?,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AgentModeSelector(
            selected = chat.agentMode,
            onModeSelected = { onAction(UiAction.AgentModeChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        ConfigPanel(
            model = chat.model,
            onModelChanged = { onAction(UiAction.ModelChanged(it)) },
            maxOutputTokens = chat.maxOutputTokens,
            onMaxOutputTokensChanged = { onAction(UiAction.MaxOutputTokensChanged(it)) },
            temperature = chat.temperature,
            onTemperatureChanged = { onAction(UiAction.TemperatureChanged(it)) },
            stopSequences = chat.stopSequences,
            onStopSequencesChanged = { onAction(UiAction.StopSequencesChanged(it)) },
            models = chatConfig?.models.orEmpty(),
            temperaturePlaceholder = chatConfig?.let {
                "${it.temperatureMin} – ${it.temperatureMax}"
            } ?: "0.0 – 2.0",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
