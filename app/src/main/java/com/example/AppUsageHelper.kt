package com.example

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.Calendar

enum class UsagePeriod(val label: String) {
    TODAY("Hoy"),
    WEEK("7 días")
}

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long,
    val formattedTime: String,
    val percentageOfTotal: Float,
    val percentageOfMax: Float,
    val isMonitored: Boolean
)

data class UsageStatsSummary(
    val period: UsagePeriod,
    val totalTimeMillis: Long,
    val formattedTotalTime: String,
    val monitoredTimeMillis: Long,
    val formattedMonitoredTime: String,
    val apps: List<AppUsageItem>
)

object AppUsageHelper {

    private val KNOWN_NAMES = mapOf(
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
        "com.facebook.orca" to "Messenger",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.twitter.android" to "X / Twitter",
        "com.google.android.youtube" to "YouTube",
        "com.reddit.frontpage" to "Reddit",
        "com.snapchat.android" to "Snapchat",
        "com.pinterest" to "Pinterest",
        "com.whatsapp" to "WhatsApp",
        "com.google.android.apps.messaging" to "Mensajes",
        "com.android.chrome" to "Chrome",
        "com.spotify.music" to "Spotify",
        "com.netflix.mediaclient" to "Netflix",
        "com.google.android.gm" to "Gmail"
    )

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            millis >= 1000 -> "< 1m"
            else -> "0m"
        }
    }

    fun getUsageSummary(
        context: Context,
        period: UsagePeriod = UsagePeriod.TODAY,
        monitoredPackages: Set<String> = emptySet(),
        targetApps: List<BlockTargetApp> = emptyList()
    ): UsageStatsSummary {
        if (!PermissionUtils.hasUsageAccessPermission(context)) {
            return UsageStatsSummary(
                period = period,
                totalTimeMillis = 0L,
                formattedTotalTime = "0m",
                monitoredTimeMillis = 0L,
                formattedMonitoredTime = "0m",
                apps = emptyList()
            )
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return UsageStatsSummary(
                period = period,
                totalTimeMillis = 0L,
                formattedTotalTime = "0m",
                monitoredTimeMillis = 0L,
                formattedMonitoredTime = "0m",
                apps = emptyList()
            )

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        val startTime = when (period) {
            UsagePeriod.TODAY -> {
                calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            UsagePeriod.WEEK -> {
                calendar.apply {
                    add(Calendar.DAY_OF_YEAR, -6)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }

        val rawStats: Map<String, UsageStats> = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        val pm = context.packageManager
        val targetPkgMap = targetApps.associate { it.packageName to it.name }

        var totalTimeMillis = 0L
        var monitoredTimeMillis = 0L

        // Gather stats
        val usageMap = mutableMapOf<String, Long>()
        for ((pkg, stats) in rawStats) {
            val duration = stats.totalTimeInForeground
            if (duration > 0) {
                // Ignore self and android internal launchers to keep stats relevant
                if (pkg == context.packageName || pkg == "com.android.systemui") continue
                val isInstalled = try {
                    pm.getApplicationInfo(pkg, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
                if (!isInstalled) continue

                usageMap[pkg] = duration
                totalTimeMillis += duration
                if (monitoredPackages.contains(pkg)) {
                    monitoredTimeMillis += duration
                }
            }
        }

        // Also make sure all configured target apps that are installed are represented in the list even if 0 usage
        for (target in targetApps) {
            val isInstalled = try {
                pm.getApplicationInfo(target.packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            if (isInstalled && !usageMap.containsKey(target.packageName)) {
                usageMap[target.packageName] = 0L
            }
        }

        val appItems = mutableListOf<AppUsageItem>()
        val maxUsage = (usageMap.values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val totalUsageFloat = totalTimeMillis.coerceAtLeast(1L).toFloat()

        for ((pkg, time) in usageMap) {
            val appName = targetPkgMap[pkg] ?: KNOWN_NAMES[pkg] ?: try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg.substringAfterLast('.')
            }

            appItems.add(
                AppUsageItem(
                    packageName = pkg,
                    appName = appName,
                    usageTimeMillis = time,
                    formattedTime = formatDuration(time),
                    percentageOfTotal = (time.toFloat() / totalUsageFloat).coerceIn(0f, 1f),
                    percentageOfMax = (time.toFloat() / maxUsage).coerceIn(0f, 1f),
                    isMonitored = monitoredPackages.contains(pkg)
                )
            )
        }

        // Sort: Monitored apps with highest usage first, then remaining apps by usage
        appItems.sortWith(
            compareByDescending<AppUsageItem> { it.usageTimeMillis }
                .thenByDescending { it.isMonitored }
                .thenBy { it.appName }
        )

        return UsageStatsSummary(
            period = period,
            totalTimeMillis = totalTimeMillis,
            formattedTotalTime = formatDuration(totalTimeMillis),
            monitoredTimeMillis = monitoredTimeMillis,
            formattedMonitoredTime = formatDuration(monitoredTimeMillis),
            apps = appItems
        )
    }

    /**
     * Helper to get quick usage map of packageName -> formatted string for today
     */
    fun getQuickTodayUsageMap(
        context: Context,
        packages: List<String>
    ): Map<String, String> {
        if (!PermissionUtils.hasUsageAccessPermission(context)) return emptyMap()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val rawStats = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        return packages.associateWith { pkg ->
            val millis = rawStats[pkg]?.totalTimeInForeground ?: 0L
            formatDuration(millis)
        }
    }
}
