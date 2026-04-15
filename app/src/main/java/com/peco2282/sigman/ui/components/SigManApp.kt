package com.peco2282.sigman.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peco2282.sigman.DisplayContext
import com.peco2282.sigman.FCN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigManApp(
  displayState: DisplayContext,
  neighborCellCounts: Map<Int, Int>,
  fcnConfig: FCN?,
  onPermissionRequest: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenLocationSettings: () -> Unit,
  onAdbToggle: (Boolean, Long) -> Unit,
  onAdbPair: (String) -> Unit,
  adbIsConnected: Boolean
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
      Column(modifier = Modifier.padding(innerPadding)) {
        TabRow(selectedTabIndex = if (displayState.isAdbEnabled) 1 else 0) {
          Tab(
            selected = !displayState.isAdbEnabled,
            onClick = { onAdbToggle(false, 1000L) },
            text = { Text("Standard") }
          )
          Tab(
            selected = displayState.isAdbEnabled,
            onClick = { onAdbToggle(true, 1000L) },
            text = { Text("ADB (Raw)") }
          )
        }

        if (displayState.isAdbEnabled) {
          AdbSignalView(
            adbEnabled = true,
            onAdbToggle = onAdbToggle,
            signalData = displayState.adbSignalData,
            onPair = onAdbPair,
            isConnected = adbIsConnected
          )
        } else {
          val subs = displayState.subInfo
          if (subs.isEmpty()) {
            CellularInfoList(
              cellularInfos = displayState.cellularInfos,
              carrierBands = displayState.carrierBands,
              lastUpdated = displayState.lastUpdated,
              modifier = Modifier,
              neighborCellCount = neighborCellCounts.values.firstOrNull() ?: 0,
              fcnConfig = fcnConfig
            )
          } else {
            val pagerState = rememberPagerState(pageCount = { subs.size })
            val scope = rememberCoroutineScope()
            Column(modifier = Modifier.fillMaxSize()) {
              if (subs.size > 1) {
                ScrollableTabRow(
                  selectedTabIndex = pagerState.currentPage,
                  edgePadding = 16.dp,
                  containerColor = MaterialTheme.colorScheme.surface,
                  contentColor = MaterialTheme.colorScheme.primary,
                  divider = {}
                ) {
                  subs.forEachIndexed { index, sub ->
                    Tab(
                      selected = pagerState.currentPage == index,
                      onClick = {
                        scope.launch {
                          pagerState.animateScrollToPage(index)
                        }
                      },
                      text = {
                        Text(
                          text = sub.displayName.toString(),
                          style = MaterialTheme.typography.titleSmall
                        )
                      }
                    )
                  }
                }
              }
              HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = subs.size > 1
              ) { page ->
                val sub = subs[page]
                val subId = sub.subscriptionId
                CellularInfoList(
                  cellularInfos = displayState.perSubCellularInfos[subId] ?: emptyList(),
                  carrierBands = displayState.perSubCarrierBands[subId] ?: emptyList(),
                  lastUpdated = displayState.lastUpdated,
                  neighborCellCount = neighborCellCounts[subId] ?: 0,
                  fcnConfig = fcnConfig
                )
              }
            }
          }
        }
      }
    }

    if (showChangelog) {
      ChangelogDialog(onDismiss = { showChangelog = false })
    }
  }
}
