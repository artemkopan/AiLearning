package io.artemkopan.ai.sharedui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.artemkopan.ai.sharedcontract.ChatInfo
import io.artemkopan.ai.sharedcontract.ChatStatus
import io.artemkopan.ai.sharedcontract.ProjectInfo
import io.artemkopan.ai.sharedui.gateway.WsEvent
import io.artemkopan.ai.sharedui.usecase.CreateChatUseCase
import io.artemkopan.ai.sharedui.usecase.LoadChatsUseCase
import io.artemkopan.ai.sharedui.usecase.LoadProjectsUseCase
import io.artemkopan.ai.sharedui.usecase.ObserveStatusEventsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatTab(
    val chatId: String,
    val projectPath: String,
    val projectName: String,
    val status: ChatStatus = ChatStatus.idle,
)

data class ErrorPopupState(
    val title: String,
    val message: String,
)

data class UiState(
    val projects: List<ProjectInfo> = emptyList(),
    val chats: List<ChatTab> = emptyList(),
    val activeChatId: String? = null,
    val error: ErrorPopupState? = null,
)

sealed interface UiAction {
    data class CreateChat(val projectPath: String) : UiAction
    data class SelectChat(val chatId: String) : UiAction
    data class CloseChat(val chatId: String) : UiAction
    data class StatusUpdated(val chatId: String, val status: ChatStatus) : UiAction
    data object DismissError : UiAction
}

class AppViewModel(
    private val loadProjects: LoadProjectsUseCase,
    private val createChat: CreateChatUseCase,
    private val loadChats: LoadChatsUseCase,
    private val observeStatusEvents: ObserveStatusEventsUseCase,
) : ViewModel() {

    private val log = Logger.withTag("AppViewModel")
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        log.d { "AppViewModel initialized" }
        loadInitialData()
        startEventListener()
    }

    fun onAction(action: UiAction) {
        when (action) {
            is UiAction.CreateChat -> handleCreateChat(action.projectPath)
            is UiAction.SelectChat -> handleSelectChat(action.chatId)
            is UiAction.CloseChat -> handleCloseChat(action.chatId)
            is UiAction.StatusUpdated -> handleStatusUpdated(action.chatId, action.status)
            is UiAction.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            loadProjects()
                .onSuccess { projects ->
                    log.i { "Loaded ${projects.size} projects" }
                    _state.update { it.copy(projects = projects) }
                }
                .onFailure { e ->
                    log.e(e) { "Failed to load projects" }
                }
        }
        viewModelScope.launch {
            loadChats()
                .onSuccess { chats ->
                    log.i { "Loaded ${chats.size} existing chats" }
                    val tabs = chats.map { chat ->
                        ChatTab(
                            chatId = chat.chatId,
                            projectPath = chat.projectPath,
                            projectName = chat.projectPath.substringAfterLast('/'),
                            status = chat.status,
                        )
                    }
                    _state.update { state ->
                        state.copy(
                            chats = tabs,
                            activeChatId = state.activeChatId ?: tabs.firstOrNull()?.chatId,
                        )
                    }
                }
                .onFailure { e ->
                    log.e(e) { "Failed to load chats" }
                }
        }
    }

    private fun startEventListener() {
        viewModelScope.launch {
            while (true) {
                try {
                    observeStatusEvents { event ->
                        when (event) {
                            is WsEvent.Status -> onAction(
                                UiAction.StatusUpdated(event.event.chatId, event.event.status)
                            )
                            is WsEvent.ChatCreated -> handleChatCreatedEvent(event.event.chat)
                        }
                    }
                } catch (e: Exception) {
                    log.w(e) { "Event listener disconnected, retrying..." }
                }
                delay(3000)
            }
        }
    }

    private fun handleCreateChat(projectPath: String) {
        viewModelScope.launch {
            createChat(projectPath)
                .onSuccess { chatId ->
                    log.i { "Created chat $chatId" }
                    val projectName = projectPath.substringAfterLast('/')
                    val tab = ChatTab(
                        chatId = chatId,
                        projectPath = projectPath,
                        projectName = projectName,
                    )
                    _state.update { state ->
                        state.copy(
                            chats = state.chats + tab,
                            activeChatId = chatId,
                        )
                    }
                }
                .onFailure { e ->
                    log.e(e) { "Failed to create chat" }
                    _state.update {
                        it.copy(
                            error = ErrorPopupState(
                                title = "Failed to Create Chat",
                                message = e.message ?: "Unknown error",
                            )
                        )
                    }
                }
        }
    }

    private fun handleChatCreatedEvent(chat: ChatInfo) {
        _state.update { state ->
            // Skip if we already have this chat (e.g. we created it ourselves)
            if (state.chats.any { it.chatId == chat.chatId }) return@update state
            val tab = ChatTab(
                chatId = chat.chatId,
                projectPath = chat.projectPath,
                projectName = chat.projectPath.substringAfterLast('/'),
                status = chat.status,
            )
            state.copy(chats = state.chats + tab)
        }
    }

    private fun handleSelectChat(chatId: String) {
        _state.update { it.copy(activeChatId = chatId) }
    }

    private fun handleCloseChat(chatId: String) {
        _state.update { state ->
            val newChats = state.chats.filter { it.chatId != chatId }
            val newActiveId = if (state.activeChatId == chatId) {
                val oldIndex = state.chats.indexOfFirst { it.chatId == chatId }
                when {
                    newChats.isEmpty() -> null
                    oldIndex >= newChats.size -> newChats.last().chatId
                    else -> newChats[oldIndex].chatId
                }
            } else {
                state.activeChatId
            }
            state.copy(chats = newChats, activeChatId = newActiveId)
        }
    }

    private fun handleStatusUpdated(chatId: String, status: ChatStatus) {
        _state.update { state ->
            state.copy(
                chats = state.chats.map { tab ->
                    if (tab.chatId == chatId) tab.copy(status = status) else tab
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        log.d { "AppViewModel cleared" }
    }
}
