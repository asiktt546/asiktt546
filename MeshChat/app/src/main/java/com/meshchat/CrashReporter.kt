package com.meshchat

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess

/**
 * Сохраняет диагностические логи с причиной падений и ошибок в файл приложения.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val LOG_FILE_NAME = "meshchat-crash.log"

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            val appContext = context.applicationContext
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    appendLog(
                        appContext,
                        buildString {
                            appendLine("=== UNCAUGHT EXCEPTION ===")
                            appendLine("time=${timestamp()}")
                            appendLine("thread=${thread.name}")
                            appendLine("type=${throwable::class.java.name}")
                            appendLine("message=${throwable.message ?: "<no-message>"}")
                            appendLine(Log.getStackTraceString(throwable))
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Не удалось сохранить необработанное исключение", e)
                }

                previousHandler?.uncaughtException(thread, throwable) ?: run {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }

            initialized = true
        }
    }

    fun recordHandledError(context: Context, source: String, message: String, throwable: Throwable? = null) {
        try {
            appendLog(
                context.applicationContext,
                buildString {
                    appendLine("=== HANDLED ERROR ===")
                    appendLine("time=${timestamp()}")
                    appendLine("source=$source")
                    appendLine("message=$message")
                    if (throwable != null) {
                        appendLine("type=${throwable::class.java.name}")
                        appendLine("throwableMessage=${throwable.message ?: "<no-message>"}")
                        appendLine(Log.getStackTraceString(throwable))
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось сохранить обработанную ошибку", e)
        }
    }

    fun readLog(context: Context): String {
        val file = getLogFile(context.applicationContext)
        if (!file.exists()) return "Лог ошибок пока пуст"
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            "Не удалось прочитать лог: ${e.message}"
        }
    }

    fun clearLog(context: Context) {
        val file = getLogFile(context.applicationContext)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun getLogFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)

    private fun appendLog(context: Context, block: String) {
        val file = getLogFile(context)
        file.appendText("$block\n", Charsets.UTF_8)
    }

    private fun timestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        format.timeZone = TimeZone.getDefault()
        return format.format(Date())
    }
}
