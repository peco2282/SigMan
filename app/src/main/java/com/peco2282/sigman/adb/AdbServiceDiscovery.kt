package com.peco2282.sigman.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class AdbServiceDiscovery(private val context: Context) {
  private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
  
  companion object {
    const val SERVICE_TYPE_CONNECT = "_adbsecure_connect._tcp."
    const val SERVICE_TYPE_PAIRING = "_adbsecure_pairing._tcp."
  }
  
  private var discoveryListener: NsdManager.DiscoveryListener? = null

  fun startDiscovery(serviceType: String = SERVICE_TYPE_CONNECT, onServiceFound: (NsdServiceInfo) -> Unit) {
    stopDiscovery()
    discoveryListener = object : NsdManager.DiscoveryListener {
      override fun onDiscoveryStarted(regType: String) {
        Log.d("AdbDiscovery", "Discovery started")
      }

      override fun onServiceFound(service: NsdServiceInfo) {
        Log.d("AdbDiscovery", "Service found: ${service.serviceName}")
        // 既に解決中のリスナーがいる可能性を考慮して新しいリスナーを渡す
        try {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
              override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("AdbDiscovery", "Resolve failed: $errorCode")
              }

              override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d("AdbDiscovery", "Service resolved: ${serviceInfo.host}:${serviceInfo.port}")
                onServiceFound(serviceInfo)
              }
            })
        } catch (e: Exception) {
            Log.e("AdbDiscovery", "Error resolving service", e)
        }
      }

      override fun onServiceLost(service: NsdServiceInfo) {
        Log.d("AdbDiscovery", "Service lost: ${service.serviceName}")
      }

      override fun onDiscoveryStopped(regType: String) {
        Log.d("AdbDiscovery", "Discovery stopped")
      }

      override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e("AdbDiscovery", "Start discovery failed: $errorCode")
        stopDiscovery()
      }

      override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e("AdbDiscovery", "Stop discovery failed: $errorCode")
        try {
            nsdManager.stopServiceDiscovery(this)
        } catch (e: Exception) {
            Log.e("AdbDiscovery", "Error in onStopDiscoveryFailed", e)
        }
      }
    }
    try {
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    } catch (e: Exception) {
        Log.e("AdbDiscovery", "Error starting discovery", e)
    }
  }

  fun stopDiscovery() {
    discoveryListener?.let {
      try {
          nsdManager.stopServiceDiscovery(it)
      } catch (e: Exception) {
          Log.e("AdbDiscovery", "Error stopping discovery", e)
      }
      discoveryListener = null
    }
  }
}
