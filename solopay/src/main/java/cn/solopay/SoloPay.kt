package cn.solopay

import android.content.Context
import androidx.room.Room
import cn.solopay.db.SoloPayDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicBoolean

object SoloPay {

    private val initialized = AtomicBoolean(false)

    @Volatile
    lateinit var appContext: Context
        private set

    lateinit var db: SoloPayDatabase
        private set

    val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureInit(context: Context) {
        if (initialized.get()) return
        synchronized(this) {
            if (initialized.get()) return
            appContext = context.applicationContext
            db = Room.databaseBuilder(appContext, SoloPayDatabase::class.java, "solopay.db")
                .fallbackToDestructiveMigration()
                .build()
            initialized.set(true)
        }
    }
}
