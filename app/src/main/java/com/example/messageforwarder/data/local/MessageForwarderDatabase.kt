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
 * High-level delivery states shown in the log UI.
 */
enum class DeliveryStatus {
    RECEIVED,
    SENDING,
    DELIVERED,
    FAILED,
}

@Entity(
    tableName = "pending_forwards",
    indices = [Index(value = ["messageFingerprint"], unique = true)],
)
data class PendingForwardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Fingerprint deduplicates multipart or repeated broadcasts for the same SMS.
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
 * Queue table for messages that still need an HTTP delivery attempt.
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
 * Immutable-ish audit trail shown in the logs screen and dashboard summaries.
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
                // A schema reset is acceptable for this internal v1 tool while the model is still moving.
                .fallbackToDestructiveMigration()
                .build()
    }
}
