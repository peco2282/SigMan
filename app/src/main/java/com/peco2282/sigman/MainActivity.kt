package com.peco2282.sigman

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    if (result.resultCode == Activity.RESULT_OK) {
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
        var showMenu by remember { mutableStateOf(false) }
        var showChangelog by remember { mutableStateOf(false) }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          topBar = {
            TopAppBar(
              title = { Text("Cell Info") },
              actions = {
                IconButton(onClick = { showMenu = true }) {
                  Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                  expanded = showMenu,
                  onDismissRequest = { showMenu = false }
                ) {
                  DropdownMenuItem(
                    text = { Text("更新履歴") },
                    onClick = {
                      showMenu = false
                      showChangelog = true
                    },
                    leadingIcon = {
                      Icon(Icons.Default.Info, contentDescription = null)
                    }
                  )
                }
              }
            )
          }
        ) { innerPadding ->
          val context = displayState.value
          if (!context.hasLocationPermission || !context.hasPhoneStatePermission || !context.isLocationEnabled) {
            PermissionRequiredView(
              hasLocationPermission = context.hasLocationPermission,
              hasPhoneStatePermission = context.hasPhoneStatePermission,
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

          if (showChangelog) {
            ChangelogDialog(onDismiss = { showChangelog = false })
          }
        }
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

    val locationGranted = ContextCompat.checkSelfPermission(this, locationPermission) == PackageManager.PERMISSION_GRANTED
    val phoneStateGranted = ContextCompat.checkSelfPermission(this, phoneStatePermission) == PackageManager.PERMISSION_GRANTED

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
      Log.w(TAG, "Cannot start updating: permissions not granted (Location: $locationGranted, PhoneState: $phoneStateGranted)")
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
      val all = telephonyManager.allCellInfo ?: emptyList<CellInfo>()
      val infos = all.filter { it.isRegistered }.map { convertToCellularInfo(it, now) }

      val unregistered = all.filter { !it.isRegistered }.map { convertToCellularInfo(it, now) }

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
fun ChangelogDialog(onDismiss: () -> Unit) {
  val changelogs = listOf(
    "v1.3.2" to listOf("UIレイアウトを2列表示に変更"),
    "v1.3.1" to listOf("UI上のラベル幅を固定"),
    "v1.3" to listOf("更新履歴ダイアログとメニューオプションの追加", "セル情報への PCI (Physical Cell ID) 追加",),
//    "v1.2.2" to listOf("デプロイ用 GitHub Actions ワークフローの修正", "リファクタリング: セル情報変換メソッドの共通化"),
    "v1.2.1" to listOf("NRセル使用時のRSRQの未取得を修正"),
    "v1.2" to listOf("RSRQ および RSSI 信号強度メトリクスの追加"),
    "v1.1" to listOf("5G (NR) の情報表示に対応", "バンド詳細情報の拡充", "NR-ARFCN サポートの追加"),
    "v1.0" to listOf("初回リリース", "LTE情報の基本表示機能")
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("更新履歴") },
    text = {
      LazyColumn {
        items(changelogs) { (version, features) ->
          Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
              text = version,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary
            )
            features.forEach { feature ->
              Text(
                text = "・$feature",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("閉じる")
      }
    }
  )
}

@Composable
fun PermissionRequiredView(
  hasLocationPermission: Boolean,
  hasPhoneStatePermission: Boolean,
  isLocationEnabled: Boolean,
  onPermissionRequest: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenLocationSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val hasPermissions = hasLocationPermission && hasPhoneStatePermission
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
      text = if (!hasPermissions) "必要な権限がありません" else "位置情報をオンにしてください",
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    val message = when {
      !hasLocationPermission && !hasPhoneStatePermission -> "セル情報を取得するには、位置情報と電話の権限が必要です。"
      !hasLocationPermission -> "セル情報を取得するには、位置情報の権限が必要です。"
      !hasPhoneStatePermission -> "セル情報を取得するには、電話の状態の権限が必要です。"
      !isLocationEnabled -> "セル情報を取得するには、位置情報サービスを有効にする必要があります。"
      else -> ""
    }
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))
    if (!hasPermissions) {
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

    if (displayContext.carrierBands.isNotEmpty()) {
      item {
        Text(
          text = "Neighbor Cells",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
      }
      items(displayContext.carrierBands) { info ->
        CellularInfoCard(info, fcnConfig)
      }
    }

    item {
      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
  Row(modifier = modifier.padding(vertical = 2.dp)) {
    Text(
      text = label,
      modifier = Modifier.width(75.dp),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.secondary
    )
    Text(
      text = ": $value",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
fun CellularInfoCard(info: CellularInfo, fcnConfig: FCN?, neighborCellCount: Int = 0) {
  Card(
    modifier = Modifier
      .padding(vertical = 4.dp)
      .fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        val bandDisplay = if (info.band.isNullOrEmpty()) {
          when (info.networkType) {
            NetworkType.LTE -> CarrierUtils.getEarfcnDetails(fcnConfig, info.earfcn).first ?: ""
            NetworkType.NR -> CarrierUtils.getNrfcnDetails(fcnConfig, info.nrarfcn).first ?: ""
            else -> ""
          }
        } else {
          info.band
        }
        Text(
          text = "${info.networkType} $bandDisplay",
          style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.weight(1f))
        if (info.isRegistered) {
          Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Text(
              text = "Connected",
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
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
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 4.dp)
        )
        val note = info.bandDetails?.features ?: detailInfo?.let {
          when (it) {
            is EarfcnChild -> it.note ?: ""
            is NrfcnChild -> it.note ?: ""
            else -> ""
          }
        } ?: ""
        if (note.isNotEmpty()) {
          Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
          )
        }
      }

      HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

      InfoRow(label = "Provider", value = info.providerName ?: "Unknown")

      @OptIn(ExperimentalLayoutApi::class)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        info.rsrp?.let {
            InfoRow(
              label = "RSRP",
              value = "$it dBm",
              modifier = Modifier.fillMaxWidth(0.5f)
            )
          }
          info.rsrq?.let {
            InfoRow(
              label = "RSRQ",
              value = "$it dB",
              modifier = Modifier.fillMaxWidth(0.5f)
            )
          }
          info.rssi?.let {
            InfoRow(
              label = "RSSI",
              value = "$it dBm",
              modifier = Modifier.fillMaxWidth(0.5f)
            )
          }
          info.sinr?.let {
            InfoRow(
              label = "SINR",
              value = "$it dB",
              modifier = Modifier.fillMaxWidth(0.5f)
            )
          }

          if (info.networkType == NetworkType.LTE) {
            InfoRow(
              label = "EARFCN",
              value = info.earfcn.toString(),
              modifier = Modifier.fillMaxWidth(0.5f)
            )
            val bw = CarrierUtils.getBandWidth(fcnConfig, info.earfcn).second
            bw?.let {
              InfoRow(
                label = "Bandwidth",
                value = "%.1f MHz".format(it),
                modifier = Modifier.fillMaxWidth(0.5f)
              )
            }
          } else if (info.networkType == NetworkType.NR) {
            InfoRow(
              label = "NR-ARFCN",
              value = info.nrarfcn.toString(),
              modifier = Modifier.fillMaxWidth(0.5f)
            )
            val bw = CarrierUtils.getNrBandWidth(fcnConfig, info.nrarfcn).second
            bw?.let {
              InfoRow(
                label = "Bandwidth",
                value = "%.1f MHz".format(it),
                modifier = Modifier.fillMaxWidth(0.5f)
              )
            }
          }

          if (info.pci != null) {
            InfoRow(
              label = "PCI",
              value = info.pci.toString(),
              modifier = Modifier.fillMaxWidth(0.5f)
            )
          }
        }

      if (info.isRegistered && neighborCellCount > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Neighbor Cells: $neighborCellCount",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.outline
        )
      }
    }
  }
}
