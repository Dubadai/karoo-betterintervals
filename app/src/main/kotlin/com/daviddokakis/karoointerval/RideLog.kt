package com.daviddokakis.karoointerval

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * A small log in the app's own storage: every info, warning and error line with its time, so a
 * freeze or a restart mid-ride can still be traced after the Karoo's logcat is gone (a Karoo
 * restart empties it). `files/ridelog.txt`, the one before in `ridelog.1.txt`; debug/pull.sh
 * copies them from debug builds. Never log the API key: this file can be shared.
 */
class RideLogTree(context: Context) : Timber.Tree() {
    private val dir = context.filesDir
    private val file = File(dir, NAME)
    // Off the caller's thread, one line after another
    private val writer = Executors.newSingleThreadExecutor()

    init {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        write("S", "start ${context.packageName} ${version ?: "?"}")
    }

    override fun isLoggable(tag: String?, priority: Int) = priority >= Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            Log.WARN -> "W"
            Log.ERROR, Log.ASSERT -> "E"
            else -> "I"
        }
        // Timber puts the stack trace into the message; one line with the error is enough here
        val text = if (t != null) "${message.substringBefore('\n')} | ${t.javaClass.simpleName}: ${t.message}" else message
        write(level, text)
    }

    private fun write(level: String, message: String) {
        val line = rideLogLine(LocalDateTime.now(), level, message)
        writer.execute {
            runCatching {
                if (file.length() > MAX_BYTES) file.renameTo(File(dir, PREVIOUS))
                file.appendText(line)
            }
        }
    }

    companion object {
        const val NAME = "ridelog.txt"
        private const val PREVIOUS = "ridelog.1.txt"
        private const val MAX_BYTES = 256 * 1024L
    }
}

private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

/** One line of the ride log: time, level (S start, I, W, E), the message on one line. */
internal fun rideLogLine(time: LocalDateTime, level: String, message: String) =
    "${time.format(TIME)} $level ${message.replace('\n', ' ')}\n"
