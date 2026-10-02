package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class ComponentWidgetType(val displayName: String) {
    BUTTON("Button"),
    TOGGLE("Toggle Switch"),
    SLIDER("Slider"),
    TEXT("Text Label"),
    INPUT("Text Input"),
    IMAGE("Image Box"),
    LINK("Link Opener"),
    S1_START("Start"),
    S1_STOP("Stop"),
    S1_TEXT("TextView"),
    S1_LINK("Link Open"),
    S1_IMAGE("ImageView");

    val isScreen1Widget: Boolean
        get() = name.startsWith("S1_")
}

fun isScreen1WidgetType(type: String?): Boolean {
    return type != null && type.trim().uppercase().startsWith("S1_")
}

fun StudioProjectEntity.isLuaScriptProject(): Boolean {
    return defaultTargetFilePath.trim().endsWith(".lua", ignoreCase = true) ||
        packageName.trim().startsWith("lua.script", ignoreCase = true)
}

@Entity(tableName = "studio_projects")
data class StudioProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String = "",
    val projectName: String = name,
    val overlayTitle: String = "$name Floating Panel",
    val appLogoPath: String = "",
    val floatingLogoPath: String = "",
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val minSdk: Int = 24,
    val targetSdk: Int = 35,
    val canvasWidthDp: Int = 270,
    val canvasHeightDp: Int = 340,
    val canvasBgColorHex: String = "#FFFFFF",
    val defaultTargetFilePath: String = "/storage/emulated/0/StudioTarget/live_state.bin",
    val autoFixSize: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val canvasBgImagePath: String = ""
)

@Entity(tableName = "canvas_components")
data class CanvasComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val type: String,
    val label: String,
    val posXDp: Int = 10,
    val posYDp: Int = 10,
    val widthDp: Int = 165,
    val heightDp: Int = 36,
    val bgColorHex: String = "#FFFFFF",
    val textColorHex: String = "#0F172A",
    val bgImagePath: String = "",
    val customImagePath: String = "",
    val soundTrigger: String = "CLICK",
    val customSoundPath: String = "",
    val offSoundTrigger: String = "LOCK",
    val offCustomSoundPath: String = "",
    val targetFilePath: String = "/storage/emulated/0/StudioTarget/live_state.bin",
    val byteOffsetHex: String = "0x04",
    val onPayloadHex: String = "On",
    val offPayloadHex: String = "Off",
    val sliderMax: Int = 100,
    val currentValue: String = "false",
    val linkUrl: String = "",
    val borderColorHex: String = "#38BDF8",
    val borderStrokePercent: Int = 25,
    val borderAnimation: String = "NONE"
)

@Entity(tableName = "config_write_audit")
data class ConfigWriteAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parameterKey: String,
    val byteOffsetHex: String,
    val previousValue: String,
    val newValue: String,
    val durationMicros: Long,
    val crc32Hex: String,
    val targetFilePath: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM studio_projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<StudioProjectEntity>>

    @Query("SELECT * FROM studio_projects ORDER BY updatedAt DESC")
    suspend fun getAllProjectsSync(): List<StudioProjectEntity>

    @Query("SELECT * FROM studio_projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: Long): StudioProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: StudioProjectEntity): Long

    @Update
    suspend fun updateProject(project: StudioProjectEntity)

    @Query("DELETE FROM studio_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    @Query("SELECT * FROM canvas_components WHERE projectId = :projectId ORDER BY id ASC")
    fun observeComponentsForProject(projectId: Long): Flow<List<CanvasComponentEntity>>

    @Query("SELECT * FROM canvas_components WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun getComponentsForProjectSync(projectId: Long): List<CanvasComponentEntity>

    @Query("SELECT COUNT(*) FROM canvas_components WHERE projectId = :projectId")
    fun observeComponentCount(projectId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponent(component: CanvasComponentEntity): Long

    @Update
    suspend fun updateComponent(component: CanvasComponentEntity)

    @Query("DELETE FROM canvas_components WHERE id = :componentId")
    suspend fun deleteComponentById(componentId: Long)

    @Query("DELETE FROM canvas_components WHERE projectId = :projectId")
    suspend fun deleteAllComponentsForProject(projectId: Long)
}

@Dao
interface ConfigAuditDao {
    @Query("SELECT * FROM config_write_audit ORDER BY timestampMillis DESC LIMIT 50")
    fun observeRecentAudits(): Flow<List<ConfigWriteAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(entry: ConfigWriteAuditEntity)

    @Query("DELETE FROM config_write_audit")
    suspend fun clearAllAudits()
}

@Database(
    entities = [
        StudioProjectEntity::class,
        CanvasComponentEntity::class,
        ConfigWriteAuditEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao
    abstract fun configAuditDao(): ConfigAuditDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE canvas_components ADD COLUMN bgImagePath TEXT NOT NULL DEFAULT ''")
                } catch (_: Throwable) {
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE canvas_components ADD COLUMN borderColorHex TEXT NOT NULL DEFAULT '#38BDF8'")
                } catch (_: Throwable) {
                }
                try {
                    db.execSQL("ALTER TABLE canvas_components ADD COLUMN borderStrokePercent INTEGER NOT NULL DEFAULT 25")
                } catch (_: Throwable) {
                }
                try {
                    db.execSQL("ALTER TABLE canvas_components ADD COLUMN borderAnimation TEXT NOT NULL DEFAULT 'NONE'")
                } catch (_: Throwable) {
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "studio_error_workspace.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ConfigAuditRepository(private val dao: ConfigAuditDao) {
    val recentAudits: Flow<List<ConfigWriteAuditEntity>> = dao.observeRecentAudits()

    suspend fun recordWrite(entry: ConfigWriteAuditEntity) {
        dao.insertAudit(entry)
    }

    suspend fun clearHistory() {
        dao.clearAllAudits()
    }
}
