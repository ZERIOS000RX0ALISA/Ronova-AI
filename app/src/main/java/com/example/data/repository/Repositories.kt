package com.example.data.repository

import com.example.data.local.ActiveCategoryProfileEntity
import com.example.data.local.AppDatabase
import com.example.data.local.AppSettingsEntity
import com.example.data.local.ConversationDao
import com.example.data.local.ConversationEntity
import com.example.data.local.EndpointProfileDao
import com.example.data.local.EndpointProfileEntity
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.local.SettingsDao
import com.example.data.local.WorkspaceDao
import com.example.data.local.WorkspaceEntity
import com.example.data.model.AppSettings
import com.example.data.model.Attachment
import com.example.data.model.Conversation
import com.example.data.model.EndpointProfile
import com.example.data.model.Message
import com.example.data.model.ProfileCapabilities
import com.example.data.model.Workspace
import com.example.data.security.KeystoreSecurityHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class EndpointRepository(
    private val endpointProfileDao: EndpointProfileDao
) {
    val allProfiles: Flow<List<EndpointProfile>> = endpointProfileDao.getAllProfiles().map { entities ->
        entities.map { it.toDomain() }
    }

    val activeCategoryProfiles: Flow<Map<String, String>> = endpointProfileDao.getAllActiveCategoryProfiles().map { list ->
        list.associate { it.category to it.endpointProfileId }
    }

    suspend fun getProfile(id: String): EndpointProfile? {
        return endpointProfileDao.getProfileById(id)?.toDomain()
    }

    suspend fun saveProfile(profile: EndpointProfile) {
        val encryptedKey = KeystoreSecurityHelper.encrypt(profile.apiKey)
        val entity = EndpointProfileEntity(
            id = profile.id,
            name = profile.name,
            category = profile.category,
            providerType = profile.providerType,
            baseUrl = profile.baseUrl,
            encryptedApiKey = encryptedKey,
            selectedModel = profile.selectedModel,
            chatCapability = profile.capabilities.chat,
            visionCapability = profile.capabilities.vision,
            toolsCapability = profile.capabilities.tools,
            streamingCapability = profile.capabilities.streaming,
            thinkingCapability = profile.capabilities.thinking,
            contextLength = profile.contextLength,
            thinkingBudget = profile.thinkingBudget,
            createdAt = profile.createdAt
        )
        endpointProfileDao.insertProfile(entity)
        endpointProfileDao.setActiveProfileForCategory(
            ActiveCategoryProfileEntity(
                category = profile.category,
                endpointProfileId = profile.id
            )
        )
    }

    suspend fun deleteProfile(id: String) {
        endpointProfileDao.deleteProfile(id)
    }

    suspend fun getActiveProfileForCategory(category: String): String? {
        return endpointProfileDao.getActiveProfileForCategory(category)?.endpointProfileId
    }

    suspend fun setActiveProfileForCategory(category: String, profileId: String) {
        endpointProfileDao.setActiveProfileForCategory(
            ActiveCategoryProfileEntity(
                category = category,
                endpointProfileId = profileId
            )
        )
    }

    private fun EndpointProfileEntity.toDomain(): EndpointProfile {
        val decryptedKey = KeystoreSecurityHelper.decrypt(encryptedApiKey)
        return EndpointProfile(
            id = id,
            name = name,
            category = category,
            providerType = providerType,
            baseUrl = baseUrl,
            apiKey = decryptedKey,
            selectedModel = selectedModel,
            capabilities = ProfileCapabilities(
                chat = chatCapability,
                vision = visionCapability,
                tools = toolsCapability,
                streaming = streamingCapability,
                thinking = thinkingCapability
            ),
            contextLength = contextLength,
            thinkingBudget = thinkingBudget,
            createdAt = createdAt
        )
    }
}

class WorkspaceRepository(
    private val workspaceDao: WorkspaceDao
) {
    val allWorkspaces: Flow<List<Workspace>> = workspaceDao.getAllWorkspaces().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getWorkspace(id: String): Workspace? {
        return workspaceDao.getWorkspaceById(id)?.toDomain()
    }

    suspend fun createWorkspace(name: String): Workspace {
        val entity = WorkspaceEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "New Workspace" },
            defaultEndpointProfileId = null,
            createdAt = System.currentTimeMillis()
        )
        workspaceDao.insertWorkspace(entity)
        return entity.toDomain()
    }

    suspend fun deleteWorkspace(id: String) {
        workspaceDao.deleteWorkspace(id)
    }

    suspend fun ensureDefaultWorkspace(): Workspace {
        val existing = workspaceDao.getWorkspaceById(AppDatabase.DEFAULT_WORKSPACE_ID)
        if (existing != null) return existing.toDomain()
        val defaultWs = WorkspaceEntity(
            id = AppDatabase.DEFAULT_WORKSPACE_ID,
            name = "Default Workspace",
            defaultEndpointProfileId = null,
            createdAt = System.currentTimeMillis()
        )
        workspaceDao.insertWorkspace(defaultWs)
        return defaultWs.toDomain()
    }

    private fun WorkspaceEntity.toDomain() = Workspace(
        id = id,
        name = name,
        defaultEndpointProfileId = defaultEndpointProfileId,
        createdAt = createdAt
    )
}

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun getConversations(workspaceId: String): Flow<List<Conversation>> {
        return conversationDao.getConversationsForWorkspace(workspaceId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getConversation(id: String): Conversation? {
        return conversationDao.getConversationById(id)?.toDomain()
    }

    suspend fun getLatestConversation(): Conversation? {
        return conversationDao.getLatestConversation()?.toDomain()
    }

    suspend fun createConversation(
        workspaceId: String,
        title: String,
        profileId: String,
        isThinkingEnabled: Boolean = true
    ): Conversation {
        val entity = ConversationEntity(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            title = title.trim().ifEmpty { "New Conversation" },
            endpointProfileId = profileId,
            isThinkingEnabled = isThinkingEnabled,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        conversationDao.insertConversation(entity)
        return entity.toDomain()
    }

    suspend fun updateTitle(id: String, newTitle: String) {
        conversationDao.updateTitle(id, newTitle)
    }

    suspend fun updateProfile(id: String, profileId: String) {
        conversationDao.updateProfileId(id, profileId)
    }

    suspend fun updateThinkingEnabled(id: String, enabled: Boolean) {
        conversationDao.updateThinkingEnabled(id, enabled)
    }

    suspend fun deleteConversation(id: String) {
        conversationDao.deleteConversation(id)
        messageDao.deleteMessagesForConversation(id)
    }

    fun getMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(conversationId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getMessagesList(conversationId: String): List<Message> {
        return messageDao.getMessagesList(conversationId).map { it.toDomain() }
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        reasoningContent: String? = null,
        thoughtDurationSeconds: Int? = null,
        attachments: List<Attachment> = emptyList()
    ): Message {
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            reasoningContent = reasoningContent,
            thoughtDurationSeconds = thoughtDurationSeconds,
            attachmentsJson = serializeAttachments(attachments),
            createdAt = System.currentTimeMillis()
        )
        messageDao.insertMessage(entity)
        conversationDao.updateTitle(conversationId, entity.content.take(30).trim().ifEmpty { "Chat" })
        return entity.toDomain()
    }

    suspend fun saveOrUpdateMessage(message: Message) {
        val entity = MessageEntity(
            id = message.id,
            conversationId = message.conversationId,
            role = message.role,
            content = message.content,
            reasoningContent = message.reasoningContent,
            thoughtDurationSeconds = message.thoughtDurationSeconds,
            attachmentsJson = serializeAttachments(message.attachments),
            createdAt = message.createdAt
        )
        messageDao.insertMessage(entity)
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        workspaceId = workspaceId,
        title = title,
        endpointProfileId = endpointProfileId,
        isThinkingEnabled = isThinkingEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        reasoningContent = reasoningContent,
        thoughtDurationSeconds = thoughtDurationSeconds,
        attachments = deserializeAttachments(attachmentsJson),
        createdAt = createdAt
    )

    companion object {
        fun serializeAttachments(list: List<Attachment>): String? {
            if (list.isEmpty()) return null
            val arr = JSONArray()
            for (att in list) {
                val obj = JSONObject()
                obj.put("id", att.id)
                obj.put("name", att.name)
                obj.put("mimeType", att.mimeType)
                obj.put("sizeBytes", att.sizeBytes)
                att.uriString?.let { obj.put("uriString", it) }
                att.base64Data?.let { obj.put("base64Data", it) }
                att.extractedText?.let { obj.put("extractedText", it) }
                att.lineCount?.let { obj.put("lineCount", it) }
                arr.put(obj)
            }
            return arr.toString()
        }

        fun deserializeAttachments(json: String?): List<Attachment> {
            if (json.isNullOrBlank()) return emptyList()
            val list = mutableListOf<Attachment>()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Attachment(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "attachment"),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            uriString = obj.optString("uriString", null),
                            base64Data = obj.optString("base64Data", null),
                            extractedText = obj.optString("extractedText", null),
                            lineCount = if (obj.has("lineCount")) obj.optInt("lineCount") else null
                        )
                    )
                }
            } catch (_: Exception) {}
            return list
        }
    }
}

class SettingsRepository(
    private val settingsDao: SettingsDao
) {
    val settings: Flow<AppSettings> = settingsDao.getSettings().map { entity ->
        if (entity != null) {
            AppSettings(
                pasteToFileThreshold = entity.pasteToFileThreshold,
                defaultThinkingEnabled = entity.defaultThinkingEnabled,
                defaultThinkingBudget = entity.defaultThinkingBudget
            )
        } else {
            AppSettings()
        }
    }

    suspend fun getSettings(): AppSettings {
        val entity = settingsDao.getSettingsDirect()
        return if (entity != null) {
            AppSettings(
                pasteToFileThreshold = entity.pasteToFileThreshold,
                defaultThinkingEnabled = entity.defaultThinkingEnabled,
                defaultThinkingBudget = entity.defaultThinkingBudget
            )
        } else {
            AppSettings()
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        settingsDao.saveSettings(
            AppSettingsEntity(
                id = 1,
                pasteToFileThreshold = settings.pasteToFileThreshold,
                defaultThinkingEnabled = settings.defaultThinkingEnabled,
                defaultThinkingBudget = settings.defaultThinkingBudget
            )
        )
    }
}
