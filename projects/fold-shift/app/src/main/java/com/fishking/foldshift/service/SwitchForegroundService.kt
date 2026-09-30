package com.fishking.foldshift.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.fishking.foldshift.control.FoldController
import com.fishking.foldshift.data.ConfigRepository
import com.fishking.foldshift.device.FoldStateDetector
import com.fishking.foldshift.device.ForegroundAppInspector
import com.fishking.foldshift.home.DefaultHome
import com.fishking.foldshift.home.ShizukuShellExecutor
import com.fishking.foldshift.model.FoldState
import com.fishking.foldshift.model.SwitchOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

/**
 * Watches the fold state and applies the configured default Home.
 *
 * The service runs in the foreground so the system keeps our process
 * alive long enough to observe display changes. A foreground service
 * requires an ongoing notification, so we publish a single silent,
 * minimum-importance entry.
 *
 * The service also tracks the Shizuku / Stellar permission state and
 * publishes it through [shizukuGranted] so the UI can show a calm
 * "已授权" badge instead of a misleading "尚未运行" caption.
 */
class SwitchForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private lateinit var detector: FoldStateDetector
    private lateinit var controller: FoldController
    private lateinit var repository: ConfigRepository
    private lateinit var shell: ShizukuShellExecutor

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
            Log.i("FoldShift", "Shizuku permission result: granted=${shizukuGranted.value}")
        }

    private val binderListener =
        Shizuku.OnBinderReceivedListener {
            refreshShizukuGranted()
            Log.i("FoldShift", "Shizuku binder received; granted=${shizukuGranted.value}")
        }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            shizukuGranted.value = false
            Log.w("FoldShift", "Shizuku binder died")
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        repository = ConfigRepository(applicationContext)
        shell = ShizukuShellExecutor()
        controller = FoldController(
            home = DefaultHome(applicationContext),
            shell = shell,
            inspector = ForegroundAppInspector(shell),
        )
        detector = FoldStateDetector(applicationContext) { state ->
            onFoldStateChanged(state)
        }
        refreshShizukuGranted()
        detector.start()
        Log.i("FoldShift", "Switch service started; shizukuGranted=${shizukuGranted.value}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        detector.stop()
        scope.cancel()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Log.i("FoldShift", "Switch service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshShizukuGranted() {
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            Log.w("FoldShift", "Shizuku permission check failed", t)
            false
        }
        shizukuGranted.value = granted
    }

    private fun onFoldStateChanged(state: FoldState) {
        currentState.value = state
        scope.launch {
            // Debounce: display events can arrive in bursts while the
            // panels hand off. Wait briefly, then re-read the state so we
            // converge on the final one instead of acting on a transient.
            delay(DEBOUNCE_MS)
            val settled = detector.currentState()
            currentState.value = settled

            mutex.withLock {
                val enabled = repository.enabled.first()
                if (!enabled) {
                    Log.i("FoldShift", "Service active but feature disabled; skipping $settled")
                    return@withLock
                }
                if (!shizukuGranted.value) {
                    refreshShizukuGranted()
                    if (!shizukuGranted.value) {
                        Log.w("FoldShift", "Skipping $settled: Shizuku not granted yet")
                        lastOutcome.value = null
                        return@withLock
                    }
                }
                val inner = repository.innerPackage.first()
                val outer = repository.outerPackage.first()
                val outcome = controller.handle(settled, inner, outer)
                lastOutcome.value = outcome
                Log.i("FoldShift", "Fold event $settled -> $outcome")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FoldShift 运行状态",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "FoldShift 折叠切换服务运行中"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FoldShift")
            .setContentText("折叠切换服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "foldshift_state"
        private const val NOTIFICATION_ID = 4101
        private const val DEBOUNCE_MS = 80L

        /** Last fold state observed; useful for the settings UI. */
        val currentState: MutableStateFlow<FoldState> = MutableStateFlow(FoldState.UNKNOWN)

        /** Last decision made; useful for debugging. */
        val lastOutcome: MutableStateFlow<SwitchOutcome?> = MutableStateFlow(null)

        /** Whether Shizuku / Stellar has granted FoldShift the API permission. */
        val shizukuGranted: MutableStateFlow<Boolean> = MutableStateFlow(false)

        fun start(context: Context) {
            val intent = Intent(context, SwitchForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SwitchForegroundService::class.java))
        }
    }
}