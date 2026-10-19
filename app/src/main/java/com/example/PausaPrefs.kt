package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PausaPrefs {
    private const val PREFS_NAME = "pausa_prefs"
    private const val KEY_BLOCK_END_TIME = "block_end_time_ms"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages_v1"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed_v2"



    private val _isPausaActiveFlow = MutableStateFlow(false)
    val isPausaActiveFlow: StateFlow<Boolean> = _isPausaActiveFlow.asStateFlow()

    private val _remainingSecondsFlow = MutableStateFlow(0L)
    val remainingSecondsFlow: StateFlow<Long> = _remainingSecondsFlow.asStateFlow()

    private fun getSharedPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes the state flows based on current preferences.
     */
    fun init(context: Context) {
        updateState(context)
    }

    fun getBlockEndTimeMs(context: Context): Long {
        return getSharedPrefs(context).getLong(KEY_BLOCK_END_TIME, 0L)
    }

    fun setBlockEndTimeMs(context: Context, endTimeMs: Long) {
        getSharedPrefs(context).edit().putLong(KEY_BLOCK_END_TIME, endTimeMs).apply()
        updateState(context)
    }

    fun isPausaActive(context: Context): Boolean {
        val endTime = getBlockEndTimeMs(context)
        return System.currentTimeMillis() < endTime
    }

    fun getRemainingSeconds(context: Context): Long {
        val endTime = getBlockEndTimeMs(context)
        val diff = endTime - System.currentTimeMillis()
        return if (diff > 0) diff / 1000 else 0L
    }

    fun getBlockedPackages(context: Context): Set<String> {
        val prefs = getSharedPrefs(context)
        val installedSet = InstalledAppsHelper.getInstalledApps(context).map { it.packageName }.toSet()
        val saved = prefs.getStringSet(KEY_BLOCKED_PACKAGES, null)
        return if (saved != null) {
            saved.filter { installedSet.contains(it) }.toSet()
        } else {
            InstalledAppsHelper.getInstalledApps(context)
                .filter { it.isSocialOrDistraction }
                .map { it.packageName }
                .toSet()
        }
    }

    fun setBlockedPackages(context: Context, packages: Set<String>) {
        getSharedPrefs(context).edit().putStringSet(KEY_BLOCKED_PACKAGES, packages).apply()
    }

    fun updateState(context: Context) {
        val active = isPausaActive(context)
        _isPausaActiveFlow.value = active
        _remainingSecondsFlow.value = getRemainingSeconds(context)
    }

    fun clearPause(context: Context) {
        setBlockEndTimeMs(context, 0)
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return getSharedPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getSharedPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
}
