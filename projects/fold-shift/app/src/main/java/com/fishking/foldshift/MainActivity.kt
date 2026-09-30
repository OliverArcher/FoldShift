package com.fishking.foldshift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fishking.foldshift.data.ConfigRepository
import com.fishking.foldshift.home.DefaultHome
import com.fishking.foldshift.service.SwitchForegroundService
import com.fishking.foldshift.ui.SettingsScreen
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val requestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = ConfigRepository(applicationContext)
        val defaultHome = DefaultHome(applicationContext)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    AppRoot(
                        repository = repository,
                        defaultHome = defaultHome,
                        onRequestShizuku = { Shizuku.requestPermission(requestCode) },
                        onSetEnabled = { value ->
                            if (value) {
                                SwitchForegroundService.start(applicationContext)
                            } else {
                                SwitchForegroundService.stop(applicationContext)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRoot(
    repository: ConfigRepository,
    defaultHome: DefaultHome,
    onRequestShizuku: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()

    val inner by repository.innerPackage.collectAsState(initial = DEFAULT_INNER)
    val outer by repository.outerPackage.collectAsState(initial = DEFAULT_OUTER)
    val enabled by repository.enabled.collectAsState(initial = false)
    val batteryAck by repository.batteryPromptAcknowledged.collectAsState(initial = false)
    val serviceState by SwitchForegroundService.currentState.collectAsState()
    val lastOutcome by SwitchForegroundService.lastOutcome.collectAsState()
    val shizukuGranted by SwitchForegroundService.shizukuGranted.collectAsState()
    var launchers by remember { mutableStateOf(defaultHome.listLaunchers()) }

    LaunchedEffect(Unit) { launchers = defaultHome.listLaunchers() }

    SettingsScreen(
        innerPackage = inner,
        outerPackage = outer,
        enabled = enabled,
        batteryPromptAcknowledged = batteryAck,
        launchers = launchers,
        shizukuGranted = shizukuGranted,
        foldState = serviceState,
        lastOutcome = lastOutcome,
        onInnerPicked = { pkg -> scope.launch { repository.setInnerPackage(pkg) } },
        onOuterPicked = { pkg -> scope.launch { repository.setOuterPackage(pkg) } },
        onEnabledChanged = { value ->
            scope.launch {
                repository.setEnabled(value)
                onSetEnabled(value)
            }
        },
        onAcknowledgeBattery = { scope.launch { repository.acknowledgeBatteryPrompt() } },
        onRequestShizuku = onRequestShizuku,
    )
}

private const val DEFAULT_INNER: String = "com.sec.android.app.launcher"
private const val DEFAULT_OUTER: String = "bitpit.launcher"