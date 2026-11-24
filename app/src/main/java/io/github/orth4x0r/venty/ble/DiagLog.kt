package io.github.orth4x0r.venty.ble

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A diagnostics log for comparing what the device reports with what the app shows: status
 * changes, writes and link events. Each line goes to logcat under [TAG] (`adb logcat -s VentyDiag`)
 * and to a file in the app's storage that survives restarts, so it can be shared from Settings.
 */
object DiagLog {
    const val TAG = "VentyDiag"
    private const val MAX_LINES = 3000
    private const val MAX_FILE_BYTES = 600_000L

    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun init(context: Context) {
        val f = File(context.filesDir, "diag.log")
        file = f
        runCatching { if (f.exists()) f.readLines().takeLast(MAX_LINES).forEach(lines::addLast) }
    }

    @Synchronized
    fun log(text: String) {
        Log.i(TAG, text)
        val line = "${time.format(Date())} $text"
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        val f = file ?: return
        runCatching {
            f.appendText(line + "\n")
            // Keep the file from growing without end: rewrite it from what's kept in memory.
            if (f.length() > MAX_FILE_BYTES) f.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }

    /** The whole log, oldest first. */
    @Synchronized
    fun dump(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        runCatching { file?.writeText("") }
    }
}
