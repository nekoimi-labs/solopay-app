package cn.ppps.forwarder.utils

import android.content.Context
import android.os.Build
import java.io.File

/** Preserve data when switching the application's default storage to unlocked-user storage. */
object BusinessStorageMigration {
    fun migrate(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val marker = context.getSharedPreferences("business_storage_migration", Context.MODE_PRIVATE)
        if (marker.getBoolean("complete", false)) return
        val old = context.createDeviceProtectedStorageContext()
        old.databaseList().forEach { name ->
            if (!context.getDatabasePath(name).exists()) {
                check(context.moveDatabaseFrom(old, name)) { "Cannot migrate database: $name" }
            }
        }
        File(old.dataDir, "shared_prefs").listFiles()?.filter { it.extension == "xml" }?.forEach { source ->
            // SmsForwarder explicitly keeps these settings in device storage for direct boot.
            if (source.nameWithoutExtension != context.packageName &&
                !File(context.dataDir, "shared_prefs/${source.name}").exists()
            ) {
                check(context.moveSharedPreferencesFrom(old, source.nameWithoutExtension)) {
                    "Cannot migrate preferences: ${source.name}"
                }
            }
        }
        if (old.filesDir.exists()) {
            old.filesDir.walkTopDown().forEach { source ->
                val target = File(context.filesDir, source.relativeTo(old.filesDir).path)
                if (source.isDirectory) target.mkdirs() else if (!target.exists()) source.copyTo(target)
            }
        }
        marker.edit().putBoolean("complete", true).apply()
    }
}
