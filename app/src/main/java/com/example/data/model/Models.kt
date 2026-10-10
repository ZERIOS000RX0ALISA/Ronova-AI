package com.example.data.model

import java.util.UUID

/**
 * Category tag for Endpoint Profiles:
 * Flash (⚡ fast/cheap), System (🧠 heavy/capable), Astra (☄️ local/self-hosted/custom)
 */
object ProfileCategory {
    const val FLASH = "flash"
    const val SYSTEM = "system"
    const val ASTRA = "astra"

    val ALL = listOf(FLASH, SYSTEM, ASTRA)

    fun getDisplayName(category: String): String = when (category.lowercase()) {
        FLASH -> "Flash"
        SYSTEM -> "System"
        ASTRA -> "Astra"
        else -> category.replaceFirstChar { it.uppercase() }
    }

    fun getIconEmoji(category: String): String = when (category.lowercase()) {
        FLASH -> "⚡"
        SYSTEM -> "🧠"
        ASTRA -> "☄️"
        else -> "✨"
    }

    fun getDescription(category: String): String = when (category.lowercase()) {
        FLASH -> "Fast & lightweight models"
        SYSTEM -> "Heavy & high-capability models"
        ASTRA -> "Local & self-hosted endpoints"
        else -> "Custom model endpoint"
    }
}

/**
 * Supported provider types
 */
object ProviderType {
    const val OPENAI_COMPATIBLE = "openai-compatible"
    const val GEMINI = "gemini"
    const val ANTHROPIC = "anthropic"

    val ALL = listOf(OPENAI_COMPATIBLE, GEMINI, ANTHROPIC)

    fun getDisplayName(type: String): String = when (type.lowercase()) {
        OPENAI_COMPATIBLE -> "OpenAI / Compatible"
        GEMINI -> "Google Gemini"
        ANTHROPIC -> "Anthropic Claude"
        else -> type
    }

    fun getDefaultBaseUrl(type: String): String = when (type.lowercase()) {
        GEMINI -> "https://generativelanguage.googleapis.com"
        ANTHROPIC -> "https://api.anthropic.com/v1"
        else -> "https://api.openai.com/v1"
    }
}

/**
 * Declared capabilities of the endpoint profile
 */
data class ProfileCapabilities(
    val chat: Boolean = true,
    val vision: Boolean = false,
    val tools: Boolean = false,
    val streaming: Boolean = true,
    val thinking: Boolean = false,
)

/**
 * Core Endpoint Profile representation across the application
 */
data class EndpointProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = ProfileCategory.ASTRA,
    val providerType: String = ProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String,
    val apiKey: String? = null, // In-memory decrypted or null. Masked in logs.
    val selectedModel: String,
    val capabilities: ProfileCapabilities = ProfileCapabilities(),
    val contextLength: Int? = null,
    val thinkingBudget: Int? = null,
    val createdAt: String = java.text.SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        java.util.Locale.US
    ).format(java.util.Date())
) {
    override fun toString(): String {
        val maskedKey = if (apiKey.isNullOrEmpty()) "none" else "present (masked)"
        return "EndpointProfile(id=$id, name='$name', category=$category, providerType=$providerType, baseUrl='$baseUrl', model='$selectedModel', apiKey=$maskedKey)"
    }
}

/**
 * Model info discovered from GET /models or provider catalog
 */
data class ModelInfo(
    val id: String,
    val name: String = id,
    val contextLength: Int? = null,
    val description: String? = null,
    val ownedBy: String? = null,
    val supportsVision: Boolean = false,
    val supportsThinking: Boolean = false,
    val supportsTools: Boolean = false
)

/**
 * Result of connection test
 */
data class ConnectionStatus(
    val isSuccessful: Boolean,
    val latencyMs: Long,
    val message: String,
    val modelCount: Int = 0,
    val models: List<ModelInfo> = emptyList()
)

/**
 * File or image attachment for multimodal interaction
 */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val uriString: String? = null,
    val base64Data: String? = null,
    val extractedText: String? = null,
    val isImage: Boolean = mimeType.startsWith("image/"),
    val lineCount: Int? = null,
    val isEncoding: Boolean = false
)

/**
 * Unified Chat Message
 */
data class UnifiedMessage(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val attachments: List<Attachment> = emptyList()
)

/**
 * Unified Chat Request
 */
data class UnifiedChatRequest(
    val messages: List<UnifiedMessage>,
    val model: String,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val thinkingEnabled: Boolean = false,
    val thinkingBudget: Int? = null,
    val attachments: List<Attachment> = emptyList()
)

/**
 * Non-streamed response
 */
data class UnifiedChatResponse(
    val id: String,
    val text: String,
    val reasoningContent: String? = null,
    val finishReason: String? = null
)

/**
 * Streaming token chunk
 */
data class StreamChunk(
    val text: String = "",
    val reasoningContent: String? = null,
    val isFinished: Boolean = false,
    val finishReason: String? = null,
    val rawData: String? = null
) {
    val contentDelta: String? get() = text.takeIf { it.isNotEmpty() }
    val reasoningDelta: String? get() = reasoningContent
    val isFinal: Boolean get() = isFinished
}

/**
 * Workspace domain model
 */
data class Workspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val defaultEndpointProfileId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Conversation domain model
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val workspaceId: String,
    val title: String,
    val endpointProfileId: String,
    val isThinkingEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Message domain model
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String, // "user" or "assistant"
    val content: String, // the actual answer, never includes reasoning
    val reasoningContent: String? = null, // null when absent, NOT the string "null"
    val thoughtDurationSeconds: Int? = null,
    val isReasoningInProgress: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Message content parsed block hierarchy
 */
sealed class MessageBlock {
    data class Text(val content: String) : MessageBlock()
    data class Markdown(val content: String) : MessageBlock()
    data class Code(val language: String?, val content: String) : MessageBlock()
    data class LatexBlock(val content: String, val displayMode: Boolean) : MessageBlock()
    data class Table(val columns: List<String>, val rows: List<List<String>>) : MessageBlock()
    data class Chart(val chartType: String, val x: List<String>, val series: List<ChartSeries>) : MessageBlock()
    data class ImageBlock(val url: String, val caption: String? = null) : MessageBlock()
    data class ImageGallery(val items: List<ImageItem>) : MessageBlock()
}

data class ChartSeries(
    val name: String,
    val values: List<Float>
)

data class ImageItem(
    val url: String,
    val caption: String? = null
)

/**
 * App Settings
 */
data class AppSettings(
    val pasteToFileThreshold: Int = 15,
    val defaultThinkingEnabled: Boolean = true,
    val defaultThinkingBudget: Int = 2048
)
