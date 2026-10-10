package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.model.EndpointProfile
import com.example.data.model.ProfileCategory
import com.example.data.model.Workspace
import com.example.data.repository.ConversationRepository
import com.example.data.repository.EndpointRepository
import com.example.data.repository.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val workspaces: List<Workspace> = emptyList(),
    val currentWorkspace: Workspace? = null,
    val conversations: List<Conversation> = emptyList(),
    val profiles: List<EndpointProfile> = emptyList(),
    val activeProfilesByCategory: Map<String, String> = emptyMap(),
    val selectedTab: Int = 0, // 0: Chats, 1: Model Profiles
    val isCreateWorkspaceDialogOpen: Boolean = false,
    val newWorkspaceName: String = "",
    val renameConversationDialogTarget: Conversation? = null,
    val newConversationTitle: String = ""
)

class HomeViewModel(
    private val workspaceRepository: WorkspaceRepository,
    private val conversationRepository: ConversationRepository,
    private val endpointRepository: EndpointRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        initializeData()
    }

    private fun initializeData() {
        viewModelScope.launch {
            // Ensure default workspace exists
            val defaultWs = workspaceRepository.ensureDefaultWorkspace()
            _uiState.update { it.copy(currentWorkspace = defaultWs) }

            // Observe workspaces
            launch {
                workspaceRepository.allWorkspaces.collect { list ->
                    _uiState.update { state ->
                        state.copy(
                            workspaces = list,
                            currentWorkspace = state.currentWorkspace ?: list.firstOrNull()
                        )
                    }
                }
            }

            // Observe endpoint profiles
            launch {
                endpointRepository.allProfiles.collect { profiles ->
                    _uiState.update { it.copy(profiles = profiles) }
                }
            }

            // Observe active category mapping
            launch {
                endpointRepository.activeCategoryProfiles.collect { map ->
                    _uiState.update { it.copy(activeProfilesByCategory = map) }
                }
            }

            // Observe conversations for active workspace
            launch {
                _uiState.collect { state ->
                    val wsId = state.currentWorkspace?.id
                    if (wsId != null) {
                        conversationRepository.getConversations(wsId).collect { convs ->
                            _uiState.update { it.copy(conversations = convs) }
                        }
                    }
                }
            }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectWorkspace(workspace: Workspace) {
        _uiState.update { it.copy(currentWorkspace = workspace) }
        viewModelScope.launch {
            conversationRepository.getConversations(workspace.id).collect { convs ->
                _uiState.update { it.copy(conversations = convs) }
            }
        }
    }

    fun openCreateWorkspaceDialog(open: Boolean) {
        _uiState.update { it.copy(isCreateWorkspaceDialogOpen = open, newWorkspaceName = "") }
    }

    fun updateNewWorkspaceName(name: String) {
        _uiState.update { it.copy(newWorkspaceName = name) }
    }

    fun createWorkspace() {
        val name = _uiState.value.newWorkspaceName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val created = workspaceRepository.createWorkspace(name)
            _uiState.update {
                it.copy(
                    isCreateWorkspaceDialogOpen = false,
                    currentWorkspace = created
                )
            }
        }
    }

    fun createNewChat(onConversationCreated: (Conversation) -> Unit) {
        val wsId = _uiState.value.currentWorkspace?.id ?: return
        val profiles = _uiState.value.profiles
        val activeMap = _uiState.value.activeProfilesByCategory
        // Find default profile or first available
        val defaultProfile = profiles.firstOrNull { it.id == activeMap[ProfileCategory.ASTRA] }
            ?: profiles.firstOrNull { it.id == activeMap[ProfileCategory.FLASH] }
            ?: profiles.firstOrNull()

        viewModelScope.launch {
            val conv = conversationRepository.createConversation(
                workspaceId = wsId,
                title = "New Chat",
                profileId = defaultProfile?.id ?: ""
            )
            onConversationCreated(conv)
        }
    }

    fun openRenameConversationDialog(conversation: Conversation?) {
        _uiState.update {
            it.copy(
                renameConversationDialogTarget = conversation,
                newConversationTitle = conversation?.title ?: ""
            )
        }
    }

    fun updateNewConversationTitle(title: String) {
        _uiState.update { it.copy(newConversationTitle = title) }
    }

    fun saveRenamedConversation() {
        val target = _uiState.value.renameConversationDialogTarget ?: return
        val newTitle = _uiState.value.newConversationTitle.trim()
        if (newTitle.isEmpty()) return
        viewModelScope.launch {
            conversationRepository.updateTitle(target.id, newTitle)
            _uiState.update { it.copy(renameConversationDialogTarget = null) }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            endpointRepository.deleteProfile(id)
        }
    }

    fun setActiveProfileForCategory(category: String, profileId: String) {
        viewModelScope.launch {
            endpointRepository.setActiveProfileForCategory(category, profileId)
        }
    }
}
