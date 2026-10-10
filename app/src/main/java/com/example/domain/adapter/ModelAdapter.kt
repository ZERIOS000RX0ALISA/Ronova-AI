package com.example.domain.adapter

import com.example.data.model.ConnectionStatus
import com.example.data.model.EndpointProfile
import com.example.data.model.ModelInfo
import com.example.data.model.StreamChunk
import com.example.data.model.UnifiedChatRequest
import com.example.data.model.UnifiedChatResponse
import kotlinx.coroutines.flow.Flow

/**
 * Unified provider adapter interface.
 * All providers (OpenAI-compatible in Stage 1, Gemini/Anthropic in later stages)
 * adhere strictly to this contract. UI and business logic interact exclusively
 * through this interface.
 */
interface ModelAdapter {
    val id: String
    val endpointProfile: EndpointProfile

    suspend fun listModels(): Result<List<ModelInfo>>
    suspend fun testConnection(): Result<ConnectionStatus>

    suspend fun sendMessage(request: UnifiedChatRequest): Result<UnifiedChatResponse>
    fun streamMessage(request: UnifiedChatRequest): Flow<StreamChunk>

    fun supportsVision(): Boolean
    fun supportsTools(): Boolean
    fun supportsThinking(): Boolean
    fun supportsStreaming(): Boolean

    fun cancel()
}
