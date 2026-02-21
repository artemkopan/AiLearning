package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.ProjectInfo
import io.artemkopan.ai.sharedui.gateway.TerminalGateway

class LoadProjectsUseCase(private val gateway: TerminalGateway) {
    suspend operator fun invoke(): Result<List<ProjectInfo>> = gateway.getProjects()
}
