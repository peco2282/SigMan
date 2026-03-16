package com.peco2282.sigman

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

enum class NetworkType { LTE, NR, WCDMA, GSM, UNKNOWN, DISCONNECT }

@Serializable
data class BandInfo(
  val band: String,
  val frequency: String? = null,
  val bandwidth: String? = null,
  val features: String? = null,
  val type: String? = null // 5G用
)

@Serializable
data class CarrierBands(
  val docomo: List<BandInfo> = emptyList(),
  val au: List<BandInfo> = emptyList(),
  val softbank: List<BandInfo> = emptyList(),
  val rakuten: List<BandInfo> = emptyList()
) {
  fun getBandsByCarrier(carrier: String): List<BandInfo> {
    return when (carrier.lowercase()) {
      "docomo" -> docomo
      "au" -> au
      "softbank" -> softbank
      "rakuten" -> rakuten
      else -> emptyList()
    }
  }
}

@Serializable
data class BandsConfig(
  @SerialName("updated_at")
  val updatedAt: String,
  val version: Int,
  val carriers: Map<String, CarrierBands>
)

data class CellularInfo(
  val networkType: NetworkType = NetworkType.DISCONNECT,
  val providerName: String? = null,
  val mcc: String? = null,
  val mnc: String? = null,
  val band: String? = null,
  val bandwidth: Int? = null, // kHz (LTE)
  val rsrp: Int? = null,
  val rsrq: Int? = null,
  val rssi: Int? = null,
  val sinr: Int? = null,
  val rssnr: Int? = null, // LTE 用 (0.1 dB 単位が多いが、Int)
  val cqi: Int? = null,
  val ta: Int? = null, // Timing Advance
  val earfcn: Int? = null,
  val nrarfcn: Int? = null,
  val arfcn: Int? = null, // GSM
  val uarfcn: Int? = null, // WCDMA
  val pci: Int? = null,
  val cid: Long? = null, // Cell Identity (LTE: CI, NR: NCI, GSM: CID, WCDMA: CID)
  val lac: Int? = null, // GSM/WCDMA
  val tac: Int? = null, // LTE/NR (Tracking Area Code)
  val bsic: Int? = null, // GSM
  val psc: Int? = null, // WCDMA
  val isRegistered: Boolean = false,
  val bandDetails: BandInfo? = null, // bands.jsonからの詳細情報
  val timestampNs: Long = 0L, // CellInfo.timestamp から取得されるナノ秒
  val collectedAt: Long = 0L, // アプリ側で取得した時刻 (System.currentTimeMillis())

  // --- 追加情報 (TelephonyCallback等から) ---
  val serviceState: String? = null, // IN_SERVICE, OUT_OF_SERVICE等
  val roaming: Boolean? = null,
  val dataNetworkType: String? = null, // LTE, NR, etc.
  val isManualSelection: Boolean? = null,
  val operatorAlphaLong: String? = null,
  val operatorAlphaShort: String? = null,
  val operatorNumeric: String? = null,
  val isEnDcAvailable: Boolean? = null, // 5G NSA可用性
  val isNrAvailable: Boolean? = null,   // 5G可用性
  val isDcNrRestricted: Boolean? = null // 5G制限
)

data class DisplayContext(
  val cellularInfos: List<CellularInfo> = emptyList(),
  val carrierBands: List<CellularInfo> = emptyList(),
  val lastUpdated: Long = 0L,
  val hasLocationPermission: Boolean = true,
  val hasPhoneStatePermission: Boolean = true,
  val isLocationEnabled: Boolean = true
)

@Serializable
data class FCNRoot(
  val earfcn: Map<String, JsonElement>
)

data class FCN(
  val earfcn: Map<String, List<EarfcnChild>>,
  val nrarfcn: Map<String, List<NrfcnChild>>
)

@Serializable
data class EarfcnChild(
  val earfcn: Int,
  val provider: String,
  val frequency: Double,
  @SerialName("BW")
  val bw: Double,
  val note: String? = null
)

@Serializable
data class NrfcnChild(
  val nrarfcn: Int,
  val provider: String,
  val frequency: Double,
  @SerialName("BW")
  val bw: Double,
  val note: String? = null
)

object CarrierUtils {
  fun getCarrierName(mcc: String?, mnc: String?): String? {
    if (mcc != "440" && mcc != "441") return null
    return when (mnc) {
      "10", "01" -> "docomo"
      "50", "51", "52", "53", "54" -> "au"
      "20", "80", "81" -> "softbank"
      "11" -> "rakuten"
      else -> null
    }
  }

  fun getEarfcnDetails(fcn: FCN?, earfcn: Int?): Pair<String?, EarfcnChild?> {
    if (fcn == null || earfcn == null) return null to null
    fcn.earfcn.forEach { (band, children) ->
      children.forEach { child ->
        if (child.earfcn == earfcn) {
          return band to child
        }
      }
    }
    return null to null
  }

  fun getNrfcnDetails(fcn: FCN?, nrarfcn: Int?): Pair<String?, NrfcnChild?> {
    if (fcn == null || nrarfcn == null) return null to null
    fcn.nrarfcn.forEach { (band, children) ->
      children.forEach { child ->
        if (child.nrarfcn == nrarfcn) {
          return band to child
        }
      }
    }
    return null to null
  }

  fun getBandWidth(fcn: FCN?, earfcn: Int?): Pair<String?, Double?> {
    val (band, child) = getEarfcnDetails(fcn, earfcn)
    return band to child?.bw
  }

  fun getNrBandWidth(fcn: FCN?, nrarfcn: Int?): Pair<String?, Double?> {
    val (band, child) = getNrfcnDetails(fcn, nrarfcn)
    return band to child?.bw
  }
}

object AssetsLoader {
  private val json = Json {
    ignoreUnknownKeys = true
  }

  fun loadBands(context: Context): BandsConfig? {
    val assetManager = context.assets
    val files = assetManager.list("") // ルートのリストを取得
    files?.forEach { fileName ->
      Log.d("AssetCheck", "File found: $fileName")
    }
    return try {
      val jsonString = context.assets.open("bands.json").bufferedReader().use { it.readText() }
      json.decodeFromString<BandsConfig>(jsonString)
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  fun loadFcn(context: Context): FCN? {
    return try {
      val jsonString = context.assets.open("fcn.json").bufferedReader().use { it.readText() }
      val root = json.decodeFromString<Map<String, Map<String, JsonElement>>>(jsonString)
      val earfcnContent = root["earfcn"] ?: return null
      val nrarfcnContent = root["nrarfcn"] ?: return null

      val earfcn = earfcnContent.filterKeys { !it.startsWith("_") }
        .mapValues { entry ->
          json.decodeFromJsonElement<List<EarfcnChild>>(entry.value)
        }
      val nrarfcn = nrarfcnContent.filterKeys { !it.startsWith("_") }
        .mapValues { entry ->
          json.decodeFromJsonElement<List<NrfcnChild>>(entry.value)
        }
      FCN(earfcn, nrarfcn)
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }
}
