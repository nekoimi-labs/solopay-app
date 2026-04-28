package cn.solopay.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "signal_outbox")
data class SignalOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "external_event_id") val externalEventId: String,
    @ColumnInfo(name = "body_json") val bodyJson: String,
    @ColumnInfo(name = "channel_type") val channelType: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_FAILED = "failed"
    }
}

@Dao
interface SignalOutboxDao {

    @Insert
    suspend fun insert(row: SignalOutboxEntity): Long

    @Query(
        "SELECT * FROM signal_outbox WHERE status = :st ORDER BY id ASC LIMIT :limit",
    )
    suspend fun listByStatus(st: String, limit: Int): List<SignalOutboxEntity>

    @Query("SELECT * FROM signal_outbox ORDER BY id DESC LIMIT :limit")
    fun recentFlow(limit: Int): Flow<List<SignalOutboxEntity>>

    @Query(
        """
        SELECT IFNULL(SUM(amount_cents), 0) FROM signal_outbox
        WHERE status = 'sent' AND created_at >= :startOfDay
        """,
    )
    fun todaySentAmountCentsFlow(startOfDay: Long): Flow<Long?>

    @Query("UPDATE signal_outbox SET status = :st, updated_at = :t, last_error = :err WHERE id = :id")
    suspend fun updateStatus(id: Long, st: String, t: Long, err: String?)

    @Query("SELECT * FROM signal_outbox WHERE external_event_id = :eid LIMIT 1")
    suspend fun byExternalId(eid: String): SignalOutboxEntity?
}

@Database(entities = [SignalOutboxEntity::class], version = 1, exportSchema = false)
abstract class SoloPayDatabase : RoomDatabase() {
    abstract fun outboxDao(): SignalOutboxDao
}
