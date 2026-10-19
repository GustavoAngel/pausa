package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.InstalledAppItem
import com.example.InstalledAppsHelper
import com.example.PausaPrefs
import com.example.PermissionUtils

val FocusGreenButtonColor = Color(0xFF25D366)

@Composable
fun WelcomeScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live permission states
    var hasUsageStats by remember { mutableStateOf(PermissionUtils.hasUsageAccessPermission(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var hasNotifications by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }

    // Re-check permissions when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageStats = PermissionUtils.hasUsageAccessPermission(context)
                hasOverlay = PermissionUtils.hasOverlayPermission(context)
                hasNotifications = PermissionUtils.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifications = granted
    }

    // Query installed apps on the device
    val allInstalledApps = remember {
        InstalledAppsHelper.getInstalledApps(context)
    }

    // Default selection: pre-select distraction/social apps
    var selectedPackages by remember {
        mutableStateOf(
            allInstalledApps.filter { it.isSocialOrDistraction }.map { it.packageName }.toSet()
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var filterOnlySocial by remember { mutableStateOf(false) }

    val filteredApps = remember(searchQuery, filterOnlySocial, allInstalledApps) {
        allInstalledApps.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = if (filterOnlySocial) item.isSocialOrDistraction else true
            matchesQuery && matchesCategory
        }
    }

    val isAllPermissionsGranted = hasUsageStats && hasOverlay && hasNotifications

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0F172A),
        bottomBar = {
            Surface(
                color = Color(0xFF1E293B),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedPackages.size} apps seleccionadas",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = FocusGreenButtonColor
                        )
                        if (!isAllPermissionsGranted) {
                            Text(
                                text = "⚠️ Faltan permisos",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF59E0B)
                            )
                        } else {
                            Text(
                                text = "✓ Permisos listos",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FocusGreenButtonColor
                            )
                        }
                    }

                    // Main Action Button with color #25D366
                    Button(
                        onClick = {
                            // Save selected apps and mark onboarding complete
                            val finalSet = if (selectedPackages.isNotEmpty()) {
                                selectedPackages
                            } else {
                                allInstalledApps.filter { it.isSocialOrDistraction }.map { it.packageName }.toSet()
                            }
                            PausaPrefs.setBlockedPackages(context, finalSet)
                            PausaPrefs.setOnboardingCompleted(context, true)
                            onFinishOnboarding()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FocusGreenButtonColor,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("finish_onboarding_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Black
                            )
                            Text(
                                text = "Comenzar a usar Focus",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero Welcome Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.ic_focus_logo_square),
                        contentDescription = "Logo Focus",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Bienvenido a Focus",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Configura tus permisos y elige qué aplicaciones se pausarán cuando necesites concentración profunda.",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // SECTION 1: PERMISSIONS (Needs to ask permissions first)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(FocusGreenButtonColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FocusGreenButtonColor
                                )
                            }
                            Text(
                                text = "Permisos Necesarios",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Para poder medir el tiempo de pantalla y bloquear el acceso durante las pausas, autoriza estos 3 accesos:",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        // Permission 1: Usage Access
                        OnboardingPermissionItem(
                            title = "Acceso a Datos de Uso",
                            description = "Mide el tiempo de cada app y detecta aperturas.",
                            isGranted = hasUsageStats,
                            onRequest = { PermissionUtils.launchUsageAccessSettings(context) }
                        )

                        // Permission 2: Overlay
                        OnboardingPermissionItem(
                            title = "Mostrar sobre otras aplicaciones",
                            description = "Muestra la pantalla de pausa al abrir una app bloqueada.",
                            isGranted = hasOverlay,
                            onRequest = { PermissionUtils.launchOverlaySettings(context) }
                        )

                        // Permission 3: Notifications
                        OnboardingPermissionItem(
                            title = "Notificaciones del Sistema",
                            description = "Mantiene el servicio de protección activo en segundo plano.",
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

            // SECTION 2: APPS SELECTION (Installed apps on the device)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(FocusGreenButtonColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "2",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = FocusGreenButtonColor
                            )
                        }
                        Text(
                            text = "Elige las Apps para las Pausas",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "Estas son las aplicaciones instaladas en tu teléfono. Marca las que deseas incluir en las pausas de 15 minutos:",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )

                    // Search and filter
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_search_input"),
                        placeholder = { Text("Buscar aplicación instalada...", color = Color(0xFF64748B)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = Color(0xFF64748B)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar búsqueda",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FocusGreenButtonColor,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    // Quick Action Selection Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val socialPackages = allInstalledApps.filter { it.isSocialOrDistraction }.map { it.packageName }.toSet()
                                selectedPackages = socialPackages
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = FocusGreenButtonColor
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Text(
                                text = "Redes/Ocio",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FocusGreenButtonColor
                            )
                        }

                        Button(
                            onClick = {
                                selectedPackages = allInstalledApps.map { it.packageName }.toSet()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Text(
                                text = "Todas",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                selectedPackages = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Text(
                                text = "Limpiar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            if (filteredApps.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) {
                                    "No se encontraron aplicaciones instaladas con el nombre \"$searchQuery\"."
                                } else {
                                    "No se detectaron aplicaciones instaladas en el dispositivo."
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // APPS LIST ITEMS
            items(filteredApps, key = { it.packageName }) { appItem ->
                val isSelected = selectedPackages.contains(appItem.packageName)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF172033)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPackages = if (isSelected) {
                                selectedPackages - appItem.packageName
                            } else {
                                selectedPackages + appItem.packageName
                            }
                        }
                        .testTag("app_select_row_${appItem.packageName}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Initial badge
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (appItem.isSocialOrDistraction) FocusGreenButtonColor.copy(alpha = 0.15f)
                                        else Color.White.copy(alpha = 0.06f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = appItem.name.firstOrNull()?.toString()?.uppercase() ?: "A",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (appItem.isSocialOrDistraction) FocusGreenButtonColor else Color.White
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = appItem.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (appItem.isSocialOrDistraction) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(FocusGreenButtonColor.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Red Social / Ocio",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FocusGreenButtonColor
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = appItem.description,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedPackages = if (checked == true) {
                                    selectedPackages + appItem.packageName
                                } else {
                                    selectedPackages - appItem.packageName
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = FocusGreenButtonColor,
                                checkmarkColor = Color.Black,
                                uncheckedColor = Color(0xFF64748B)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPermissionItem(
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
                color = if (isGranted) FocusGreenButtonColor else Color.White
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (isGranted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(FocusGreenButtonColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Concedido",
                        tint = FocusGreenButtonColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Listo",
                        fontSize = 12.sp,
                        color = FocusGreenButtonColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusGreenButtonColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = "Autorizar",
                    fontSize = 12.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
