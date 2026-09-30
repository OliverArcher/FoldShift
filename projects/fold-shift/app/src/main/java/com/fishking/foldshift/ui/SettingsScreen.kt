package com.fishking.foldshift.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fishking.foldshift.home.LauncherCandidate
import com.fishking.foldshift.model.FoldState
import com.fishking.foldshift.model.SwitchOutcome

/**
 * Phase 2 Settings screen. Lets the user pick inner / outer launchers
 * from the list reported by the system, toggle the controller on or off,
 * and jump into the battery-optimisation flow.
 *
 * State is hoisted into the caller via callbacks so we can wire it to the
 * ConfigRepository later without rewriting this composable.
 */
@Composable
fun SettingsScreen(
    innerPackage: String,
    outerPackage: String,
    enabled: Boolean,
    batteryPromptAcknowledged: Boolean,
    launchers: List<LauncherCandidate>,
    shizukuGranted: Boolean,
    foldState: FoldState,
    lastOutcome: SwitchOutcome?,
    onInnerPicked: (String) -> Unit,
    onOuterPicked: (String) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onAcknowledgeBattery: () -> Unit,
    onRequestShizuku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var launcherPickerFor by remember { mutableStateOf<LauncherKind?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { EnableCard(enabled = enabled, onEnabledChanged = onEnabledChanged) }
        item { StatusCard(foldState = foldState, lastOutcome = lastOutcome) }
        item {
            LauncherSlotCard(
                title = "内屏桌面",
                subtitle = "展开状态使用",
                currentPackage = innerPackage,
                launchers = launchers,
                onEdit = { launcherPickerFor = LauncherKind.Inner },
            )
        }
        item {
            LauncherSlotCard(
                title = "外屏桌面",
                subtitle = "合盖状态使用",
                currentPackage = outerPackage,
                launchers = launchers,
                onEdit = { launcherPickerFor = LauncherKind.Outer },
            )
        }
        item {
            ShizukuCard(
                granted = shizukuGranted,
                onRequest = onRequestShizuku,
            )
        }
        if (!batteryPromptAcknowledged || enabled) {
            item {
                BatteryPromptCard(onAcknowledge = onAcknowledgeBattery)
            }
        }
    }

    launcherPickerFor?.let { kind ->
        val onPick: (String) -> Unit = { pkg ->
            when (kind) {
                LauncherKind.Inner -> onInnerPicked(pkg)
                LauncherKind.Outer -> onOuterPicked(pkg)
            }
            launcherPickerFor = null
        }
        LauncherPickerDialog(
            title = when (kind) {
                LauncherKind.Inner -> "选择内屏桌面"
                LauncherKind.Outer -> "选择外屏桌面"
            },
            currentPackage = when (kind) {
                LauncherKind.Inner -> innerPackage
                LauncherKind.Outer -> outerPackage
            },
            launchers = launchers,
            onPick = onPick,
            onDismiss = { launcherPickerFor = null },
        )
    }
}

private enum class LauncherKind { Inner, Outer }

@Composable
private fun ShizukuCard(
    granted: Boolean,
    onRequest: () -> Unit,
) {
    val containerColor = if (granted) {
        // Material You primary container (low-saturation violet) instead
        // of the default tertiary container, which leans green. Still
        // distinct from surfaceVariant so "已授权" reads as positive,
        // but doesn't shout.
        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Shizuku / Stellar", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (granted) "已授权" else "未授权 — 切换设备前请先授权",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!granted) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("申请 Shizuku 权限")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    foldState: FoldState,
    lastOutcome: SwitchOutcome?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "当前状态", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when (foldState) {
                    FoldState.OPENED -> "展开（内屏）"
                    FoldState.CLOSED -> "合盖（外屏）"
                    FoldState.UNKNOWN -> "未知"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = when (lastOutcome) {
                    null -> "尚未执行切换"
                    is SwitchOutcome.NoOp -> "无需切换"
                    is SwitchOutcome.Switched -> "已切换默认桌面 → ${lastOutcome.target}"
                    is SwitchOutcome.SwitchedAndBroughtForward ->
                        "已切换并切前台 → ${lastOutcome.target}"
                    is SwitchOutcome.Failed ->
                        "切换失败 → ${lastOutcome.target}：${lastOutcome.reason}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EnableCard(enabled: Boolean, onEnabledChanged: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = "启用切换", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (enabled) {
                        "FoldShift 将根据折叠状态自动切换默认桌面。"
                    } else {
                        "关闭后保留当前默认桌面，不会自动切换。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChanged)
        }
    }
}

@Composable
private fun LauncherSlotCard(
    title: String,
    subtitle: String,
    currentPackage: String,
    launchers: List<LauncherCandidate>,
    onEdit: () -> Unit,
) {
    val currentLabel = launchers.firstOrNull { it.packageName == currentPackage }?.label
        ?: currentPackage
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun LauncherPickerDialog(
    title: String,
    currentPackage: String,
    launchers: List<LauncherCandidate>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(launchers, key = { it.packageName }) { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(candidate.packageName) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = candidate.packageName == currentPackage,
                            onClick = { onPick(candidate.packageName) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(text = candidate.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = candidate.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}