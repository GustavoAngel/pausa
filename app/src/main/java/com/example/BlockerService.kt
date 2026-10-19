package com.example

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import java.util.TreeMap
import kotlinx.coroutines.*

class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun start() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class BlockerService : Service() {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + job)

    private var overlayView: ComposeView? = null
    private var serviceLifecycleOwner: ServiceLifecycleOwner? = null

    private var monitorJob: Job? = null
    private var countdownJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "pausa_blocker_channel_id"
        private const val NOTIFICATION_ID = 2468
        
        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_REFRESH = "com.example.action.REFRESH"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_REFRESH
        when (action) {
            ACTION_START -> {
                startMonitoring()
            }
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
            ACTION_REFRESH -> {
                if (PausaPrefs.isPausaActive(this)) {
                    startMonitoring()
                } else {
                    stopMonitoring()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Servicio Focus")
            .setContentText("Modo de desconexión social activo")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Control Focus"
            val descriptionText = "Monitorear e interceptar acceso a redes sociales"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startMonitoring() {
        // Clear previous tasks if any
        monitorJob?.cancel()
        countdownJob?.cancel()

        // 1. Polling check for the foreground app name
        monitorJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                val isActivePause = PausaPrefs.isPausaActive(this@BlockerService)
                if (isActivePause) {
                    val foregroundPackage = getForegroundPackage()
                    val blockedPackages = PausaPrefs.getBlockedPackages(this@BlockerService)

                    withContext(Dispatchers.Main) {
                        if (foregroundPackage != null && blockedPackages.contains(foregroundPackage)) {
                            // Social media detected in foreground
                            val appName = getAppName(foregroundPackage)
                            showOverlay(appName)
                        } else {
                            // Safe app or home screen
                            removeOverlay()
                        }
                    }
                } else {
                    // Pause expired, exit
                    withContext(Dispatchers.Main) {
                        removeOverlay()
                        stopMonitoring()
                        stopSelf()
                    }
                    break
                }
                delay(500) // Poll every 500ms
            }
        }

        // 2. Countdown timer updater
        countdownJob = serviceScope.launch(Dispatchers.Main) {
            while (isActive) {
                PausaPrefs.updateState(this@BlockerService)
                if (!PausaPrefs.isPausaActive(this@BlockerService)) {
                    stopMonitoring()
                    stopSelf()
                    break
                }
                delay(1000)
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        countdownJob?.cancel()
        removeOverlay()
        PausaPrefs.updateState(this)
    }

    private fun getForegroundPackage(): String? {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 10 * 1000,
                time
            )
            if (!stats.isNullOrEmpty()) {
                val sortedMap = TreeMap<Long, android.app.usage.UsageStats>()
                for (usageStat in stats) {
                    sortedMap[usageStat.lastTimeUsed] = usageStat
                }
                if (!sortedMap.isEmpty()) {
                    return sortedMap.lastKey()?.let { sortedMap[it]?.packageName }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun showOverlay(appName: String) {
        if (overlayView != null) {
            return // Already showing
        }

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Define overlay flags
        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        val lifecycleOwner = ServiceLifecycleOwner()
        lifecycleOwner.start()
        this.serviceLifecycleOwner = lifecycleOwner

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                OverlayContent(
                    blockedAppName = appName,
                    onGoToHome = {
                        goToHomeScreen()
                    }
                )
            }
        }

        try {
            wm.addView(composeView, layoutParams)
            overlayView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.removeView(view)
            serviceLifecycleOwner?.stop()
            serviceLifecycleOwner = null
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
        }
    }

    private fun goToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.facebook.katana", "com.facebook.lite" -> "Facebook"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.twitter.android" -> "X (Twitter)"
            "com.reddit.frontpage" -> "Reddit"
            "com.snapchat.android" -> "Snapchat"
            "com.google.android.youtube" -> "YouTube"
            "com.pinterest" -> "Pinterest"
            else -> "Red Social"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        job.cancel()
    }
}

@Composable
fun OverlayContent(
    blockedAppName: String,
    onGoToHome: () -> Unit
) {
    val remainingSecs by PausaPrefs.remainingSecondsFlow.collectAsState()
    
    val minutes = remainingSecs / 60
    val seconds = remainingSecs % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)

    // Cosmic Dark elegant gradient background
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Slate 900
            Color(0xFF1E293B)  // Slate 800
        )
    )

    // Pulse animation for the lock icon glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            // Consume clicks to avoid passing touches to underlying social media apps
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            // Pulsing Focus logo header
            Box(
                modifier = Modifier
                    .size(96.dp * pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFF25D366).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.ic_focus_logo_square),
                    contentDescription = "Logo Focus",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Pausa Digital",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle indicating which app is blocked
            Text(
                text = "Interrumpimos tu acceso a $blockedAppName para regalarte paz.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF59E0B),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Card centered containing countdown and quote
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tiempo restante de pausa",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8), // Slate 400
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = timerText,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "\"Respira hondo. El mundo real te ofrece maravillas que ninguna pantalla puede replicar.\"",
                        fontSize = 14.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = Color(0xFFCBD5E1), // Slate 300
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Premium home floating button
            Button(
                onClick = onGoToHome,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Ir a Inicio",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Regresar al Inicio de Android",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Desconéctate para reconectar",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B), // Slate 500
                textAlign = TextAlign.Center
            )
        }
    }
}
