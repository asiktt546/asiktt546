package com.meshchat.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.meshchat.CrashReporter
import com.meshchat.MainActivity
import com.meshchat.R
import com.meshchat.crypto.CryptoManager
import com.meshchat.data.MessageDatabase
import com.meshchat.data.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Фоновый сервис для периодического обнаружения узлов и синхронизации банка сообщений.
 */
class MeshBackgroundService : Service() {

    companion object {
        private const val TAG = "MeshBackgroundService"
        private const val CHANNEL_ID = "mesh_background_sync"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.meshchat.mesh.ACTION_START_BACKGROUND_SYNC"
        const val ACTION_STOP = "com.meshchat.mesh.ACTION_STOP_BACKGROUND_SYNC"

        private const val DISCOVERY_INTERVAL_MS = 2 * 60 * 1000L

        fun start(context: Context): Boolean {
            val intent = Intent(context, MeshBackgroundService::class.java).apply {
                action = ACTION_START
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось запустить MeshBackgroundService", e)
                CrashReporter.recordHandledError(context, TAG, "Не удалось запустить MeshBackgroundService", e)
                false
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var periodicJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    private lateinit var cryptoManager: CryptoManager
    private lateinit var meshManager: MeshManager
    private lateinit var messageRouter: MessageRouter

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquirePerformanceLocks()
        BackgroundRuntimeStatus.updateServiceRunning(true)

        cryptoManager = CryptoManager().also { it.initialize() }
        meshManager = MeshManager(this).also { it.initialize() }

        val repository = MessageRepository(MessageDatabase.getInstance(this).messageDao())
        messageRouter = MessageRouter(cryptoManager, meshManager, repository).also { it.initialize() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, buildNotification("Фоновый обмен включен"))
                startPeriodicLoopIfNeeded()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        periodicJob?.cancel()
        meshManager.cleanup()
        releasePerformanceLocks()
        BackgroundRuntimeStatus.updateServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPeriodicLoopIfNeeded() {
        if (periodicJob?.isActive == true) return

        periodicJob = serviceScope.launch {
            while (isActive) {
                meshManager.discoverPeers()

                val hostAddress = meshManager.connectionInfo.value?.groupOwnerAddress?.hostAddress
                if (!hostAddress.isNullOrBlank()) {
                    messageRouter.requestBankSync(hostAddress)
                }

                delay(DISCOVERY_INTERVAL_MS)
            }
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh Background Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Фоновый обмен данными и поиск ближайших узлов MeshChat"
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquirePerformanceLocks() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "meshchat:highperf-wifi"
            ).apply {
                setReferenceCounted(false)
                if (!isHeld) {
                    acquire()
                }
            }
            BackgroundRuntimeStatus.updateWifiLockHeld(wifiLock?.isHeld == true)
        } else {
            BackgroundRuntimeStatus.updateWifiLockHeld(false)
        }

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null) {
            cpuWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "meshchat:cpu"
            ).apply {
                setReferenceCounted(false)
                if (!isHeld) {
                    acquire()
                }
            }
            BackgroundRuntimeStatus.updateWakeLockHeld(cpuWakeLock?.isHeld == true)
        } else {
            BackgroundRuntimeStatus.updateWakeLockHeld(false)
        }
    }

    private fun releasePerformanceLocks() {
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wifiLock = null
        BackgroundRuntimeStatus.updateWifiLockHeld(false)

        cpuWakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        cpuWakeLock = null
        BackgroundRuntimeStatus.updateWakeLockHeld(false)
    }
}
