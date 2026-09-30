package com.fishking.foldshift.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * UI block that tells the user whether FoldShift is exempt from battery
 * optimisations and lets them grant it. We poll the system status on a
 * short interval so the UI re-syncs after they return from Settings.
 *
 * Notifications are intentionally *not* requested — the foreground
 * service keeps the process alive while switching is enabled, and on
 * Samsung devices doze is the primary aggressor to defeat.
 */
@Composable
fun BatteryPromptCard(
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ignored by remember { mutableStateOf(isIgnoringBatteryOptimisations(context)) }

    // Re-poll when returning from Settings.
    LaunchedEffect(Unit) {
        while (true) {
            delay(750)
            ignored = isIgnoringBatteryOptimisations(context)
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "电池白名单",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (ignored) {
                    "已授权，FoldShift 在折叠发生时不会被系统暂停。"
                } else {
                    "FoldShift 需要在折叠时立刻切换默认桌面；" +
                        "请在系统电池优化中将 FoldShift 设为「不优化」。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!ignored) {
                OutlinedButton(onClick = { openBatteryOptimisationSettings(context) }) {
                    Text("前往电池优化设置")
                }
                TextButton(onClick = onAcknowledge) {
                    Text("稍后再说")
                }
            }
        }
    }
}

private fun isIgnoringBatteryOptimisations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBatteryOptimisationSettings(context: Context) {
    val packageName = context.packageName
    val intents = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        },
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // try the next candidate
        } catch (_: SecurityException) {
            // Some OEM ROMs reject the data: URI form.
        }
    }
    Toast.makeText(context, "未找到电池优化设置入口，请手动前往", Toast.LENGTH_LONG).show()
}