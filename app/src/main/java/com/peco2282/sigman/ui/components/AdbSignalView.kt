package com.peco2282.sigman.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peco2282.sigman.adb.AdbSignalData

@Composable
fun AdbSignalView(
    adbEnabled: Boolean,
    onAdbToggle: (Boolean, Long) -> Unit,
    signalData: AdbSignalData?,
    onPair: (String) -> Unit,
    isConnected: Boolean
) {
    var pairingCode by remember { mutableStateOf("") }
    var pollingInterval by remember { mutableStateOf("1000") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("ADB計測モード", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = adbEnabled,
                onCheckedChange = { onAdbToggle(it, pollingInterval.toLongOrNull() ?: 1000L) }
            )
        }

        if (adbEnabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pollingInterval,
                onValueChange = { if (it.all { char -> char.isDigit() }) pollingInterval = it },
                label = { Text("ポーリング周期 (ms)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Divider(Modifier.padding(vertical = 8.dp))

        if (adbEnabled) {
            if (!isConnected) {
                Text("ADB未接続", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it },
                    label = { Text("ペアリングコード") },
                    placeholder = { Text("123456") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                Button(
                    onClick = { onPair(pairingCode) },
                    modifier = Modifier.padding(top = 8.dp),
                    enabled = pairingCode.length >= 6
                ) {
                    Text("ペアリング実行")
                }
            } else {
                Text("ADB接続済み", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                
                SignalItem("RSRP", "${signalData?.rsrp ?: "N/A"} dBm")
                SignalItem("RSRQ", "${signalData?.rsrq ?: "N/A"} dB")
                SignalItem("SNR", "${signalData?.rssnr ?: "N/A"} dB")
                SignalItem("PCI", "${signalData?.pci ?: "N/A"}")
                SignalItem("NR State", signalData?.nrState ?: "Unknown")
                SignalItem("Network", signalData?.networkType ?: "Unknown")
            }
        } else {
            Text("TelephonyManagerを使用した標準計測モードです。")
        }

        if (adbEnabled) {
            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "ヒント: ワイヤレスデバッグの接続にはWi-FiがON、またはテザリングがONである必要があります。外部のWi-Fiネットワークに接続していなくても、テザリング(ホットスポット)を有効にすれば動作します。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun SignalItem(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(100.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
