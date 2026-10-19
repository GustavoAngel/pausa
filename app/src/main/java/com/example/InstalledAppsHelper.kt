package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledAppItem(
    val name: String,
    val packageName: String,
    val description: String,
    val isSocialOrDistraction: Boolean,
    val isSystemApp: Boolean
)

object InstalledAppsHelper {

    // Curated metadata and categories for known social & distraction platforms
    val KNOWN_DISTRACTION_APPS = listOf(
        InstalledAppItem(
            name = "Instagram",
            packageName = "com.instagram.android",
            description = "Feed, reels y mensajería directa",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "Facebook",
            packageName = "com.facebook.katana",
            description = "Noticias, grupos y videos",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "TikTok",
            packageName = "com.zhiliaoapp.musically",
            description = "Videos cortos y tendencias",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "X / Twitter",
            packageName = "com.twitter.android",
            description = "Mensajes y tendencias globales",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "YouTube",
            packageName = "com.google.android.youtube",
            description = "Videos y shorts",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "Reddit",
            packageName = "com.reddit.frontpage",
            description = "Comunidades y debates",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "Snapchat",
            packageName = "com.snapchat.android",
            description = "Filtros y snaps instantáneos",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "Pinterest",
            packageName = "com.pinterest",
            description = "Tableros de ideas e imágenes",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "WhatsApp",
            packageName = "com.whatsapp",
            description = "Chats y estados sociales",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "Telegram",
            packageName = "org.telegram.messenger",
            description = "Canales y mensajería",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "Netflix",
            packageName = "com.netflix.mediaclient",
            description = "Streaming de series y películas",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "Twitch",
            packageName = "tv.twitch.android.app",
            description = "Transmisiones en vivo",
            isSocialOrDistraction = true,
            isSystemApp = false
        ),
        InstalledAppItem(
            name = "Discord",
            packageName = "com.discord",
            description = "Comunidades y chat de voz",
            isSocialOrDistraction = true,
            isSystemApp = false
        )
    )

    private val KNOWN_MAP = KNOWN_DISTRACTION_APPS.associateBy { it.packageName }

    /**
     * Retrieves all installed launchable apps and user applications on the device,
     * merging with known social apps so user can select them even if not yet opened.
     */
    fun getInstalledApps(context: Context): List<InstalledAppItem> {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val foundMap = mutableMapOf<String, InstalledAppItem>()

        // 1. Query all launcher activities (user launchable apps)
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                if (pkg == selfPkg || pkg == "com.android.systemui") continue

                val known = KNOWN_MAP[pkg]
                val appLabel = try {
                    ri.loadLabel(pm).toString().trim()
                } catch (e: Exception) {
                    known?.name ?: pkg.substringAfterLast('.')
                }

                val isSocial = known != null || isDistractionPackage(pkg)
                val desc = known?.description ?: if (isSocial) "Red Social / Ocio" else "Aplicación instalada"

                foundMap[pkg] = InstalledAppItem(
                    name = if (appLabel.isNotEmpty()) appLabel else (known?.name ?: pkg),
                    packageName = pkg,
                    description = desc,
                    isSocialOrDistraction = isSocial,
                    isSystemApp = false
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Query installed applications to catch any user apps without launcher activity
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val pkg = app.packageName
                if (pkg == selfPkg || pkg == "com.android.systemui") continue
                if (foundMap.containsKey(pkg)) continue

                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val known = KNOWN_MAP[pkg]

                // Show all non-system apps, or known distraction apps even if marked system (like YouTube)
                if (!isSystem || known != null) {
                    val appLabel = try {
                        pm.getApplicationLabel(app).toString().trim()
                    } catch (e: Exception) {
                        known?.name ?: pkg.substringAfterLast('.')
                    }
                    val isSocial = known != null || isDistractionPackage(pkg)
                    val desc = known?.description ?: if (isSocial) "Red Social / Ocio" else "Aplicación instalada"

                    foundMap[pkg] = InstalledAppItem(
                        name = if (appLabel.isNotEmpty()) appLabel else (known?.name ?: pkg),
                        packageName = pkg,
                        description = desc,
                        isSocialOrDistraction = isSocial,
                        isSystemApp = isSystem
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // NEVER add uninstalled apps or suggestions. Only apps actually installed on the phone are returned.

        // Sort: Social/Distraction apps first, then alphabetical by name
        return foundMap.values.sortedWith(
            compareByDescending<InstalledAppItem> { it.isSocialOrDistraction }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun isDistractionPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower.contains("instagram") ||
                lower.contains("facebook") ||
                lower.contains("tiktok") ||
                lower.contains("twitter") ||
                lower.contains("youtube") ||
                lower.contains("reddit") ||
                lower.contains("snapchat") ||
                lower.contains("pinterest") ||
                lower.contains("netflix") ||
                lower.contains("twitch") ||
                lower.contains("discord") ||
                lower.contains("game")
    }
}
