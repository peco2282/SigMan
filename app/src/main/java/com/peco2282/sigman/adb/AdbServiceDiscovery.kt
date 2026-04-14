package com.peco2282.sigman.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class AdbServiceDiscovery(private val context: Context) {
  private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
  private val serviceType = "_adbsecure_connect._tcp."
  private var discoveryListener: NsdManager.DiscoveryListener? = null

  fun startDiscovery(onServiceFound: (NsdServiceInfo) -> Unit) {
    stopDiscovery()
    discoveryListener = object : NsdManager.DiscoveryListener {
      override fun onDiscoveryStarted(regType: String) {
        Log.d("AdbDiscovery", "Discovery started")
      }

      override fun onServiceFound(service: NsdServiceInfo) {
        Log.d("AdbDiscovery", "Service found: ${service.serviceName}")
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
          override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e("AdbDiscovery", "Resolve failed: $errorCode")
          }

          override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.d("AdbDiscovery", "Service resolved: ${serviceInfo.host}:${serviceInfo.port}")
            onServiceFound(serviceInfo)
          }
        })
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
        nsdManager.stopServiceDiscovery(this)
      }
    }
    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
  }

  fun stopDiscovery() {
    discoveryListener?.let {
      nsdManager.stopServiceDiscovery(it)
      discoveryListener = null
    }
  }
}
