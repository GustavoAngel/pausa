package com.example.ui

import androidx.compose.animation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.*

@Composable
fun UsageStatsScreen(
    blockedPackages: Set<String>,
    onToggleBlocked: (String) -> Unit,
    targetApps: List<BlockTargetApp>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var period by remember { mutableStateOf(UsagePeriod.TODAY) }
    var filterOnlyMonitored by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    val hasPermission = PermissionUtils.hasUsageAccessPermission(context)

    val summary = remember(period, blockedPackages, refreshKey, hasPermission) {
        if (hasPermission) {
            AppUsageHelper.getUsageSummary(
                context = context,
                period = period,
                monitoredPackages = blockedPackages,
                targetApps = targetApps
            )
        } else {
            null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Title and Refresh
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tiempo de Uso",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Monitoreo del tiempo en cada aplicación",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                IconButton(
                    onClick = { refreshKey++ },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .testTag("refresh_usage_stats_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar estadísticas",
                        tint = Color(0xFF25D366)
                    )
                }
            }
        }

        // Permission Card if permission missing
        if (!hasPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Permiso Necesario",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Para poder medir el tiempo exacto que pasas en cada app, Android requiere el permiso de 'Acceso a datos de uso'.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )

                        Button(
                            onClick = { PermissionUtils.launchUsageAccessSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_usage_permission_button")
                        ) {
                            Text(
                                text = "Conceder Acceso a Datos de Uso",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Period Selector (Hoy vs 7 días)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E293B))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UsagePeriod.values().forEach { p ->
                        val isSelected = period == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(0xFF25D366) else Color.Transparent
                                )
                                .clickable { period = p }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // Summary KPI Cards
            item {
                summary?.let { stats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Screen Time
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Pantalla Total",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Text(
                                    text = stats.formattedTotalTime,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = if (period == UsagePeriod.TODAY) "registrado hoy" else "última semana",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Monitored Apps Time
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "En Redes",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Text(
                                    text = stats.formattedMonitoredTime,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF25D366)
                                )
                                Text(
                                    text = "${blockedPackages.size} seleccionadas",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }

            // Filter Chips (Todas vs Solo Monitoreadas)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !filterOnlyMonitored,
                        onClick = { filterOnlyMonitored = false },
                        label = { Text("Todas las Apps") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF334155),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                    FilterChip(
                        selected = filterOnlyMonitored,
                        onClick = { filterOnlyMonitored = true },
                        label = { Text("Solo Monitoreadas") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF334155),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            // Apps List
            val displayedApps = summary?.apps?.filter { item ->
                if (filterOnlyMonitored) item.isMonitored else true
            } ?: emptyList()

            if (displayedApps.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Sin datos de uso registrados aún",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Abre tus aplicaciones en el dispositivo y regresa a Focus para ver el desglose en tiempo real.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(displayedApps) { appItem ->
                    AppUsageCard(
                        item = appItem,
                        onToggleMonitored = { onToggleBlocked(appItem.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun AppUsageCard(
    item: AppUsageItem,
    onToggleMonitored: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_usage_card_${item.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // App Initial Box
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (item.isMonitored) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.appName.firstOrNull()?.toString()?.uppercase() ?: "A",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isMonitored) Color(0xFFF59E0B) else Color.White
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.appName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (item.isMonitored) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF25D366).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Monitoreada",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF25D366)
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.packageName,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Measured Time Tag
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.formattedTime,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = if (item.usageTimeMillis > 0) Color(0xFF38BDF8) else Color(0xFF64748B)
                    )
                    Text(
                        text = if (item.usageTimeMillis > 0) "${(item.percentageOfTotal * 100).toInt()}% total" else "Sin uso",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Visual Progress Meter
            LinearProgressIndicator(
                progress = { item.percentageOfMax },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (item.isMonitored) Color(0xFF25D366) else Color(0xFF38BDF8),
                trackColor = Color(0xFF334155),
            )
        }
    }
}
