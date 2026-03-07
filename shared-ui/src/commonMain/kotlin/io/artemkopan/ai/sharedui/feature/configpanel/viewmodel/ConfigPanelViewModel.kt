package io.artemkopan.ai.sharedui.feature.configpanel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.feature.configpanel.model.ConfigPanelUiModel
import io.artemkopan.ai.sharedui.usecase.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ConfigPanelViewModel(
    private val agentId: AgentId,
    private val observeAgentSliceUseCase: ObserveAgentSliceUseCase,
    private val updateModelActionUseCase: UpdateModelActionUseCase,
    private val updateMaxOutputTokensActionUseCase: UpdateMaxOutputTokensActionUseCase,
    private val updateTemperatureActionUseCase: UpdateTemperatureActionUseCase,
    private val updateStopSequencesActionUseCase: UpdateStopSequencesActionUseCase,
) : ViewModel() {

    val state: StateFlow<ConfigPanelUiModel> = observeAgentSliceUseCase(agentId)
        .map { slice ->
            val agent = slice?.agent
            val config = slice?.agentConfig
            ConfigPanelUiModel(
                model = agent?.model.orEmpty(),
                maxOutputTokens = agent?.maxOutputTokens.orEmpty(),
                temperature = agent?.temperature.orEmpty(),
                stopSequences = agent?.stopSequences.orEmpty(),
                models = config?.models.orEmpty(),
                temperaturePlaceholder = config?.let {
                    "${it.temperatureMin} - ${it.temperatureMax}"
                } ?: "0.0 - 2.0",
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ConfigPanelUiModel(),
        )

    fun onModelChanged(value: String) {
        updateModelActionUseCase(agentId, value)
    }

    fun onMaxOutputTokensChanged(value: String) {
        updateMaxOutputTokensActionUseCase(agentId, value)
    }

    fun onTemperatureChanged(value: String) {
        updateTemperatureActionUseCase(agentId, value)
    }

    fun onStopSequencesChanged(value: String) {
        updateStopSequencesActionUseCase(agentId, value)
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
