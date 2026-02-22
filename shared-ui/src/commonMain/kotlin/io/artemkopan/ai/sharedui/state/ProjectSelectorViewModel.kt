package io.artemkopan.ai.sharedui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.artemkopan.ai.sharedcontract.ProjectInfo
import io.artemkopan.ai.sharedui.usecase.LoadProjectsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectSelectorState(
    val projects: List<ProjectInfo> = emptyList(),
    val searchQuery: String = "",
    val expanded: Boolean = false,
) {
    val filteredProjects: List<ProjectInfo>
        get() = if (searchQuery.isBlank()) projects
        else projects.filter { it.name.contains(searchQuery, ignoreCase = true) }
}

class ProjectSelectorViewModel(
    private val loadProjects: LoadProjectsUseCase,
) : ViewModel() {

    private val log = Logger.withTag("ProjectSelectorVM")
    private val _projects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _expanded = MutableStateFlow(false)

    val state: StateFlow<ProjectSelectorState> = combine(
        _projects, _searchQuery, _expanded,
    ) { projects, query, expanded ->
        ProjectSelectorState(projects = projects, searchQuery = query, expanded = expanded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectSelectorState())

    init {
        loadProjectList()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleExpanded() {
        val newExpanded = !_expanded.value
        _expanded.value = newExpanded
        if (!newExpanded) _searchQuery.value = ""
    }

    fun collapse() {
        _expanded.value = false
        _searchQuery.value = ""
    }

    private fun loadProjectList() {
        viewModelScope.launch {
            loadProjects()
                .onSuccess { projects ->
                    log.i { "Loaded ${projects.size} projects" }
                    _projects.value = projects
                }
                .onFailure { e ->
                    log.e(e) { "Failed to load projects" }
                }
        }
    }
}
