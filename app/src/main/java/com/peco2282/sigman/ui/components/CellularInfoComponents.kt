package com.peco2282.sigman.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peco2282.sigman.*

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

@OptIn(ExperimentalLayoutApi::class)
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
