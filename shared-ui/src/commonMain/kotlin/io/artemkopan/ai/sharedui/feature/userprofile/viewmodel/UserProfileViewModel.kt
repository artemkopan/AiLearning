package io.artemkopan.ai.sharedui.feature.userprofile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.feature.userprofile.model.ProfilePreset
import io.artemkopan.ai.sharedui.feature.userprofile.model.UserProfileUiModel
import io.artemkopan.ai.sharedui.usecase.UpdateUserProfileActionUseCase
import kotlinx.coroutines.flow.*

class UserProfileViewModel(
    private val sessionStore: AgentSessionStore,
    private val updateUserProfileActionUseCase: UpdateUserProfileActionUseCase,
) : ViewModel() {

    private val _localEdits = MutableStateFlow<UserProfileUiModel?>(null)

    val state: StateFlow<UserProfileUiModel> = combine(
        sessionStore.observeUserProfile(),
        _localEdits,
    ) { serverProfile, localEdits ->
        if (localEdits != null) {
            localEdits
        } else {
            UserProfileUiModel(
                communicationStyle = serverProfile.communicationStyle,
                responseFormat = serverProfile.responseFormat,
                restrictions = serverProfile.restrictions.joinToString("; "),
                customInstructions = serverProfile.customInstructions,
                isDirty = false,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserProfileUiModel(),
    )

    fun onCommunicationStyleChanged(value: String) {
        updateLocal { it.copy(communicationStyle = value) }
    }

    fun onResponseFormatChanged(value: String) {
        updateLocal { it.copy(responseFormat = value) }
    }

    fun onRestrictionsChanged(value: String) {
        updateLocal { it.copy(restrictions = value) }
    }

    fun onCustomInstructionsChanged(value: String) {
        updateLocal { it.copy(customInstructions = value) }
    }

    fun onSaveProfile() {
        val current = state.value
        val restrictions = current.restrictions
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        updateUserProfileActionUseCase(
            communicationStyle = current.communicationStyle,
            responseFormat = current.responseFormat,
            restrictions = restrictions,
            customInstructions = current.customInstructions,
        )
        _localEdits.value = null
    }

    fun onApplyPreset(preset: ProfilePreset) {
        updateUserProfileActionUseCase(
            communicationStyle = preset.communicationStyle,
            responseFormat = preset.responseFormat,
            restrictions = preset.restrictions,
            customInstructions = preset.customInstructions,
        )
        _localEdits.value = null
    }

    private fun updateLocal(transform: (UserProfileUiModel) -> UserProfileUiModel) {
        val current = _localEdits.value ?: state.value
        _localEdits.value = transform(current).copy(isDirty = true)
    }
}
