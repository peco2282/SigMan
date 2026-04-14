package com.peco2282.sigman.adb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class AdbShellExecutor(private val context: Context) {
  private val connectionManager = AdbConnectionManagerImpl.getInstance(context)

  suspend fun pair(host: String, port: Int, pairingCode: String): Boolean = withContext(Dispatchers.IO) {
    try {
      connectionManager.pair(host, port, pairingCode)
      true
    } catch (e: Exception) {
      Log.e("AdbShell", "Pairing failed", e)
      false
    }
  }

  suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
    try {
      connectionManager.hostAddress = host
      connectionManager.connect(port)
      true
    } catch (e: Exception) {
      Log.e("AdbShell", "Connection failed", e)
      false
    }
  }

  suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
    if (!connectionManager.isConnected) {
      return@withContext "Error: Not connected"
    }
    try {
      val stream = connectionManager.openStream("shell:$command")
      val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
      val result = try {
        reader.readText()
      } finally {
        reader.close()
      }
      stream.close()
      result
    } catch (e: Exception) {
      Log.e("AdbShell", "Command execution failed: $command", e)
      "Error: ${e.message}"
    }
  }

  fun isConnected(): Boolean = try {
    connectionManager.isConnected
  } catch (e: Exception) {
    false
  }
}
