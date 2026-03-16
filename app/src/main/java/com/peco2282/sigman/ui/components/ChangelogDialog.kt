package com.peco2282.sigman.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
  val changelogs = listOf(
    "v1.3.4" to listOf("セル情報の更新性を向上", "Neighbor Cellの更新タイミングを改善"),
    "v1.3.3" to listOf("PCI de 表示位置を修正"),
    "v1.3.2" to listOf("UIレイアウトを2列表示に変更"),
    "v1.3.1" to listOf("UI上のラベル幅を固定"),
    "v1.3" to listOf("更新履歴ダイアログとメニューオプションの追加", "セル情報への PCI (Physical Cell ID) 追加"),
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
