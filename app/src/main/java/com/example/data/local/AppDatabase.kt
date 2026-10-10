package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        EndpointProfileEntity::class,
        ActiveCategoryProfileEntity::class,
        WorkspaceEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AppSettingsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun endpointProfileDao(): EndpointProfileDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DEFAULT_WORKSPACE_ID = "default-workspace-001"

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningContent TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN thoughtDurationSeconds INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE endpoint_profiles ADD COLUMN thinkingBudget INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE conversations ADD COLUMN isThinkingEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT DEFAULT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (id INTEGER PRIMARY KEY NOT NULL, pasteToFileThreshold INTEGER NOT NULL, defaultThinkingEnabled INTEGER NOT NULL, defaultThinkingBudget INTEGER NOT NULL)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ronova_ai.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Initialize default workspace and default settings automatically
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.workspaceDao()?.insertWorkspace(
                                    WorkspaceEntity(
                                        id = DEFAULT_WORKSPACE_ID,
                                        name = "Default Workspace",
                                        defaultEndpointProfileId = null,
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                INSTANCE?.settingsDao()?.saveSettings(
                                    AppSettingsEntity(
                                        id = 1,
                                        pasteToFileThreshold = 15,
                                        defaultThinkingEnabled = true,
                                        defaultThinkingBudget = 2048
                                    )
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
