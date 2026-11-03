package cn.solopay.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "signal_outbox",
    indices = [
        Index(value = ["external_event_id"], unique = true),
    ],
)
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
    @ColumnInfo(name = "binding_id", defaultValue = "''") val bindingId: String = "",
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_FAILED = "failed"
    }
}

@Dao
interface SignalOutboxDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
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
        WHERE status = :sent AND created_at >= :startOfDay
        """,
    )
    fun todaySentAmountCentsFlow(startOfDay: Long, sent: String): Flow<Long?>

    @Query("UPDATE signal_outbox SET status = :st, updated_at = :t, last_error = :err WHERE id = :id")
    suspend fun updateStatus(id: Long, st: String, t: Long, err: String?)

    @Query(
        """
        UPDATE signal_outbox SET status = :failed, updated_at = :t, last_error = :err
        WHERE status = :pending
        """,
    )
    suspend fun markAllPendingAsFailed(pending: String, failed: String, t: Long, err: String)

    @Query("UPDATE signal_outbox SET status = 'failed', updated_at = :t, last_error = 'binding_changed' WHERE status = 'pending' AND binding_id != :bindingId")
    suspend fun retireOtherBindings(bindingId: String, t: Long)

    @Query("SELECT * FROM signal_outbox WHERE external_event_id = :eid LIMIT 1")
    suspend fun byExternalId(eid: String): SignalOutboxEntity?
}

@Database(entities = [SignalOutboxEntity::class], version = 3, exportSchema = false)
abstract class SoloPayDatabase : RoomDatabase() {
    abstract fun outboxDao(): SignalOutboxDao

    companion object {
        /** Queue rows from before binding isolation have no provable owner; retain but never upload them. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE signal_outbox ADD COLUMN binding_id TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE signal_outbox SET status = 'failed', last_error = 'binding_unknown' WHERE status = 'pending'")
            }
        }
    }
}
