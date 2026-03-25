package com.example.messageforwarder.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 紀錄頁與狀態頁會顯示的高階轉送狀態。
 */
enum class DeliveryStatus {
    RECEIVED,
    SENDING,
    DELIVERED,
    FAILED,
    FILTERED,
}

@Entity(
    tableName = "pending_forwards",
    indices = [Index(value = ["messageFingerprint"], unique = true)],
)
/**
 * 尚未成功送達 API 的待轉送佇列資料。
 */
data class PendingForwardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Fingerprint 用來避免同一封多段簡訊或重複廣播被重複入列。
    val messageFingerprint: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val subscriptionId: Int?,
    val simSlot: Int?,
    val deviceId: String,
    val appVersion: String,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
)

@Entity(
    tableName = "delivery_logs",
    indices = [Index(value = ["messageFingerprint"], unique = true)],
)
/**
 * 完整保留每封簡訊處理結果的歷程紀錄，用於畫面查詢與人工補送。
 */
data class DeliveryLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageFingerprint: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val subscriptionId: Int?,
    val simSlot: Int?,
    val deviceId: String,
    val appVersion: String,
    val status: DeliveryStatus,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    val deliveredAt: Long? = null,
    val httpStatusCode: Int? = null,
)

class DeliveryStatusConverters {
    @TypeConverter
    fun fromStatus(status: DeliveryStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)
}

/**
 * 管理待轉送佇列表的 DAO。
 */
@Dao
interface PendingForwardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingForwardEntity): Long

    @Query("SELECT * FROM pending_forwards ORDER BY receivedAt ASC LIMIT 1")
    suspend fun getNext(): PendingForwardEntity?

    @Query("SELECT * FROM pending_forwards WHERE messageFingerprint = :fingerprint LIMIT 1")
    suspend fun getByFingerprint(fingerprint: String): PendingForwardEntity?

    @Query("DELETE FROM pending_forwards WHERE messageFingerprint = :fingerprint")
    suspend fun deleteByFingerprint(fingerprint: String)

    @Query("SELECT COUNT(*) FROM pending_forwards")
    fun observePendingCount(): Flow<Int>

    @Update
    suspend fun update(entity: PendingForwardEntity)
}

/**
 * 管理送達紀錄表的 DAO，提供畫面查詢與狀態更新。
 */
@Dao
interface DeliveryLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DeliveryLogEntity): Long

    @Update
    suspend fun update(entity: DeliveryLogEntity)

    @Query("SELECT * FROM delivery_logs WHERE messageFingerprint = :fingerprint LIMIT 1")
    suspend fun getByFingerprint(fingerprint: String): DeliveryLogEntity?

    @Query("SELECT * FROM delivery_logs ORDER BY receivedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DeliveryLogEntity>>

    @Query("SELECT * FROM delivery_logs ORDER BY receivedAt DESC LIMIT 1")
    fun observeLatest(): Flow<DeliveryLogEntity?>

    @Query("SELECT MAX(deliveredAt) FROM delivery_logs WHERE deliveredAt IS NOT NULL")
    fun observeLastDeliveredAt(): Flow<Long?>
}

@Database(
    entities = [PendingForwardEntity::class, DeliveryLogEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(DeliveryStatusConverters::class)
/**
 * App 使用的 Room 資料庫，包含待送佇列與歷程紀錄兩張表。
 */
abstract class MessageForwarderDatabase : RoomDatabase() {
    abstract fun pendingForwardDao(): PendingForwardDao
    abstract fun deliveryLogDao(): DeliveryLogDao

    companion object {
        fun create(context: Context): MessageForwarderDatabase =
            Room.databaseBuilder(
                context,
                MessageForwarderDatabase::class.java,
                "message-forwarder.db",
            )
                // 這是內部使用的 v1 工具，模型仍在變動，允許以重建資料庫換取快速迭代。
                .fallbackToDestructiveMigration()
                .build()
    }
}
