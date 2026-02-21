package io.artemkopan.ai.sharedui.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import io.artemkopan.ai.sharedui.ui.component.InsertFromChatPopup
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
                            otherChats = orderedChats.filter { it.id != activeChat.id },
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
    otherChats: List<ChatState>,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val hasResponse = chat.response != null

        var textFieldValue by remember(chat.id) {
            mutableStateOf(TextFieldValue(text = chat.prompt, selection = TextRange(chat.prompt.length)))
        }
        // Sync external changes (e.g. tab switch populating prompt from ViewModel)
        if (textFieldValue.text != chat.prompt) {
            textFieldValue = TextFieldValue(text = chat.prompt, selection = TextRange(chat.prompt.length))
        }

        var showInsertPopup by remember { mutableStateOf(false) }
        var slashPosition by remember { mutableStateOf(-1) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasResponse) Modifier.heightIn(max = 200.dp) else Modifier.weight(1f)),
        ) {
            CyberpunkTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val oldText = textFieldValue.text
                    textFieldValue = newValue

                    // Auto-dismiss if slash was removed
                    if (showInsertPopup && slashPosition >= 0) {
                        if (slashPosition >= newValue.text.length || newValue.text[slashPosition] != '/') {
                            showInsertPopup = false
                            slashPosition = -1
                        }
                    }

                    // Detect `/` trigger: text grew by 1, char at cursor-1 is `/`,
                    // preceded by whitespace/newline or at start of text
                    if (!showInsertPopup && newValue.text.length == oldText.length + 1) {
                        val cursor = newValue.selection.start
                        if (cursor > 0 && newValue.text[cursor - 1] == '/') {
                            val isAtStart = cursor == 1
                            val precededByWhitespace = cursor >= 2 && newValue.text[cursor - 2].let {
                                it == ' ' || it == '\n' || it == '\r' || it == '\t'
                            }
                            if (isAtStart || precededByWhitespace) {
                                slashPosition = cursor - 1
                                showInsertPopup = true
                            }
                        }
                    }

                    onAction(UiAction.PromptChanged(newValue.text))
                },
                label = "// ENTER PROMPT",
                modifier = Modifier.fillMaxSize(),
                minLines = 8,
            )

            InsertFromChatPopup(
                expanded = showInsertPopup,
                onDismiss = {
                    showInsertPopup = false
                    slashPosition = -1
                },
                otherChats = otherChats,
                onInsert = { content ->
                    if (slashPosition >= 0) {
                        textFieldValue = insertContent(content, slashPosition, textFieldValue)
                        onAction(UiAction.PromptChanged(textFieldValue.text))
                        showInsertPopup = false
                        slashPosition = -1
                    }
                },
            )
        }

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

private fun insertContent(content: String, slashPos: Int, current: TextFieldValue): TextFieldValue {
    val text = current.text
    val before = text.substring(0, slashPos)
    val after = text.substring(slashPos + 1)
    val newText = before + content + after
    return TextFieldValue(text = newText, selection = TextRange(before.length + content.length))
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
