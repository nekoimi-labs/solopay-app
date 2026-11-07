package cn.solopay

/** Pure text collection shared by the Android bridge and parser regression tests. */
internal object PaymentNotificationText {
    fun combine(shortText: String, bigText: String, lines: List<String>, ticker: String): String {
        val parts = (listOf(shortText, bigText) + lines)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (parts.isEmpty()) ticker.trim() else parts.joinToString("\n")
    }
}
