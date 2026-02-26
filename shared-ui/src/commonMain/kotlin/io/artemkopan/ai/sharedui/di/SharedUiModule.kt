package io.artemkopan.ai.sharedui.di

import io.artemkopan.ai.sharedui.gateway.AgentGateway
import io.artemkopan.ai.sharedui.state.AppViewModel
import io.artemkopan.ai.sharedui.usecase.BuildUpdatedConfigWithModelMetadataUseCase
import io.artemkopan.ai.sharedui.usecase.ComputeContextLeftLabelUseCase
import io.artemkopan.ai.sharedui.usecase.EnrichRuntimeStateUseCase
import io.artemkopan.ai.sharedui.usecase.FilterTemperatureInputUseCase
import io.artemkopan.ai.sharedui.usecase.MapSnapshotToUiStateUseCase
import io.artemkopan.ai.sharedui.usecase.NormalizeAgentsForConfigUseCase
import io.artemkopan.ai.sharedui.usecase.NormalizeModelUseCase
import io.artemkopan.ai.sharedui.usecase.ObserveActiveModelSelectionUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedUiModule = module {
    single { NormalizeModelUseCase() }
    single { FilterTemperatureInputUseCase() }
    single { NormalizeAgentsForConfigUseCase(normalizeModelUseCase = get()) }
    single { MapSnapshotToUiStateUseCase(normalizeModelUseCase = get()) }
    single { ObserveActiveModelSelectionUseCase(normalizeModelUseCase = get()) }
    single { BuildUpdatedConfigWithModelMetadataUseCase() }
    single { ComputeContextLeftLabelUseCase() }
    single { EnrichRuntimeStateUseCase(computeContextLeftLabelUseCase = get()) }

    viewModel {
        AppViewModel(
            gateway = get(),
            normalizeModelUseCase = get(),
            filterTemperatureInputUseCase = get(),
            normalizeAgentsForConfigUseCase = get(),
            mapSnapshotToUiStateUseCase = get(),
            observeActiveModelSelectionUseCase = get(),
            buildUpdatedConfigWithModelMetadataUseCase = get(),
            enrichRuntimeStateUseCase = get(),
        )
    }
}

fun sharedUiModules(gateway: AgentGateway) = listOf(
    module { single<AgentGateway> { gateway } },
    sharedUiModule,
)
