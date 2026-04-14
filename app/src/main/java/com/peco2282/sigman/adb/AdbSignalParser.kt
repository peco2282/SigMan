package com.peco2282.sigman.adb

data class AdbSignalData(
  val rsrp: Int? = null,
  val rsrq: Int? = null,
  val rssnr: Int? = null,
  val pci: Int? = null,
  val nrState: String? = null,
  val networkType: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)

class AdbSignalParser {
  // dumpsys telephony.registry の出力を解析するための正規表現 (例)
  // 端末やOSバージョンによって形式が異なる可能性があるため、複数のパターンに対応する必要がある
  private val rsrpRegex = Regex("""mRsrp=(-?\d+)""")
  private val rsrqRegex = Regex("""mRsrq=(-?\d+)""")
  private val rssnrRegex = Regex("""mRssnr=(-?\d+)""")
  private val pciRegex = Regex("""mPci=(\d+)""")
  private val nrStateRegex = Regex("""nrState=([A-Z_]+)""")
  private val dataNetworkTypeRegex = Regex("""mDataNetworkType=(\d+)""")

  // 5G (NR) 用の追加パターン
  private val ssRsrpRegex = Regex("""ssRsrp=(-?\d+)""")
  private val ssRsrqRegex = Regex("""ssRsrq=(-?\d+)""")
  private val ssSinrRegex = Regex("""ssSinr=(-?\d+)""")

  fun parseTelephonyRegistry(output: String): AdbSignalData {
    // 全体の文字列から各値を抽出
    val rsrp =
      rsrpRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: ssRsrpRegex.find(output)?.groupValues?.get(1)
        ?.toIntOrNull()
    val rsrq =
      rsrqRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: ssRsrqRegex.find(output)?.groupValues?.get(1)
        ?.toIntOrNull()
    val rssnr =
      rssnrRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: ssSinrRegex.find(output)?.groupValues?.get(1)
        ?.toIntOrNull()
    val pci = pciRegex.find(output)?.groupValues?.get(1)?.toIntOrNull()
    val nrState = nrStateRegex.find(output)?.groupValues?.get(1)

    return AdbSignalData(
      rsrp = if (rsrp == 2147483647) null else rsrp, // 無効値をフィルタ
      rsrq = if (rsrq == 2147483647) null else rsrq,
      rssnr = if (rssnr == 2147483647) null else rssnr,
      pci = if (pci == 2147483647) null else pci,
      nrState = nrState,
      networkType = null
    )
  }

  // dumpsys connectivity などから4G/5G情報を補完する
  fun parseConnectivity(output: String): String? {
    // 簡易的な実装
    return if (output.contains("MOBILE[5G]")) "5G" else if (output.contains("MOBILE[LTE]")) "LTE" else null
  }
}
