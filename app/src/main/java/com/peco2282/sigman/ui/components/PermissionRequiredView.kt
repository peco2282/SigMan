package com.peco2282.sigman.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
