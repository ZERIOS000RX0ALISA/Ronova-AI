package com.example.ui.screens.chat

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Attachment
import com.example.data.model.Conversation
import com.example.data.model.EndpointProfile
import com.example.data.model.Message
import com.example.data.model.ProfileCategory
import com.example.data.model.StreamChunk
import com.example.data.model.UnifiedChatRequest
import com.example.data.model.UnifiedMessage
import com.example.data.repository.ConversationRepository
import com.example.data.repository.EndpointRepository
import com.example.domain.adapter.ModelAdapter
import com.example.domain.adapter.ModelAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val conversation: Conversation? = null,
    val currentProfile: EndpointProfile? = null,
    val allProfiles: List<EndpointProfile> = emptyList(),
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val attachments: List<Attachment> = emptyList(),
    val isGenerating: Boolean = false,
    val isWaitingForFirstToken: Boolean = false,
    val isReasoningEnabled: Boolean = true,
    val isReasoningActive: Boolean = false,
    val streamingReasoningContent: String = "",
    val streamingAnswerContent: String = "",
    val streamingThoughtDurationSeconds: Int? = null,
    val activeCategory: String = ProfileCategory.ASTRA,
    val errorMessage: String? = null,
    val pasteNotice: String? = null,
    val isModelPickerOpen: Boolean = false,
    val isAttachmentMenuOpen: Boolean = false
)

class ChatViewModel(
    private val conversationId: String,
    private val conversationRepository: ConversationRepository,
    private val endpointRepository: EndpointRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeAdapter: ModelAdapter? = null
    private var streamJob: Job? = null

    init {
        loadConversationAndProfiles()
        observeMessages()
    }

    private fun loadConversationAndProfiles() {
        viewModelScope.launch {
            val conv = conversationRepository.getConversation(conversationId)
            _uiState.update { it.copy(conversation = conv) }

            // Observe all saved profiles
            endpointRepository.allProfiles.collect { profiles ->
                _uiState.update { state ->
                    val profileToUse = profiles.firstOrNull { it.id == state.conversation?.endpointProfileId }
                        ?: profiles.firstOrNull()
                    state.copy(
                        allProfiles = profiles,
                        currentProfile = profileToUse,
                        activeCategory = profileToUse?.category ?: ProfileCategory.ASTRA
                    )
                }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            conversationRepository.getMessages(conversationId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun updateInputText(text: String) {
        val previousText = _uiState.value.inputText
        val addedLength = text.length - previousText.length
        val lineCount = text.lines().size

        // Auto-Paste-to-File threshold:
        // When user pastes a block exceeding 15 lines or > 800 characters,
        // automatically convert to a file attachment to keep the chat input clean.
        if ((lineCount > 15 || addedLength > 800) && text.trim().isNotEmpty()) {
            val detectedFileName = when {
                text.contains("fun ") || text.contains("class ") || text.contains("val ") -> "snippet.kt"
                text.contains("def ") || text.contains("import ") && text.contains(":\n") -> "snippet.py"
                text.startsWith("{") || text.startsWith("[") -> "data.json"
                else -> "pasted_text.txt"
            }
            val attachment = Attachment(
                name = detectedFileName,
                mimeType = "text/plain",
                sizeBytes = text.toByteArray().size.toLong(),
                extractedText = text,
                lineCount = lineCount,
                isImage = false
            )
            _uiState.update {
                it.copy(
                    inputText = "",
                    attachments = it.attachments + attachment,
                    pasteNotice = "Converted large pasted text ($lineCount lines) to $detectedFileName"
                )
            }
            return
        }

        _uiState.update { it.copy(inputText = text) }
    }

    fun addAttachment(attachment: Attachment) {
        _uiState.update { it.copy(attachments = it.attachments + attachment, isAttachmentMenuOpen = false) }
    }

    fun removeAttachment(attachmentId: String) {
        _uiState.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == attachmentId }) }
    }

    fun clearPasteNotice() {
        _uiState.update { it.copy(pasteNotice = null) }
    }

    fun toggleAttachmentMenu(isOpen: Boolean) {
        _uiState.update { it.copy(isAttachmentMenuOpen = isOpen) }
    }

    fun handleImagePicked(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val name = "image_${System.currentTimeMillis().toString().takeLast(6)}.${if (mime.contains("png")) "png" else "jpg"}"
                val attachment = Attachment(
                    name = name,
                    mimeType = mime,
                    sizeBytes = bytes.size.toLong(),
                    uriString = uri.toString(),
                    base64Data = base64,
                    isImage = true
                )
                _uiState.update { it.copy(attachments = it.attachments + attachment, isAttachmentMenuOpen = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load image: ${e.localizedMessage}") }
            }
        }
    }

    fun handleDocumentPicked(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val name = queryFileName(context, uri) ?: "file_${System.currentTimeMillis().toString().takeLast(4)}.txt"
                val mime = context.contentResolver.getType(uri) ?: "text/plain"
                val textContent = try {
                    String(bytes, Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
                val attachment = Attachment(
                    name = name,
                    mimeType = mime,
                    sizeBytes = bytes.size.toLong(),
                    uriString = uri.toString(),
                    extractedText = textContent,
                    lineCount = textContent?.lines()?.size,
                    isImage = false
                )
                _uiState.update { it.copy(attachments = it.attachments + attachment, isAttachmentMenuOpen = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load document: ${e.localizedMessage}") }
            }
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
        return name ?: uri.lastPathSegment
    }

    fun toggleReasoningEnabled() {
        _uiState.update { it.copy(isReasoningEnabled = !it.isReasoningEnabled) }
    }

    fun toggleModelPicker(isOpen: Boolean) {
        _uiState.update { it.copy(isModelPickerOpen = isOpen) }
    }

    fun selectProfile(profile: EndpointProfile) {
        viewModelScope.launch {
            conversationRepository.updateProfile(conversationId, profile.id)
            endpointRepository.setActiveProfileForCategory(profile.category, profile.id)
            _uiState.update {
                it.copy(
                    currentProfile = profile,
                    activeCategory = profile.category,
                    isModelPickerOpen = false
                )
            }
        }
    }

    fun switchCategory(category: String) {
        viewModelScope.launch {
            val activeId = endpointRepository.getActiveProfileForCategory(category)
            val profile = _uiState.value.allProfiles.firstOrNull { it.id == activeId }
                ?: _uiState.value.allProfiles.firstOrNull { it.category == category }

            if (profile != null) {
                selectProfile(profile)
            }
        }
    }

    fun updateTitle(newTitle: String) {
        viewModelScope.launch {
            conversationRepository.updateTitle(conversationId, newTitle)
            _uiState.update { state ->
                state.copy(conversation = state.conversation?.copy(title = newTitle))
            }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val prompt = state.inputText.trim()
        val currentAttachments = state.attachments
        if ((prompt.isEmpty() && currentAttachments.isEmpty()) || state.isGenerating) return

        val profile = state.currentProfile
        if (profile == null) {
            _uiState.update { it.copy(errorMessage = "No model endpoint configured. Please add or select a model first.") }
            return
        }

        val finalPrompt = if (prompt.isEmpty() && currentAttachments.isNotEmpty()) {
            "Please analyze the attached ${if (currentAttachments.size == 1) "file" else "files"}."
        } else {
            prompt
        }

        _uiState.update {
            it.copy(
                inputText = "",
                attachments = emptyList(),
                pasteNotice = null,
                isGenerating = true,
                isWaitingForFirstToken = true,
                isReasoningActive = false,
                streamingReasoningContent = "",
                streamingAnswerContent = "",
                streamingThoughtDurationSeconds = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            // Persist user message with attachments immediately to database
            conversationRepository.addMessage(
                conversationId = conversationId,
                role = "user",
                content = finalPrompt,
                attachments = currentAttachments
            )

            // Prepare context history: send ONLY the actual answer content, never reasoning
            val currentMsgs = conversationRepository.getMessagesList(conversationId)
            val unifiedMsgs = currentMsgs.map {
                UnifiedMessage(role = it.role, content = it.content, attachments = it.attachments)
            }

            val request = UnifiedChatRequest(
                messages = unifiedMsgs,
                model = profile.selectedModel,
                thinkingEnabled = state.isReasoningEnabled,
                thinkingBudget = profile.thinkingBudget ?: 2048,
                attachments = currentAttachments
            )

            val adapter = ModelAdapterFactory.create(profile)
            activeAdapter = adapter

            val assistantMsgId = UUID.randomUUID().toString()
            // Strictly separate buffers: never write reasoning and content into the same buffer
            val reasoningBuffer = StringBuilder()
            val contentBuffer = StringBuilder()
            var reasoningStartTime: Long? = null
            var thoughtDurationSeconds: Int? = null

            streamJob = launch {
                try {
                    adapter.streamMessage(request)
                        .catch { e ->
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    isWaitingForFirstToken = false,
                                    isReasoningActive = false,
                                    errorMessage = e.localizedMessage ?: "Stream connection error."
                                )
                            }
                        }
                        .collect { chunk ->
                            // 1. Check reasoning delta
                            val reasoningChunk = chunk.reasoningContent
                            if (_uiState.value.isReasoningEnabled && !reasoningChunk.isNullOrEmpty() && reasoningChunk != "null") {
                                if (reasoningStartTime == null) {
                                    reasoningStartTime = System.currentTimeMillis()
                                }
                                reasoningBuffer.append(reasoningChunk)
                                _uiState.update {
                                    it.copy(
                                        isWaitingForFirstToken = false,
                                        isReasoningActive = true,
                                        streamingReasoningContent = reasoningBuffer.toString()
                                    )
                                }
                            }

                            // 2. Check content delta (the actual answer)
                            val contentChunk = chunk.text
                            if (contentChunk.isNotEmpty() && contentChunk != "null") {
                                // If model was reasoning and now started emitting content, compute duration and transition
                                if (reasoningStartTime != null && thoughtDurationSeconds == null) {
                                    thoughtDurationSeconds = ((System.currentTimeMillis() - reasoningStartTime) / 1000).toInt().coerceAtLeast(1)
                                }
                                contentBuffer.append(contentChunk)
                                _uiState.update {
                                    it.copy(
                                        isWaitingForFirstToken = false,
                                        isReasoningActive = false,
                                        streamingAnswerContent = contentBuffer.toString(),
                                        streamingThoughtDurationSeconds = thoughtDurationSeconds
                                    )
                                }
                            }

                            // 3. Check finished state
                            if (chunk.isFinished) {
                                val completeAnswer = contentBuffer.toString()
                                val completeReasoning = reasoningBuffer.toString().takeIf { it.isNotEmpty() }

                                if (reasoningStartTime != null && thoughtDurationSeconds == null) {
                                    thoughtDurationSeconds = ((System.currentTimeMillis() - reasoningStartTime) / 1000).toInt().coerceAtLeast(1)
                                }

                                if (completeAnswer.isNotEmpty() || completeReasoning != null) {
                                    conversationRepository.saveOrUpdateMessage(
                                        Message(
                                            id = assistantMsgId,
                                            conversationId = conversationId,
                                            role = "assistant",
                                            content = completeAnswer,
                                            reasoningContent = completeReasoning,
                                            thoughtDurationSeconds = thoughtDurationSeconds
                                        )
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        isWaitingForFirstToken = false,
                                        isReasoningActive = false,
                                        streamingReasoningContent = "",
                                        streamingAnswerContent = "",
                                        streamingThoughtDurationSeconds = null
                                    )
                                }
                            }
                        }
                } catch (e: CancellationException) {
                    // Cancelled mid-stream by user: preserve partial answer & reasoning separately
                    val partialAnswer = contentBuffer.toString()
                    val partialReasoning = reasoningBuffer.toString().takeIf { it.isNotEmpty() }
                    if (partialAnswer.isNotEmpty() || partialReasoning != null) {
                        conversationRepository.saveOrUpdateMessage(
                            Message(
                                id = assistantMsgId,
                                conversationId = conversationId,
                                role = "assistant",
                                content = if (partialAnswer.isNotEmpty()) "$partialAnswer [Cancelled]" else "[Cancelled]",
                                reasoningContent = partialReasoning,
                                thoughtDurationSeconds = thoughtDurationSeconds
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            isWaitingForFirstToken = false,
                            isReasoningActive = false,
                            streamingReasoningContent = "",
                            streamingAnswerContent = "",
                            streamingThoughtDurationSeconds = null
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            isWaitingForFirstToken = false,
                            isReasoningActive = false,
                            errorMessage = e.localizedMessage ?: "Failed to generate response."
                        )
                    }
                } finally {
                    activeAdapter = null
                    streamJob = null
                }
            }
        }
    }

    fun cancelGeneration() {
        activeAdapter?.cancel()
        streamJob?.cancel()
        activeAdapter = null
        streamJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                isWaitingForFirstToken = false,
                isReasoningActive = false
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
