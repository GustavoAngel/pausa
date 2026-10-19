package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Focus", appName)
  }

  @Test
  fun `formatDuration formats minutes and hours correctly`() {
    assertEquals("0m", AppUsageHelper.formatDuration(0L))
    assertEquals("< 1m", AppUsageHelper.formatDuration(5000L))
    assertEquals("15m", AppUsageHelper.formatDuration(15 * 60 * 1000L))
    assertEquals("1h", AppUsageHelper.formatDuration(60 * 60 * 1000L))
    assertEquals("1h 25m", AppUsageHelper.formatDuration((85 * 60 * 1000L)))
  }

  @Test
  fun `onboarding state persists correctly in PausaPrefs`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(false, PausaPrefs.isOnboardingCompleted(context))
    PausaPrefs.setOnboardingCompleted(context, true)
    assertEquals(true, PausaPrefs.isOnboardingCompleted(context))
    PausaPrefs.setOnboardingCompleted(context, false)
    assertEquals(false, PausaPrefs.isOnboardingCompleted(context))
  }

  @Test
  fun `installed apps helper only lists installed apps and ignores uninstalled suggestions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val apps = InstalledAppsHelper.getInstalledApps(context)
    // Uninstalled suggestions like Instagram or TikTok must NOT appear
    assert(apps.none { it.packageName == "com.instagram.android" })
    assert(apps.none { it.packageName == "com.zhiliaoapp.musically" })

    // Simulate an installed user package
    val pm = context.packageManager
    val shadowPm = org.robolectric.Shadows.shadowOf(pm)
    val packageInfo = android.content.pm.PackageInfo().apply {
      packageName = "com.sample.installedapp"
      applicationInfo = android.content.pm.ApplicationInfo().apply {
        packageName = "com.sample.installedapp"
        flags = 0
      }
    }
    shadowPm.addPackage(packageInfo)

    val updatedApps = InstalledAppsHelper.getInstalledApps(context)
    assert(updatedApps.any { it.packageName == "com.sample.installedapp" })
  }
}
