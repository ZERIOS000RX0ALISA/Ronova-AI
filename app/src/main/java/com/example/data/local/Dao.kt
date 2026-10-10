package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EndpointProfileDao {
    @Query("SELECT * FROM endpoint_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<EndpointProfileEntity>>

    @Query("SELECT * FROM endpoint_profiles WHERE category = :category ORDER BY createdAt DESC")
    fun getProfilesByCategory(category: String): Flow<List<EndpointProfileEntity>>

    @Query("SELECT * FROM endpoint_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): EndpointProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: EndpointProfileEntity)

    @Query("DELETE FROM endpoint_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("SELECT * FROM category_active_profiles")
    fun getAllActiveCategoryProfiles(): Flow<List<ActiveCategoryProfileEntity>>

    @Query("SELECT * FROM category_active_profiles WHERE category = :category LIMIT 1")
    suspend fun getActiveProfileForCategory(category: String): ActiveCategoryProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setActiveProfileForCategory(entity: ActiveCategoryProfileEntity)
}

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY createdAt ASC")
    fun getAllWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceById(id: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: WorkspaceEntity)

    @Update
    suspend fun updateWorkspace(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteWorkspace(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC")
    fun getConversationsForWorkspace(workspaceId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestConversation(): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET endpointProfileId = :profileId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProfileId(id: String, profileId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET isThinkingEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateThinkingEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations WHERE workspaceId = :workspaceId")
    suspend fun deleteConversationsForWorkspace(workspaceId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesList(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)
}
