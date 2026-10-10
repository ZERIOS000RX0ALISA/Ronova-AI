package com.example.ui.screens.addmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ConnectionStatus
import com.example.data.model.EndpointProfile
import com.example.data.model.ModelInfo
import com.example.data.model.ProfileCapabilities
import com.example.data.model.ProfileCategory
import com.example.data.model.ProviderType
import com.example.data.repository.EndpointRepository
import com.example.domain.tester.ConnectionTester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionType {
    CLOUD_API,
    LOCAL_CLOUD
}

data class AddModelUiState(
    val connectionType: ConnectionType = ConnectionType.LOCAL_CLOUD,
    val providerType: String = ProviderType.OPENAI_COMPATIBLE,
    val name: String = "Local Ollama",
    val baseUrl: String = "http://10.0.2.2:11434/v1",
    val apiKey: String = "",
    val isApiKeyVisible: Boolean = false,
    val isTesting: Boolean = false,
    val testError: String? = null,
    val testSuccess: ConnectionStatus? = null,
    val discoveredModels: List<ModelInfo> = emptyList(),
    val selectedModel: String = "",
    val category: String = ProfileCategory.ASTRA,
    val isSaved: Boolean = false,
    val hasTestedSuccessfully: Boolean = false
)

class AddModelViewModel(
    private val endpointRepository: EndpointRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddModelUiState())
    val uiState: StateFlow<AddModelUiState> = _uiState.asStateFlow()

    private val connectionTester = ConnectionTester()

    fun setProviderType(provider: String) {
        _uiState.update { current ->
            when (provider) {
                ProviderType.GEMINI -> current.copy(
                    providerType = provider,
                    connectionType = ConnectionType.CLOUD_API,
                    name = "Google Gemini",
                    baseUrl = "https://generativelanguage.googleapis.com",
                    category = ProfileCategory.FLASH,
                    hasTestedSuccessfully = false,
                    testSuccess = null,
                    testError = null,
                    discoveredModels = emptyList(),
                    selectedModel = "gemini-2.5-flash"
                )
                ProviderType.ANTHROPIC -> current.copy(
                    providerType = provider,
                    connectionType = ConnectionType.CLOUD_API,
                    name = "Anthropic Claude",
                    baseUrl = "https://api.anthropic.com/v1",
                    category = ProfileCategory.SYSTEM,
                    hasTestedSuccessfully = false,
                    testSuccess = null,
                    testError = null,
                    discoveredModels = emptyList(),
                    selectedModel = "claude-3-7-sonnet-20250219"
                )
                else -> current.copy(
                    providerType = ProviderType.OPENAI_COMPATIBLE,
                    connectionType = current.connectionType,
                    name = if (current.connectionType == ConnectionType.LOCAL_CLOUD) "Local Ollama" else "OpenAI Cloud",
                    baseUrl = if (current.connectionType == ConnectionType.LOCAL_CLOUD) "http://10.0.2.2:11434/v1" else "https://api.openai.com/v1",
                    category = if (current.connectionType == ConnectionType.LOCAL_CLOUD) ProfileCategory.ASTRA else ProfileCategory.FLASH,
                    hasTestedSuccessfully = false,
                    testSuccess = null,
                    testError = null,
                    discoveredModels = emptyList(),
                    selectedModel = ""
                )
            }
        }
    }

    fun setConnectionType(type: ConnectionType) {
        _uiState.update { current ->
            when (type) {
                ConnectionType.CLOUD_API -> current.copy(
                    connectionType = type,
                    providerType = if (current.providerType == ProviderType.OPENAI_COMPATIBLE) ProviderType.OPENAI_COMPATIBLE else current.providerType,
                    name = if (current.name == "Local Ollama") "OpenAI Cloud" else current.name,
                    baseUrl = if (current.baseUrl.contains("11434")) "https://api.openai.com/v1" else current.baseUrl,
                    category = if (current.category == ProfileCategory.ASTRA) ProfileCategory.FLASH else current.category,
                    hasTestedSuccessfully = false,
                    testSuccess = null,
                    testError = null,
                    discoveredModels = emptyList()
                )
                ConnectionType.LOCAL_CLOUD -> current.copy(
                    connectionType = type,
                    providerType = ProviderType.OPENAI_COMPATIBLE,
                    name = if (current.name == "OpenAI Cloud" || current.name.contains("Gemini") || current.name.contains("Claude")) "Local Ollama" else current.name,
                    baseUrl = if (current.baseUrl.contains("openai.com") || current.baseUrl.contains("googleapis.com") || current.baseUrl.contains("anthropic.com")) "http://10.0.2.2:11434/v1" else current.baseUrl,
                    category = ProfileCategory.ASTRA,
                    hasTestedSuccessfully = false,
                    testSuccess = null,
                    testError = null,
                    discoveredModels = emptyList()
                )
            }
        }
    }

    fun applyPreset(
        name: String,
        url: String,
        category: String,
        provider: String = ProviderType.OPENAI_COMPATIBLE,
        suggestedModel: String = ""
    ) {
        _uiState.update {
            it.copy(
                name = name,
                baseUrl = url,
                category = category,
                providerType = provider,
                connectionType = if (provider != ProviderType.OPENAI_COMPATIBLE || url.startsWith("https://")) ConnectionType.CLOUD_API else ConnectionType.LOCAL_CLOUD,
                hasTestedSuccessfully = false,
                testSuccess = null,
                testError = null,
                discoveredModels = emptyList(),
                selectedModel = suggestedModel
            )
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateBaseUrl(url: String) {
        _uiState.update {
            it.copy(
                baseUrl = url,
                hasTestedSuccessfully = false,
                testSuccess = null,
                testError = null
            )
        }
    }

    fun updateApiKey(key: String) {
        _uiState.update {
            it.copy(
                apiKey = key,
                hasTestedSuccessfully = false,
                testSuccess = null,
                testError = null
            )
        }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
    }

    fun updateCategory(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    fun testConnection() {
        val state = _uiState.value
        val tempProfile = EndpointProfile(
            name = state.name.ifBlank { "Test Endpoint" },
            category = state.category,
            providerType = state.providerType,
            baseUrl = state.baseUrl.trim(),
            apiKey = state.apiKey.trim().ifEmpty { null },
            selectedModel = state.selectedModel.trim()
        )

        _uiState.update {
            it.copy(
                isTesting = true,
                testError = null,
                testSuccess = null
            )
        }

        viewModelScope.launch {
            val result = connectionTester.test(tempProfile)
            result.fold(
                onSuccess = { status ->
                    val defaultSelection = status.models.firstOrNull()?.id ?: ""
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testSuccess = status,
                            testError = null,
                            discoveredModels = status.models,
                            selectedModel = if (it.selectedModel.isNotEmpty() && status.models.any { m -> m.id == it.selectedModel }) {
                                it.selectedModel
                            } else {
                                defaultSelection
                            },
                            hasTestedSuccessfully = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testError = error.localizedMessage ?: "Connection failed with unknown error.",
                            testSuccess = null,
                            hasTestedSuccessfully = false
                        )
                    }
                }
            )
        }
    }

    fun cancelTest() {
        connectionTester.cancel()
        _uiState.update {
            it.copy(
                isTesting = false,
                testError = "Connection test cancelled by user."
            )
        }
    }

    fun saveProfile(onSuccess: (EndpointProfile) -> Unit) {
        val state = _uiState.value
        if (!state.hasTestedSuccessfully) return
        if (state.selectedModel.isBlank()) return

        val selectedModelInfo = state.discoveredModels.firstOrNull { it.id == state.selectedModel }
        val isGemini = state.providerType == ProviderType.GEMINI
        val isAnthropic = state.providerType == ProviderType.ANTHROPIC
        val supportsVision = selectedModelInfo?.supportsVision ?: (isGemini || isAnthropic || state.selectedModel.contains("vision", true) || state.selectedModel.contains("4o", true))
        val supportsThinking = selectedModelInfo?.supportsThinking ?: (state.selectedModel.contains("r1", true) || state.selectedModel.contains("3-7", true) || state.selectedModel.contains("thinking", true) || state.selectedModel.contains("2.5", true))

        val profile = EndpointProfile(
            name = state.name.trim().ifEmpty { state.selectedModel },
            category = state.category,
            providerType = state.providerType,
            baseUrl = state.baseUrl.trim(),
            apiKey = state.apiKey.trim().ifEmpty { null },
            selectedModel = state.selectedModel.trim(),
            capabilities = ProfileCapabilities(
                chat = true,
                vision = supportsVision,
                tools = selectedModelInfo?.supportsTools ?: false,
                streaming = true,
                thinking = supportsThinking
            )
        )

        viewModelScope.launch {
            endpointRepository.saveProfile(profile)
            _uiState.update { it.copy(isSaved = true) }
            onSuccess(profile)
        }
    }
}
