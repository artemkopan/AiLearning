package io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.feature.conversationcolumn.command.ConversationCommandContext
import io.artemkopan.ai.sharedui.feature.conversationcolumn.command.ConversationCommandRegistry
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.CommandPaletteUiModel
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.ConversationColumnUiModel
import io.artemkopan.ai.sharedui.usecase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConversationColumnViewModel(
    private val agentId: AgentId,
    private val observeAgentSliceUseCase: ObserveAgentSliceUseCase,
    private val observeSessionStateUseCase: ObserveSessionStateUseCase,
    private val updateDraftMessageActionUseCase: UpdateDraftMessageActionUseCase,
    private val submitMessageActionUseCase: SubmitMessageActionUseCase,
    private val stopQueueActionUseCase: StopQueueActionUseCase,
    private val createBranchActionUseCase: CreateBranchActionUseCase,
    private val findSlashTokenBoundsUseCase: FindSlashTokenBoundsUseCase,
    private val insertCommandTokenUseCase: InsertCommandTokenUseCase,
    private val buildCommandPaletteItemsUseCase: BuildCommandPaletteItemsUseCase,
    private val buildConversationDisplayMessagesUseCase: BuildConversationDisplayMessagesUseCase,
    private val buildConversationStatusTextUseCase: BuildConversationStatusTextUseCase,
    private val conversationCommandRegistry: ConversationCommandRegistry,
) : ViewModel() {

    private val messageInputValue = MutableStateFlow(TextFieldValue(""))
    private val isCommandPaletteDismissed = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            observeAgentSliceUseCase(agentId)
                .map { slice -> slice?.agent?.draftMessage.orEmpty() }
                .distinctUntilChanged()
                .collect { draftMessage ->
                    val current = messageInputValue.value
                    if (current.text != draftMessage) {
                        messageInputValue.value = TextFieldValue(
                            text = draftMessage,
                            selection = TextRange(draftMessage.length),
                        )
                        isCommandPaletteDismissed.value = false
                    }
                }
        }
    }

    val state: StateFlow<ConversationColumnUiModel> = combine(
        observeAgentSliceUseCase(agentId),
        observeSessionStateUseCase(),
        messageInputValue,
        isCommandPaletteDismissed,
    ) { slice, session, inputValue, paletteDismissed ->
        val agent = slice?.agent
        val allAgents = session.agentOrder.mapNotNull { id -> session.agents[id] }
        val queuedMessages = slice?.queuedMessages.orEmpty()
        val slashTokenBounds = findSlashTokenBoundsUseCase(inputValue)

        val commandDefinitions = conversationCommandRegistry.resolve(
            ConversationCommandContext(
                activeAgentId = session.activeAgentId,
                allAgents = allAgents,
            )
        )
        val paletteItems = buildCommandPaletteItemsUseCase(commandDefinitions, slashTokenBounds)

        ConversationColumnUiModel(
            agent = agent,
            allAgents = allAgents,
            queuedMessages = queuedMessages,
            inputValue = inputValue,
            displayMessages = buildConversationDisplayMessagesUseCase(
                messages = agent?.messages.orEmpty(),
                queuedMessages = queuedMessages,
            ),
            statusText = agent?.let {
                buildConversationStatusTextUseCase(
                    agent = it,
                    queuedMessages = queuedMessages,
                    activeTask = session.taskByAgent[agentId],
                )
            },
            commandPalette = CommandPaletteUiModel(
                visible = slashTokenBounds != null && !paletteDismissed,
                items = paletteItems,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ConversationColumnUiModel(),
    )

    fun onMessageInputChanged(value: TextFieldValue) {
        messageInputValue.value = value
        isCommandPaletteDismissed.value = false
        updateDraftMessageActionUseCase(agentId, value.text)
    }

    fun onCommandSelected(commandId: String) {
        val selected = state.value.commandPalette.items.firstOrNull { it.id == commandId } ?: return
        val currentValue = messageInputValue.value
        val slashTokenBounds = findSlashTokenBoundsUseCase(currentValue)
        val updated = insertCommandTokenUseCase(
            value = currentValue,
            token = selected.token,
            slashTokenBounds = slashTokenBounds,
        )
        messageInputValue.value = updated
        isCommandPaletteDismissed.value = false
        updateDraftMessageActionUseCase(agentId, updated.text)
    }

    fun onCommandDismissed() {
        isCommandPaletteDismissed.value = true
    }

    fun onSubmit() {
        submitMessageActionUseCase(agentId)
    }

    fun onStopQueue() {
        stopQueueActionUseCase(agentId)
    }

    fun onCreateBranch(checkpointMessageId: String, fallbackMessageId: String) {
        createBranchActionUseCase(
            agentId = agentId,
            checkpointMessageId = checkpointMessageId,
            name = "branch-${fallbackMessageId.takeLast(6)}",
        )
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
