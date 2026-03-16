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

  private val displayState = mutableStateOf(DisplayContext())
  private val neighborCellCount = mutableIntStateOf(0)

  // Update hooks
  private var telephonyCallback: TelephonyCallback? = null
  private var phoneStateListener: PhoneStateListener? = null

  // Fallback polling (for devices that don't push callbacks reliably)
  private val handler by lazy { Handler(Looper.getMainLooper()) }
  private val pollRunnable = object : Runnable {
    override fun run() {
      try {
        updateCellularInfo()
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
          neighborCellCount = neighborCellCount.intValue,
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
    // Ensure initial load
    updateCellularInfo()

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
      return
    }

    // Register callbacks depending on API level
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (telephonyCallback == null) {
        val callback = object : TelephonyCallback(),
          TelephonyCallback.CellInfoListener,
          TelephonyCallback.SignalStrengthsListener {
          override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            updateCellularInfo()
          }

          override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
            updateCellularInfo()
          }
        }
        telephonyCallback = callback
        try {
          telephonyManager.registerTelephonyCallback(mainExecutor, callback)
        } catch (se: SecurityException) {
          Log.e(TAG, "registerTelephonyCallback SecurityException: ${se.message}")
        }
      }
    } else {
      if (phoneStateListener == null) {
        val listener = object : PhoneStateListener() {
          override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            updateCellularInfo()
          }

          override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            updateCellularInfo()
          }
        }
        phoneStateListener = listener
        try {
          @Suppress("DEPRECATION")
          telephonyManager.listen(
            listener,
            @Suppress("DEPRECATION") (PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_CELL_INFO)
          )
        } catch (se: SecurityException) {
          Log.e(TAG, "listen SecurityException: ${se.message}")
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
      telephonyCallback?.let {
        try {
          telephonyManager.unregisterTelephonyCallback(it)
        } catch (t: Throwable) {
          // ignore
        }
      }
      telephonyCallback = null
    } else {
      phoneStateListener?.let {
        try {
          @Suppress("DEPRECATION")
          telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        } catch (t: Throwable) {
          // ignore
        }
      }
      phoneStateListener = null
    }
    // Stop polling
    handler.removeCallbacks(pollRunnable)
  }

  private fun convertToCellularInfo(info: CellInfo, now: Long): CellularInfo {
    return when (info) {
      is CellInfoLte -> {
        val identity = info.cellIdentity
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
          providerName = telephonyManager.networkOperatorName,
          mcc = identity.mccString,
          mnc = identity.mncString,
          rsrp = info.cellSignalStrength.dbm.takeIf { it != CellInfo.UNAVAILABLE },
          rsrq = info.cellSignalStrength.rsrq.takeIf { it != CellInfo.UNAVAILABLE },
          rssi = info.cellSignalStrength.rssi.takeIf { it != CellInfo.UNAVAILABLE },
          sinr = info.cellSignalStrength.rssnr.takeIf { it != CellInfo.UNAVAILABLE },
          earfcn = identity.earfcn,
          pci = identity.pci.takeIf { it != CellInfo.UNAVAILABLE },
          bandwidth = identity.bandwidth,
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
          providerName = telephonyManager.networkOperatorName,
          mcc = identity.mccString,
          mnc = identity.mncString,
          rsrp = rsrp,
          rsrq = rsrq,
          rssi = null, // RSSI is not directly available in CellSignalStrengthNr
          sinr = sinr,
          nrarfcn = identity.nrarfcn,
          pci = identity.pci.takeIf { it != CellInfo.UNAVAILABLE },
          band = bandString,
          isRegistered = info.isRegistered,
          bandDetails = details,
          timestampNs = info.timeStamp,
          collectedAt = now
        )
      }

      else -> CellularInfo(networkType = NetworkType.UNKNOWN, collectedAt = now)
    }
  }

  private fun updateCellularInfo() {
    updatePermissionState()
    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    val now = System.currentTimeMillis()

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        telephonyManager.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
          override fun onCellInfo(cellInfo: List<CellInfo>) {
            processCellInfo(cellInfo, System.currentTimeMillis())
          }
        })
      } else {
        val all = telephonyManager.allCellInfo ?: emptyList<CellInfo>()
        processCellInfo(all, now)
      }
    } catch (e: SecurityException) {
      Log.e(TAG, "SecurityException: ${e.message}")
    }
  }

  private fun processCellInfo(all: List<CellInfo>, now: Long) {
    val infos = all.filter { it.isRegistered }.map { convertToCellularInfo(it, now) }
    val unregistered = all.filter { !it.isRegistered }.map { convertToCellularInfo(it, now) }

    neighborCellCount.intValue = all.size - infos.size

    // 要件に基づくリスト構築: 現在接続中のセルラーデータのみを表示
    displayState.value = displayState.value.copy(
      cellularInfos = infos,
      carrierBands = unregistered,
      lastUpdated = System.currentTimeMillis()
    )
  }
}
