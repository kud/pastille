package app.pastille.settings

enum class ClipboardClearDelay(val key: String, val seconds: Int?, val label: String) {
    Off("off", null, "Off"),
    FiveSeconds("5", 5, "5 s"),
    TenSeconds("10", 10, "10 s"),
    ThirtySeconds("30", 30, "30 s"),
    OneMinute("60", 60, "1 min"),
    ;

    companion object {
        val Default = Off

        fun fromKey(key: String?): ClipboardClearDelay = entries.firstOrNull { it.key == key } ?: Default
    }
}

// "10s" in the snackbar, "1 min" once it reaches a minute.
fun shortDuration(seconds: Int): String =
    if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} min" else "${seconds}s"
