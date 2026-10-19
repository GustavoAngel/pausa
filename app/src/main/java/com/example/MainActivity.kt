package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.UsageStatsScreen
import com.example.ui.WelcomeScreen
import com.example.InstalledAppsHelper
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.testTag

enum class MainScreenTab {
    FOCUS,
    STATS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PausaPrefs.init(this)
        
        // Refresh the monitoring service state on launching only if active
        if (PausaPrefs.isPausaActive(this)) {
            val refreshIntent = Intent(this, BlockerService::class.java).apply {
                action = BlockerService.ACTION_REFRESH
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(refreshIntent)
                } else {
                    startService(refreshIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                var isOnboardingDone by remember {
                    mutableStateOf(PausaPrefs.isOnboardingCompleted(context))
                }

                if (!isOnboardingDone) {
                    if (PausaPrefs.isOnboardingCompleted(context)) {
                        BackHandler {
                            isOnboardingDone = true
                        }
                    }
                    WelcomeScreen(
                        onFinishOnboarding = {
                            isOnboardingDone = true
                        }
                    )
                } else {
                    MainAppScreen(
                        onReconfigureApps = {
                            isOnboardingDone = false
                        }
                    )
                }
            }
        }
    }
}

// Model representing a configurable target social network app
data class BlockTargetApp(
    val name: String,
    val packageName: String,
    val description: String
)

@Composable
fun MainAppScreen(
    onReconfigureApps: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(MainScreenTab.FOCUS) }

    // Load installed and suggested apps dynamically from the phone
    val installedApps = remember {
        InstalledAppsHelper.getInstalledApps(context)
    }
    val appsList = remember(installedApps) {
        installedApps.map {
            BlockTargetApp(
                name = it.name,
                packageName = it.packageName,
                description = it.description
            )
        }
    }

    var blockedPackages by remember { mutableStateOf(PausaPrefs.getBlockedPackages(context)) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0F172A), // Sleek slate dark background
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainScreenTab.FOCUS,
                    onClick = { selectedTab = MainScreenTab.FOCUS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Modo Focus"
                        )
                    },
                    label = { Text("Modo Focus") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF25D366),
                        selectedTextColor = Color(0xFF25D366),
                        indicatorColor = Color(0xFF25D366).copy(alpha = 0.2f),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("tab_focus_mode")
                )
                NavigationBarItem(
                    selected = selectedTab == MainScreenTab.STATS,
                    onClick = { selectedTab = MainScreenTab.STATS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Tiempo de Uso"
                        )
                    },
                    label = { Text("Tiempo de Uso") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF38BDF8).copy(alpha = 0.2f),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("tab_usage_stats")
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            MainScreenTab.FOCUS -> {
                PausaDashboard(
                    appsList = appsList,
                    blockedPackages = blockedPackages,
                    onBlockedPackagesChange = { newPackages ->
                        blockedPackages = newPackages
                        PausaPrefs.setBlockedPackages(context, newPackages)
                    },
                    onReconfigureApps = onReconfigureApps,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            MainScreenTab.STATS -> {
                UsageStatsScreen(
                    blockedPackages = blockedPackages,
                    onToggleBlocked = { pkg ->
                        val newPackages = if (blockedPackages.contains(pkg)) {
                            blockedPackages - pkg
                        } else {
                            blockedPackages + pkg
                        }
                        blockedPackages = newPackages
                        PausaPrefs.setBlockedPackages(context, newPackages)
                    },
                    targetApps = appsList,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PausaDashboard(
    appsList: List<BlockTargetApp>,
    blockedPackages: Set<String>,
    onBlockedPackagesChange: (Set<String>) -> Unit,
    onReconfigureApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live variables to hold state of permissions inside Compose
    var hasUsageStats by remember { mutableStateOf(PermissionUtils.hasUsageAccessPermission(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var hasNotifications by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }

    // Re-check permissions when coming back to the screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageStats = PermissionUtils.hasUsageAccessPermission(context)
                hasOverlay = PermissionUtils.hasOverlayPermission(context)
                hasNotifications = PermissionUtils.hasNotificationPermission(context)
                PausaPrefs.updateState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle runtime Notification Permission Request
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifications = granted
    }

    val isAllPermissionsGranted = hasUsageStats && hasOverlay && hasNotifications

    // Query live today usage for target apps
    val todayUsageMap = remember(hasUsageStats, blockedPackages) {
        AppUsageHelper.getQuickTodayUsageMap(context, appsList.map { it.packageName })
    }

    // Live state of Pausa Countdown
    val isPausaActive by PausaPrefs.isPausaActiveFlow.collectAsState()
    val remainingSecs by PausaPrefs.remainingSecondsFlow.collectAsState()

    val minutes = remainingSecs / 60
    val seconds = remainingSecs % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.ic_focus_logo_square),
                        contentDescription = "Logo Focus",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column {
                        Text(
                            text = "Focus",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Tu escudo contra el ruido digital",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                IconButton(
                    onClick = onReconfigureApps,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .testTag("manage_apps_header_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configurar apps y permisos",
                        tint = Color(0xFF25D366)
                    )
                }
            }
        }

        // PERMISSIONS AND ONBOARDING CARDS (Show if any permission is missing)
        if (!isAllPermissionsGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Configuración requerida",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Permisos Requeridos",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Para poder interceptar y bloquear el acceso a las redes sociales por 15 minutos, precisamos que autorices los siguientes accesos en tu Android:",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        // Permission Item 1: Usage Access
                        PermissionRow(
                            title = "Acceso a Datos de Uso",
                            description = "Saber cuándo abres una red social.",
                            isGranted = hasUsageStats,
                            onRequest = { PermissionUtils.launchUsageAccessSettings(context) }
                        )

                        // Permission Item 2: Display Over Other Apps
                        PermissionRow(
                            title = "Mostrar sobre otras apps",
                            description = "Interrumpir con la pantalla de descanso.",
                            isGranted = hasOverlay,
                            onRequest = { PermissionUtils.launchOverlaySettings(context) }
                        )

                        // Permission Item 3: Post Notifications (Android 13+)
                        PermissionRow(
                            title = "Permiso de Notificaciones",
                            description = "Mantener el escudo activo en segundo plano.",
                            isGranted = hasNotifications,
                            onRequest = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                }
            }
        }

        // CORE CONTROL INTERFACE (START BUTTON OR TIMER STATUS)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isPausaActive) Color(0xFF14532D).copy(alpha = 0.2f) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isPausaActive) {
                        // Countdown state View
                        Text(
                            text = "MODO PAUSA ACTIVO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            letterSpacing = 2.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = timerText,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "restantes",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Disfruta de este espacio de calma sin interrupciones.",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Dev option to unlock/cancel if needed, else the rule says we can keep it locked.
                        // Let's add a clear label that says "Pausa inquebrantable" so they know it is real.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "No puedes cancelar esta pausa",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                        }

                    } else {
                        // Standard Active configuration and Trigger Button
                        Text(
                            text = "MODO PAUSA DESACTIVADO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.5.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large beautiful Action Dial Button
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isAllPermissionsGranted) {
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF25D366),
                                                Color(0xFF1EBE5D)
                                            )
                                        )
                                    } else {
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF334155),
                                                Color(0xFF1E293B)
                                            )
                                        )
                                    }
                                )
                                .clickable(enabled = isAllPermissionsGranted) {
                                    val endTime = System.currentTimeMillis() + 15 * 60 * 1000
                                    PausaPrefs.setBlockEndTimeMs(context, endTime)
                                    
                                    // Start BlockerService
                                    val serviceIntent = Intent(context, BlockerService::class.java).apply {
                                        action = BlockerService.ACTION_START
                                    }
                                    try {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(serviceIntent)
                                        } else {
                                            context.startService(serviceIntent)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAllPermissionsGranted) Icons.Default.PlayArrow else Icons.Default.Lock,
                                    contentDescription = "Iniciar Pausa",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isAllPermissionsGranted) "Iniciar Pausa" else "Configurar",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "por 15 minutos",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isAllPermissionsGranted) {
                            Text(
                                text = "⚠️ Autoriza los permisos para activar el botón",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEF4444),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "Presiona para silenciar redes sociales durante 15 minutos.",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        // SOCIAL APP CHECKBOX SELECTORS
        if (!isPausaActive) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val installedBlockedCount = appsList.count { blockedPackages.contains(it.packageName) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apps a Pausar ($installedBlockedCount)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = "Solo las marcadas serán inaccesibles durante la pausa",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Button(
                        onClick = onReconfigureApps,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color(0xFF25D366)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("edit_apps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Configurar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF25D366)
                        )
                    }
                }
            }

            val orderedApps = appsList.sortedWith(
                compareByDescending<BlockTargetApp> { blockedPackages.contains(it.packageName) }
                    .thenBy { it.name }
            )

            if (orderedApps.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No se detectaron aplicaciones adicionales instaladas.",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(orderedApps, key = { it.packageName }) { app ->
                val isChecked = blockedPackages.contains(app.packageName)
                val usageToday = todayUsageMap[app.packageName] ?: "0m"
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSet = if (isChecked) {
                                blockedPackages - app.packageName
                            } else {
                                blockedPackages + app.packageName
                            }
                            onBlockedPackagesChange(newSet)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val letter = app.name.firstOrNull()?.toString() ?: "S"
                                Text(
                                    text = letter,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = app.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (hasUsageStats && usageToday != "0m") {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "⏱️ $usageToday hoy",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = app.description,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val newSet = if (checked == true) {
                                    blockedPackages + app.packageName
                                } else {
                                    blockedPackages - app.packageName
                                }
                                onBlockedPackagesChange(newSet)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF25D366),
                                uncheckedColor = Color(0xFF64748B)
                            )
                        )
                    }
                }
            }
        } else {
            // Screen explanation shown during active Pause
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Escudo de Silencio Activo",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Si intentas abrir cualquiera de las redes seleccionadas, Focus se interpondrá pacíficamente para que regreses al presente.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) Color(0xFF10B981) else Color.White
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        if (isGranted) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp),
                enabled = false,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Permitido",
                        tint = Color(0xFF25D366),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Listo",
                        fontSize = 11.sp,
                        color = Color(0xFF25D366),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "Autorizar",
                    fontSize = 11.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
