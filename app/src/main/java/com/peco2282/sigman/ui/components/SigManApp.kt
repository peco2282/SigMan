package com.peco2282.sigman.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.peco2282.sigman.DisplayContext
import com.peco2282.sigman.FCN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigManApp(
  displayState: DisplayContext,
  neighborCellCount: Int,
  fcnConfig: FCN?,
  onPermissionRequest: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenLocationSettings: () -> Unit
) {
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
    if (!displayState.hasLocationPermission || !displayState.hasPhoneStatePermission || !displayState.isLocationEnabled) {
      PermissionRequiredView(
        hasLocationPermission = displayState.hasLocationPermission,
        hasPhoneStatePermission = displayState.hasPhoneStatePermission,
        isLocationEnabled = displayState.isLocationEnabled,
        onPermissionRequest = onPermissionRequest,
        onOpenSettings = onOpenSettings,
        onOpenLocationSettings = onOpenLocationSettings,
        modifier = Modifier.padding(innerPadding)
      )
    } else {
      CellularInfoList(
        displayContext = displayState,
        modifier = Modifier.padding(innerPadding),
        neighborCellCount = neighborCellCount,
        fcnConfig = fcnConfig
      )
    }

    if (showChangelog) {
      ChangelogDialog(onDismiss = { showChangelog = false })
    }
  }
}
