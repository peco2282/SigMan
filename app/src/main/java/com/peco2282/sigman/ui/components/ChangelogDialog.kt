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

private val CHANGELOGS = listOf(
  "v1.4" to listOf("デュアルSIMを使用している場合、すべてのSIM情報を見れるよう修正"),
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

@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("更新履歴") },
    text = {
      LazyColumn {
        items(CHANGELOGS) { (version, features) ->
          ChangelogSection(version, features)
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
private fun ChangelogSection(version: String, changes: List<String>) {
  val sectionPadding = 8.dp
  val bulletIndent = 8.dp
  val bulletPrefix = "・"

  Column(modifier = Modifier.padding(vertical = sectionPadding)) {
    Text(
      text = version,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary
    )
    changes.forEach { change ->
      Text(
        text = "$bulletPrefix$change",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = bulletIndent)
      )
    }
  }
}