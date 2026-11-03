package cn.solopay

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.room.Room
import cn.solopay.db.SoloPayDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import java.util.concurrent.atomic.AtomicBoolean

object SoloPay {

    private val initialized = AtomicBoolean(false)

    @Volatile
    lateinit var appContext: Context
        private set

    lateinit var db: SoloPayDatabase
        private set

    val ioScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e("SoloPay", "Background operation failed", error)
        },
    )

    fun ensureInit(context: Context) {
        if (initialized.get()) return
        synchronized(this) {
            if (initialized.get()) return
            appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !appContext.getDatabasePath("solopay.db").exists()) {
                appContext.moveDatabaseFrom(appContext.createDeviceProtectedStorageContext(), "solopay.db")
            }
            db = Room.databaseBuilder(appContext, SoloPayDatabase::class.java, "solopay.db")
                .addMigrations(SoloPayDatabase.MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
            initialized.set(true)
        }
    }
}
