package com.peco2282.sigman

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.peco2282.sigman.ui.components.SigManApp
import com.peco2282.sigman.ui.theme.SigManTheme


class MainActivity : ComponentActivity() {
  companion object {
    const val TAG = "SigMan"
    private const val POLL_INTERVAL_MS = 2_000L
  }

  private val telephonyManager by lazy { getSystemService(TELEPHONY_SERVICE) as TelephonyManager }
  private val subscriptionManager by lazy { getSystemService(SubscriptionManager::class.java) }

  private val displayState = mutableStateOf(DisplayContext())
  private val neighborCellCounts = mutableStateOf<Map<Int, Int>>(emptyMap())

  // Update hooks
  private val telephonyCallbacks = mutableMapOf<Int, TelephonyCallback>()
  private val phoneStateListeners = mutableMapOf<Int, PhoneStateListener>()

  // 最新の情報を保持
  private val latestServiceStates = mutableMapOf<Int, ServiceState>()
  private val latestDisplayInfos = mutableMapOf<Int, TelephonyDisplayInfo>()

  // Fallback polling (for devices that don't push callbacks reliably)
  private val handler by lazy { Handler(Looper.getMainLooper()) }
  private val pollRunnable = object : Runnable {
    override fun run() {
      try {
        updateCellularInfo(null)
      } finally {
        handler.postDelayed(this, POLL_INTERVAL_MS)
      }
    }
  }

  private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    val phoneStateGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
    if (locationGranted && phoneStateGranted) {
      startUpdating()
    } else {
      updatePermissionState()
    }
  }

  private val resolutionLauncher: ActivityResultLauncher<IntentSenderRequest> = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    if (result.resultCode == RESULT_OK) {
      updatePermissionState()
      startUpdating()
    } else {
      updatePermissionState()
    }
  }

  private fun updatePermissionState() {
    val locationGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val phoneStateGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED
    val isEnabled = isLocationEnabled()
    displayState.value = displayState.value.copy(
      hasLocationPermission = locationGranted,
      hasPhoneStatePermission = phoneStateGranted,
      isLocationEnabled = isEnabled
    )
  }

  private fun isLocationEnabled(): Boolean {
    val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
  }

  private fun openLocationSettings() {
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
    val builder = LocationSettingsRequest.Builder()
      .addLocationRequest(locationRequest)

    val client: SettingsClient = LocationServices.getSettingsClient(this)
    val task = client.checkLocationSettings(builder.build())

    task.addOnSuccessListener {
      // 位置情報設定が既に有効
      updatePermissionState()
    }

    task.addOnFailureListener { exception ->
      if (exception is ResolvableApiException) {
        try {
          // 専用のポップアップを表示
          val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
          resolutionLauncher.launch(intentSenderRequest)
        } catch (sendEx: IntentSender.SendIntentException) {
          // 無視
        }
      } else {
        // ポップアップが使えない場合は設定画面を開く
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
      }
    }
  }

  private fun openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
  }

  private val bandsConfig: BandsConfig? by lazy { AssetsLoader.loadBands(this) }
  private val fcnConfig: FCN? by lazy { AssetsLoader.loadFcn(this) }

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SigManTheme {
        SigManApp(
          displayState = displayState.value,
          neighborCellCounts = neighborCellCounts.value,
          fcnConfig = fcnConfig,
          onPermissionRequest = { checkPermissionAndRun() },
          onOpenSettings = { openAppSettings() },
          onOpenLocationSettings = { openLocationSettings() }
        )
      }
    }

    Log.d(TAG, fcnConfig.toString())

    checkPermissionAndRun()
  }

  override fun onStart() {
    super.onStart()
    checkPermissionAndRun()
  }

  override fun onStop() {
    super.onStop()
    stopUpdating()
  }

  private fun checkPermissionAndRun() {
    val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    val phoneStatePermission = Manifest.permission.READ_PHONE_STATE

    val locationGranted =
      ContextCompat.checkSelfPermission(this, locationPermission) == PackageManager.PERMISSION_GRANTED
    val phoneStateGranted =
      ContextCompat.checkSelfPermission(this, phoneStatePermission) == PackageManager.PERMISSION_GRANTED

    if (locationGranted && phoneStateGranted) {
      updatePermissionState()
      startUpdating()
    } else {
      updatePermissionState()
      requestPermissionLauncher.launch(arrayOf(locationPermission, phoneStatePermission))
    }
  }

  private fun startUpdating() {
    updatePermissionState()
    // Check permissions before registering callbacks
    val locationGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val phoneStateGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    if (!locationGranted || !phoneStateGranted) {
      Log.w(
        TAG,
        "Cannot start updating: permissions not granted (Location: $locationGranted, PhoneState: $phoneStateGranted)"
      )
      updatePermissionState()
      return
    }

    // Ensure initial load
    updateCellularInfo(null)

    val activeSubscriptions = try {
      subscriptionManager.activeSubscriptionInfoList ?: emptyList()
    } catch (e: SecurityException) {
      emptyList()
    }

    activeSubscriptions.forEach { sub ->
      val subId = sub.subscriptionId
      val subTelephonyManager = telephonyManager.createForSubscriptionId(subId)

      // Register callbacks depending on API level
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (!telephonyCallbacks.containsKey(subId)) {
          val callback = object : TelephonyCallback(),
            TelephonyCallback.CellInfoListener,
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.ServiceStateListener,
            TelephonyCallback.DisplayInfoListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
              updateCellularInfo(subId)
            }

            override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
              updateCellularInfo(subId)
            }

            override fun onServiceStateChanged(serviceState: ServiceState) {
              latestServiceStates[subId] = serviceState
              updateCellularInfo(subId)
            }

            override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
              latestDisplayInfos[subId] = telephonyDisplayInfo
              updateCellularInfo(subId)
            }
          }
          telephonyCallbacks[subId] = callback
          try {
            subTelephonyManager.registerTelephonyCallback(mainExecutor, callback)
          } catch (se: SecurityException) {
            Log.e(TAG, "registerTelephonyCallback SecurityException: ${se.message}")
          }
        }
      } else {
        if (!phoneStateListeners.containsKey(subId)) {
          val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
              updateCellularInfo(subId)
            }

            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
              updateCellularInfo(subId)
            }

            override fun onServiceStateChanged(serviceState: ServiceState?) {
              if (serviceState != null) {
                latestServiceStates[subId] = serviceState
              }
              updateCellularInfo(subId)
            }

            override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
              latestDisplayInfos[subId] = telephonyDisplayInfo
              updateCellularInfo(subId)
            }
          }
          phoneStateListeners[subId] = listener
          try {
            @Suppress("DEPRECATION")
            subTelephonyManager.listen(
              listener,
              @Suppress("DEPRECATION") (
                  PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                      PhoneStateListener.LISTEN_CELL_INFO or
                      PhoneStateListener.LISTEN_SERVICE_STATE or
                      PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED
                  )
            )
          } catch (se: SecurityException) {
            Log.e(TAG, "listen SecurityException: ${se.message}")
          }
        }
      }
    }

    // Start fallback polling
    handler.removeCallbacks(pollRunnable)
    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
  }

  private fun stopUpdating() {
    // Stop callbacks
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      telephonyCallbacks.forEach { (subId, callback) ->
        try {
          telephonyManager.createForSubscriptionId(subId).unregisterTelephonyCallback(callback)
        } catch (t: Throwable) {
          // ignore
        }
      }
      telephonyCallbacks.clear()
    } else {
      phoneStateListeners.forEach { (subId, listener) ->
        try {
          @Suppress("DEPRECATION")
          telephonyManager.createForSubscriptionId(subId).listen(listener, PhoneStateListener.LISTEN_NONE)
        } catch (t: Throwable) {
          // ignore
        }
      }
      phoneStateListeners.clear()
    }
    // Stop polling
    handler.removeCallbacks(pollRunnable)
  }

  private fun convertToCellularInfo(info: CellInfo, now: Long, subId: Int): CellularInfo {
    val subTelephonyManager = telephonyManager.createForSubscriptionId(subId)
    val latestServiceState = latestServiceStates[subId]
    val latestDisplayInfo = latestDisplayInfos[subId]

    val serviceStateStr = when (latestServiceState?.state) {
      ServiceState.STATE_IN_SERVICE -> "IN_SERVICE"
      ServiceState.STATE_OUT_OF_SERVICE -> "OUT_OF_SERVICE"
      ServiceState.STATE_EMERGENCY_ONLY -> "EMERGENCY_ONLY"
      ServiceState.STATE_POWER_OFF -> "POWER_OFF"
      else -> null
    }

    val isEnDcAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      latestDisplayInfo?.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
          latestDisplayInfo?.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
    } else null

    val dataNetworkType = try {
      when (subTelephonyManager.dataNetworkType) {
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        else -> "UNKNOWN"
      }
    } catch (se: SecurityException) {
      "PERMISSION_DENIED"
    }

    val baseInfo = when (info) {
      is CellInfoLte -> {
        val identity = info.cellIdentity
        val signalStrength = info.cellSignalStrength
        val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) identity.bands else intArrayOf()
        var bandString = if (bands.isNotEmpty()) "B${bands.joinToString(", ")}" else null

        if (bandString == null) {
          val (b, _) = CarrierUtils.getEarfcnDetails(fcnConfig, identity.earfcn)
          if (b != null) {
            bandString = b
          }
        }

        val carrierKey = CarrierUtils.getCarrierName(identity.mccString, identity.mncString)
        val details = carrierKey?.let { key ->
          bandsConfig?.carriers?.get("4G")?.getBandsByCarrier(key)?.find { b ->
            bands.any { it.toString() == b.band.replace("B", "").split("/").first() }
          }
        }

        CellularInfo(
          networkType = NetworkType.LTE,
          providerName = subTelephonyManager.networkOperatorName,
          mcc = identity.mccString,
          mnc = identity.mncString,
          rsrp = signalStrength.rsrp.takeIf { it != CellInfo.UNAVAILABLE },
          rsrq = signalStrength.rsrq.takeIf { it != CellInfo.UNAVAILABLE },
          rssi = signalStrength.rssi.takeIf { it != CellInfo.UNAVAILABLE },
          sinr = signalStrength.rssnr.takeIf { it != CellInfo.UNAVAILABLE },
          rssnr = signalStrength.rssnr.takeIf { it != CellInfo.UNAVAILABLE },
          cqi = signalStrength.cqi.takeIf { it != CellInfo.UNAVAILABLE },
          ta = signalStrength.timingAdvance.takeIf { it != CellInfo.UNAVAILABLE },
          earfcn = identity.earfcn.takeIf { it != CellInfo.UNAVAILABLE },
          pci = identity.pci.takeIf { it != CellInfo.UNAVAILABLE },
          cid = identity.ci.toLong().takeIf { it != CellInfo.UNAVAILABLE.toLong() },
          tac = identity.tac.takeIf { it != CellInfo.UNAVAILABLE },
          bandwidth = identity.bandwidth.takeIf { it != CellInfo.UNAVAILABLE },
          band = bandString,
          isRegistered = info.isRegistered,
          bandDetails = details,
          timestampNs = info.timeStamp,
          collectedAt = now
        )
      }

      is CellInfoNr -> {
        val identity = info.cellIdentity as CellIdentityNr
        val signalStrength = info.cellSignalStrength as CellSignalStrengthNr
        val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) identity.bands else intArrayOf()
        var bandString = if (bands.isNotEmpty()) "n${bands.joinToString(", ")}" else null

        if (bandString == null) {
          val (b, _) = CarrierUtils.getNrfcnDetails(fcnConfig, identity.nrarfcn)
          if (b != null) {
            bandString = b
          }
        }

        val carrierKey = CarrierUtils.getCarrierName(identity.mccString, identity.mncString)
        val details = carrierKey?.let { key ->
          bandsConfig?.carriers?.get("5G")?.getBandsByCarrier(key)?.find { b ->
            bands.any { it.toString() == b.band.replace("n", "") }
          }
        }

        val rsrp = (signalStrength.ssRsrp.takeIf { it != CellInfo.UNAVAILABLE }
          ?: signalStrength.csiRsrp.takeIf { it != CellInfo.UNAVAILABLE }
          ?: signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE })
        val rsrq = (signalStrength.ssRsrq.takeIf { it != CellInfo.UNAVAILABLE }
          ?: signalStrength.csiRsrq.takeIf { it != CellInfo.UNAVAILABLE })
        val sinr = (signalStrength.ssSinr.takeIf { it != CellInfo.UNAVAILABLE }
          ?: signalStrength.csiSinr.takeIf { it != CellInfo.UNAVAILABLE })

        CellularInfo(
          networkType = NetworkType.NR,
          providerName = subTelephonyManager.networkOperatorName,
          mcc = identity.mccString,
          mnc = identity.mncString,
          rsrp = rsrp,
          rsrq = rsrq,
          rssi = null,
          sinr = sinr,
          nrarfcn = identity.nrarfcn.takeIf { it != CellInfo.UNAVAILABLE },
          pci = identity.pci.takeIf { it != CellInfo.UNAVAILABLE },
          cid = identity.nci.takeIf { it != CellInfo.UNAVAILABLE_LONG },
          tac = identity.tac.takeIf { it != CellInfo.UNAVAILABLE },
          band = bandString,
          isRegistered = info.isRegistered,
          bandDetails = details,
          timestampNs = info.timeStamp,
          collectedAt = now
        )
      }

      is CellInfoWcdma -> {
        val identity = info.cellIdentity
        val signalStrength = info.cellSignalStrength
        CellularInfo(
          networkType = NetworkType.WCDMA,
          providerName = subTelephonyManager.networkOperatorName,
          mcc = identity.mccString,
          mnc = identity.mncString,
          rsrp = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
          rssi = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
          uarfcn = identity.uarfcn.takeIf { it != CellInfo.UNAVAILABLE },
          psc = identity.psc.takeIf { it != CellInfo.UNAVAILABLE },
          cid = identity.cid.toLong().takeIf { it != CellInfo.UNAVAILABLE.toLong() },
          lac = identity.lac.takeIf { it != CellInfo.UNAVAILABLE },
          isRegistered = info.isRegistered,
          timestampNs = info.timeStamp,
          collectedAt = now
        )
      }

      is CellInfoGsm -> {
        val identity = info.cellIdentity
        val signalStrength = info.cellSignalStrength
        CellularInfo(
          networkType = NetworkType.GSM,
          providerName = subTelephonyManager.networkOperatorName,
          mcc = identity.mccString,
          mnc = identity.mncString,
          rssi = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
          rsrp = signalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
          arfcn = identity.arfcn.takeIf { it != CellInfo.UNAVAILABLE },
          bsic = identity.bsic.takeIf { it != CellInfo.UNAVAILABLE },
          cid = identity.cid.toLong().takeIf { it != CellInfo.UNAVAILABLE.toLong() },
          lac = identity.lac.takeIf { it != CellInfo.UNAVAILABLE },
          ta = signalStrength.timingAdvance.takeIf { it != CellInfo.UNAVAILABLE },
          isRegistered = info.isRegistered,
          timestampNs = info.timeStamp,
          collectedAt = now
        )
      }

      else -> CellularInfo(networkType = NetworkType.UNKNOWN, collectedAt = now)
    }

    return try {
      baseInfo.copy(
        serviceState = serviceStateStr,
        roaming = latestServiceState?.roaming,
        dataNetworkType = dataNetworkType,
        isManualSelection = latestServiceState?.isManualSelection,
        operatorAlphaLong = latestServiceState?.operatorAlphaLong,
        operatorAlphaShort = latestServiceState?.operatorAlphaShort,
        operatorNumeric = latestServiceState?.operatorNumeric,
        isEnDcAvailable = isEnDcAvailable
      )
    } catch (se: SecurityException) {
      baseInfo.copy(
        serviceState = serviceStateStr,
        roaming = latestServiceState?.roaming,
        dataNetworkType = dataNetworkType,
        isEnDcAvailable = isEnDcAvailable
      )
    }
  }

  private fun updateCellularInfo(targetSubId: Int? = null) {
    updatePermissionState()
    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    val activeSubscriptions = try {
      subscriptionManager.activeSubscriptionInfoList ?: emptyList()
    } catch (e: SecurityException) {
      emptyList()
    }

    displayState.value = displayState.value.copy(
      subInfo = activeSubscriptions
    )

    activeSubscriptions.forEach { sub ->
      val subId = sub.subscriptionId
      if (targetSubId != null && targetSubId != subId) return@forEach

      val subTelephonyManager = telephonyManager.createForSubscriptionId(subId)
      val now = System.currentTimeMillis()

      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          subTelephonyManager.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
            override fun onCellInfo(cellInfo: List<CellInfo>) {
              processCellInfo(cellInfo, System.currentTimeMillis(), subId)
            }
          })
        } else {
          val all = subTelephonyManager.allCellInfo ?: emptyList<CellInfo>()
          processCellInfo(all, now, subId)
        }
      } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException for subId $subId: ${e.message}")
      }
    }
  }

  private fun processCellInfo(all: List<CellInfo>, now: Long, subId: Int) {
    val infos = all.filter { it.isRegistered }.map { convertToCellularInfo(it, now, subId) }
    val unregistered = all.filter { !it.isRegistered }.map { convertToCellularInfo(it, now, subId) }

    val currentNeighbors = neighborCellCounts.value.toMutableMap()
    currentNeighbors[subId] = all.size - infos.size
    neighborCellCounts.value = currentNeighbors

    val currentPerSubInfos = displayState.value.perSubCellularInfos.toMutableMap()
    currentPerSubInfos[subId] = infos
    val currentPerSubBands = displayState.value.perSubCarrierBands.toMutableMap()
    currentPerSubBands[subId] = unregistered

    // デフォルト表示（最初のSIM）も念のため更新
    val firstSubId = displayState.value.subInfo.firstOrNull()?.subscriptionId
    val defaultInfos = if (subId == firstSubId || firstSubId == null) infos else displayState.value.cellularInfos
    val defaultBands = if (subId == firstSubId || firstSubId == null) unregistered else displayState.value.carrierBands

    displayState.value = displayState.value.copy(
      cellularInfos = defaultInfos,
      carrierBands = defaultBands,
      perSubCellularInfos = currentPerSubInfos,
      perSubCarrierBands = currentPerSubBands,
      lastUpdated = System.currentTimeMillis()
    )
  }
}
