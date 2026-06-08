package co.golink.tester.data

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class LogEntry(val timestamp: Long, val tag: String, val message: String) {
    fun timeFormatted(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

    fun dateFormatted(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
}

@Singleton
class AppLogger @Inject constructor() {
    private val _entries = ArrayDeque<LogEntry>()

    fun log(tag: String, message: String) {
        synchronized(_entries) {
            if (_entries.size >= 500) _entries.removeFirst()
            _entries.addLast(LogEntry(System.currentTimeMillis(), tag, message))
        }
        Log.d(tag, message)
    }

    fun getEntries(): List<LogEntry> = synchronized(_entries) { _entries.reversed() }

    fun clear() = synchronized(_entries) { _entries.clear() }
}
