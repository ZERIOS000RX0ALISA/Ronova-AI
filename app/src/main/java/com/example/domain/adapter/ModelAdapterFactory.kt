package com.example.domain.adapter

import com.example.data.model.EndpointProfile
import com.example.data.model.ProviderType

object ModelAdapterFactory {
    fun create(profile: EndpointProfile): ModelAdapter {
        return when (profile.providerType.lowercase()) {
            ProviderType.GEMINI -> GeminiAdapter(profile)
            ProviderType.ANTHROPIC -> AnthropicAdapter(profile)
            ProviderType.OPENAI_COMPATIBLE -> OpenAICompatibleAdapter(profile)
            else -> OpenAICompatibleAdapter(profile)
        }
    }
}
