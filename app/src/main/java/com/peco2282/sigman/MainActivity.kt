package com.peco2282.sigman

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
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

  private val requestPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted: Boolean ->
    if (isGranted) {
      startUpdating()
    } else {
      updatePermissionState()
    }
  }

  private val resolutionLauncher: ActivityResultLauncher<IntentSenderRequest> = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      updatePermissionState()
      startUpdating()
    } else {
      updatePermissionState()
    }
  }

  private fun updatePermissionState() {
    val isGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val isEnabled = isLocationEnabled()
    displayState.value = displayState.value.copy(
      hasLocationPermission = isGranted,
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SigManTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          val context = displayState.value
          if (!context.hasLocationPermission || !context.isLocationEnabled) {
            PermissionRequiredView(
              hasPermission = context.hasLocationPermission,
              isLocationEnabled = context.isLocationEnabled,
              onPermissionRequest = { checkPermissionAndRun() },
              onOpenSettings = { openAppSettings() },
              onOpenLocationSettings = { openLocationSettings() },
              modifier = Modifier.padding(innerPadding)
            )
          } else {
            CellularInfoList(
              displayContext = context,
              modifier = Modifier.padding(innerPadding),
              neighborCellCount.intValue,
              fcnConfig
            )
          }
        }
      }
    }

    Log.d(TAG, fcnConfig.toString())

    checkPermissionAndRun()
  }

  override fun onStart() {
    super.onStart()
    startUpdating()
  }

  override fun onStop() {
    super.onStop()
    stopUpdating()
  }

  private fun checkPermissionAndRun() {
    when {
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED -> {
        updatePermissionState()
        startUpdating()
      }

      ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) -> {
        updatePermissionState()
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
      }

      else -> {
        updatePermissionState()
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
      }
    }
  }

  private fun startUpdating() {
    // Ensure initial load
    updateCellularInfo()

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

  private fun updateCellularInfo() {
    updatePermissionState()
    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    try {
      val all = telephonyManager.allCellInfo ?: emptyList<CellInfo>()
      val infos = all.filter { it.isRegistered }.map { info ->
        when (info) {
          is CellInfoLte -> {
            val identity = info.cellIdentity
            val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) identity.bands else intArrayOf()
            val bandString = if (bands.isNotEmpty()) "B${bands.joinToString(", ")}" else null

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
              dbm = info.cellSignalStrength.dbm,
              earfcn = identity.earfcn,
              bandwidth = identity.bandwidth,
              band = bandString,
              isRegistered = true,
              bandDetails = details
            )
          }

          is CellInfoNr -> {
            val identity = info.cellIdentity as CellIdentityNr
            val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) identity.bands else intArrayOf()
            val bandString = if (bands.isNotEmpty()) "n${bands.joinToString(", ")}" else null

            val carrierKey = CarrierUtils.getCarrierName(identity.mccString, identity.mncString)
            val details = carrierKey?.let { key ->
              bandsConfig?.carriers?.get("5G")?.getBandsByCarrier(key)?.find { b ->
                bands.any { it.toString() == b.band.replace("n", "") }
              }
            }

            CellularInfo(
              networkType = NetworkType.NR,
              providerName = telephonyManager.networkOperatorName,
              mcc = identity.mccString,
              mnc = identity.mncString,
              dbm = info.cellSignalStrength.dbm,
              nrarfcn = identity.nrarfcn,
              band = bandString,
              isRegistered = info.isRegistered,
              bandDetails = details
            )
          }

          else -> CellularInfo(networkType = NetworkType.UNKNOWN)
        }
      }

      val unregistered = all.filter { !it.isRegistered }.map { info ->
        when (info) {
          is CellInfoLte -> {
            val identity = info.cellIdentity
            val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) identity.bands else intArrayOf()
            val bandString = if (bands.isNotEmpty()) "B${bands.joinToString(", ")}" else null

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
              dbm = info.cellSignalStrength.dbm,
              earfcn = identity.earfcn,
              bandwidth = identity.bandwidth,
              band = bandString,
              isRegistered = info.isRegistered,
              bandDetails = details
            )
          }

          is CellInfoNr -> {
            val identity = info.cellIdentity as CellIdentityNr
            val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) identity.bands else intArrayOf()
            val bandString = if (bands.isNotEmpty()) "n${bands.joinToString(", ")}" else null

            val carrierKey = CarrierUtils.getCarrierName(identity.mccString, identity.mncString)
            val details = carrierKey?.let { key ->
              bandsConfig?.carriers?.get("5G")?.getBandsByCarrier(key)?.find { b ->
                bands.any { it.toString() == b.band.replace("n", "") }
              }
            }

            CellularInfo(
              networkType = NetworkType.NR,
              providerName = telephonyManager.networkOperatorName,
              mcc = identity.mccString,
              mnc = identity.mncString,
              dbm = info.cellSignalStrength.dbm,
              nrarfcn = identity.nrarfcn,
              band = bandString,
              isRegistered = false,
              bandDetails = details
            )
          }

          else -> CellularInfo(networkType = NetworkType.UNKNOWN)
        }
      }

      neighborCellCount.intValue = all.size - infos.size

      // 要件に基づくリスト構築: 現在接続中のセルラーデータのみを表示
      displayState.value = displayState.value.copy(
        cellularInfos = infos,
        carrierBands = unregistered,
        lastUpdated = System.currentTimeMillis()
      )
    } catch (e: SecurityException) {
      Log.e(TAG, "SecurityException: ${e.message}")
    }
  }
}

@Composable
fun PermissionRequiredView(
  hasPermission: Boolean,
  isLocationEnabled: Boolean,
  onPermissionRequest: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenLocationSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      imageVector = Icons.Default.LocationOn,
      contentDescription = null,
      modifier = Modifier.size(64.dp),
      tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = if (!hasPermission) "位置情報の権限が必要です" else "位置情報をオンにしてください",
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = if (!hasPermission) "セル情報を取得するには、位置情報の権限が必要です。設定から許可してください。"
      else "セル情報を取得するには、位置情報サービスを有効にする必要があります。",
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))
    if (!hasPermission) {
      Button(
        onClick = onPermissionRequest,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("権限をリクエスト")
      }
      Spacer(modifier = Modifier.height(8.dp))
      TextButton(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("設定画面を開く")
      }
    } else if (!isLocationEnabled) {
      Button(
        onClick = onOpenLocationSettings,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("位置情報をオンにする")
      }
    }
  }
}

@Composable
fun CellularInfoList(
  displayContext: DisplayContext,
  modifier: Modifier = Modifier,
  neighborCellCount: Int,
  fcnConfig: FCN?
) {
  val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
  val lastUpdatedStr =
    if (displayContext.lastUpdated > 0) sdf.format(java.util.Date(displayContext.lastUpdated)) else "Never"

  LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    item {
      Text(
        text = "Last updated: $lastUpdatedStr",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    if (displayContext.cellularInfos.isEmpty()) {
      item {
        Text(text = "No Cellular Connection", modifier = Modifier.padding(vertical = 16.dp))
      }
    } else {
      item {
        Text(
          text = "Connected Cells",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
      }
      items(displayContext.cellularInfos) { info ->
        CellularInfoCard(info, fcnConfig, neighborCellCount)
      }
    }

    item {
      Spacer(modifier = Modifier.height(32.dp))
    }

    items(displayContext.carrierBands) { info ->
      CellularInfoCard(info, fcnConfig)
    }
  }
}

@Composable
fun CellularInfoCard(info: CellularInfo, fcnConfig: FCN?, neighborCellCount: Int = 0) {
  Card(
    modifier = Modifier
      .padding(vertical = 4.dp)
      .fillMaxSize()
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "${info.networkType} ${info.band ?: ""}",
          style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.weight(1f))
        if (info.isRegistered) {
          Text("Neighbor Cells: $neighborCellCount")
          Spacer(modifier = Modifier.width(8.dp))
          Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Text("Connected")
          }
        }
      }

      val detailInfo = when (info.networkType) {
        NetworkType.LTE -> CarrierUtils.getEarfcnDetails(fcnConfig, info.earfcn).second
        NetworkType.NR -> CarrierUtils.getNrfcnDetails(fcnConfig, info.nrarfcn).second
        else -> null
      }

      if (info.bandDetails != null || detailInfo != null) {
        val frequency = when (detailInfo) {
          is EarfcnChild -> "${detailInfo.frequency} MHz"
          is NrfcnChild -> "${detailInfo.frequency} MHz"
          else -> info.bandDetails?.frequency ?: "N/A"
        }
        Text(
          text = "Frequency: $frequency",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = info.bandDetails?.features ?: detailInfo?.let {
            when (it) {
              is EarfcnChild -> it.note ?: ""
              is NrfcnChild -> it.note ?: ""
              else -> ""
            }
          } ?: "",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary
        )
      }

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      Text(text = "Provider: ${info.providerName ?: "Unknown"}")
      Text(text = "Signal: ${info.dbm} dBm")

      if (info.networkType == NetworkType.LTE) {
        val bw = CarrierUtils.getBandWidth(fcnConfig, info.earfcn).second
        Text(text = "EARFCN: ${info.earfcn}" + (bw?.let { " / BW: %.1f MHz".format(it) } ?: ""))
      } else if (info.networkType == NetworkType.NR) {
        val bw = CarrierUtils.getNrBandWidth(fcnConfig, info.nrarfcn).second
        Text(text = "NR-ARFCN: ${info.nrarfcn}" + (bw?.let { " / BW: %.1f MHz".format(it) } ?: ""))
      }
    }
  }
}
