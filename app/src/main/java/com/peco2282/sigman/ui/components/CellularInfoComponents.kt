package com.peco2282.sigman.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.peco2282.sigman.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CellDetailDialog(info: CellularInfo, fcnConfig: FCN?, onDismiss: () -> Unit) {
  val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
  val collectedAtStr = if (info.collectedAt > 0) sdf.format(Date(info.collectedAt)) else "Unknown"

  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      shape = MaterialTheme.shapes.large
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .fillMaxWidth()
      ) {
        Text(
          text = "Network Details",
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
          modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
        ) {
          SectionTitle("General")
          DetailRow("Network Type", info.networkType.name)
          DetailRow("Provider", info.providerName ?: "Unknown")
          DetailRow("MCC", info.mcc ?: "N/A")
          DetailRow("MNC", info.mnc ?: "N/A")
          DetailRow("Connected", if (info.isRegistered) "Yes" else "No")
          DetailRow("Collected At", collectedAtStr)

          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

          SectionTitle("Service Status")
          info.serviceState?.let { DetailRow("Service State", it) }
          info.roaming?.let { DetailRow("Roaming", if (it) "Yes" else "No") }
          info.dataNetworkType?.let { DetailRow("Data Network Type", it) }
          info.isManualSelection?.let { DetailRow("Manual Selection", if (it) "Yes" else "No") }
          info.operatorAlphaLong?.let { DetailRow("Operator (Long)", it) }
          info.operatorAlphaShort?.let { DetailRow("Operator (Short)", it) }
          info.operatorNumeric?.let { DetailRow("Operator Numeric", it) }
          info.isEnDcAvailable?.let { DetailRow("5G NSA (EN-DC) Available", if (it) "Yes" else "No") }

          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

          SectionTitle("Radio Info")
          info.band?.let { DetailRow("Band", it) }
          info.bandwidth?.let { DetailRow("Bandwidth", "$it kHz") }
          info.pci?.let { DetailRow("PCI", it.toString()) }
          info.earfcn?.let { DetailRow("EARFCN", it.toString()) }
          info.nrarfcn?.let { DetailRow("NR-ARFCN", it.toString()) }
          info.arfcn?.let { DetailRow("ARFCN", it.toString()) }
          info.uarfcn?.let { DetailRow("UARFCN", it.toString()) }
          info.cid?.let { DetailRow("Cell ID (CID)", it.toString()) }
          info.tac?.let { DetailRow("TAC", it.toString()) }
          info.lac?.let { DetailRow("LAC", it.toString()) }
          info.psc?.let { DetailRow("PSC", it.toString()) }
          info.bsic?.let { DetailRow("BSIC", it.toString()) }

          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

          SectionTitle("Signal Strength")
          info.rsrp?.let { DetailRow("RSRP", "$it dBm") }
          info.rsrq?.let { DetailRow("RSRQ", "$it dB") }
          info.rssi?.let { DetailRow("RSSI", "$it dBm") }
          info.sinr?.let { DetailRow("SINR", "$it dB") }
          info.rssnr?.let { DetailRow("RSSNR", "$it (0.1 dB)") }
          info.cqi?.let { DetailRow("CQI", it.toString()) }
          info.ta?.let { DetailRow("Timing Advance", it.toString()) }
        }

        TextButton(
          onClick = onDismiss,
          modifier = Modifier
            .align(Alignment.End)
            .padding(top = 16.dp)
        ) {
          Text("Close")
        }
      }
    }
  }
}

@Composable
fun SectionTitle(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(vertical = 4.dp)
  )
}

@Composable
fun DetailRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.secondary
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
fun CellularInfoList(
  cellularInfos: List<CellularInfo>,
  carrierBands: List<CellularInfo>,
  lastUpdated: Long,
  modifier: Modifier = Modifier,
  neighborCellCount: Int,
  fcnConfig: FCN?
) {
  val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
  val lastUpdatedStr =
    if (lastUpdated > 0) sdf.format(Date(lastUpdated)) else "Never"

  var selectedInfo by remember { mutableStateOf<CellularInfo?>(null) }

  LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    item {
      Text(
        text = "Last updated: $lastUpdatedStr",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    if (cellularInfos.isEmpty()) {
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
      items(cellularInfos) { info ->
        CellularInfoCard(info, fcnConfig, neighborCellCount, onClick = { selectedInfo = info })
      }
    }

    if (carrierBands.isNotEmpty()) {
      item {
        Text(
          text = "Neighbor Cells",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
      }
      items(carrierBands) { info ->
        CellularInfoCard(info, fcnConfig, onClick = { selectedInfo = info })
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CellularInfoCard(
  info: CellularInfo,
  fcnConfig: FCN?,
  neighborCellCount: Int = 0,
  onClick: () -> Unit = {}
) {
  Card(
    modifier = Modifier
      .padding(vertical = 4.dp)
      .fillMaxWidth()
      .clickable { onClick() }
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
      if (info.pci != null) {
        InfoRow(
          label = "PCI",
          value = info.pci.toString(),
          modifier = Modifier.fillMaxWidth(0.5f)
        )
      }
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
      }

      if (info.isRegistered && neighborCellCount > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Nearby cells: $neighborCellCount",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline
        )
      }
    }
  }
}
