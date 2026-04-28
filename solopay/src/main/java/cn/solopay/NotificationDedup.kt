package cn.solopay

import java.util.concurrent.ConcurrentHashMap

/**
 * 短时间窗口去重（配合服务端 external_event_id 幂等）。
 */
object NotificationDedup {

    private const val WINDOW_MS = 5000L
    private val recent = ConcurrentHashMap<String, Long>()

    /**
     * @return true 应跳过（重复）
     */
    fun shouldSkip(dedupKey: String): Boolean {
        val now = System.currentTimeMillis()
        for (k in recent.keys.toList()) {
            val t = recent[k] ?: continue
            if (now - t > WINDOW_MS) recent.remove(k)
        }
        val prev = recent.putIfAbsent(dedupKey, now)
        return prev != null
    }
}
