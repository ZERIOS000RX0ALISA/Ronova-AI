package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "endpoint_profiles")
data class EndpointProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val providerType: String,
    val baseUrl: String,
    val encryptedApiKey: String?,
    val selectedModel: String,
    val chatCapability: Boolean = true,
    val visionCapability: Boolean = false,
    val toolsCapability: Boolean = false,
    val streamingCapability: Boolean = true,
    val thinkingCapability: Boolean = false,
    val contextLength: Int? = null,
    val thinkingBudget: Int? = null,
    val createdAt: String
)

@Entity(
    tableName = "category_active_profiles",
    primaryKeys = ["category"]
)
data class ActiveCategoryProfileEntity(
    val category: String,
    val endpointProfileId: String
)

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultEndpointProfileId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["workspaceId"])]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val endpointProfileId: String,
    val isThinkingEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoningContent: String? = null,
    val thoughtDurationSeconds: Int? = null,
    val attachmentsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val pasteToFileThreshold: Int = 15,
    val defaultThinkingEnabled: Boolean = true,
    val defaultThinkingBudget: Int = 2048
)
