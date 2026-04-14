package com.peco2282.sigman.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdbMeasurementService : Service() {
  private val serviceJob = Job()
  private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

  private lateinit var adbShell: AdbShellExecutor
  private val parser = AdbSignalParser()

  private var isRunning = false
  private var pollingInterval = 1000L // 1 second as per requirement

  companion object {
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "adb_measurement_channel"
    const val ACTION_START = "ACTION_START"
    const val ACTION_STOP = "ACTION_STOP"

    private val _signalFlow = MutableStateFlow<AdbSignalData?>(null)
    val signalFlow: StateFlow<AdbSignalData?> = _signalFlow.asStateFlow()

    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
  }

  override fun onCreate() {
    super.onCreate()
    adbShell = AdbShellExecutor(this)
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> startMeasurement()
      ACTION_STOP -> stopMeasurement()
    }
    return START_STICKY
  }

  private fun startMeasurement() {
    if (isRunning) return
    isRunning = true

    val notification = createNotification("ADB計測中...")
    startForeground(NOTIFICATION_ID, notification)

    serviceScope.launch {
      while (isRunning) {
        val connected = adbShell.isConnected()
        _connectionStatus.value = connected

        if (connected) {
          val output = adbShell.executeCommand("dumpsys telephony.registry")
          val signalData = parser.parseTelephonyRegistry(output)

          _signalFlow.value = signalData
          Log.d("AdbService", "Signal: RSRP=${signalData.rsrp}, NR=${signalData.nrState}")

          updateNotification("ADB計測中: RSRP=${signalData.rsrp ?: "N/A"} dBm")
        } else {
          Log.w("AdbService", "ADB not connected")
          updateNotification("ADB未接続")
        }
        delay(pollingInterval)
      }
    }
  }

  private fun stopMeasurement() {
    isRunning = false
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    super.onDestroy()
    serviceJob.cancel()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "ADB Measurement Service",
        NotificationManager.IMPORTANCE_LOW
      )
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(content: String): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("SigMan ADB Analyzer")
      .setContentText(content)
      .setSmallIcon(android.R.drawable.ic_menu_info_details)
      .build()
  }

  private fun updateNotification(content: String) {
    val notification = createNotification(content)
    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(NOTIFICATION_ID, notification)
  }
}
