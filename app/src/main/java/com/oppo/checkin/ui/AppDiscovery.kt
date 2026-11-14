package com.oppo.checkin.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.oppo.checkin.data.AppCandidate

object AppDiscovery {

    fun discoverLaunchableApps(context: Context): List<AppCandidate> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        }

        val selfPackage = context.packageName
        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == selfPackage) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val isLikelyOppo = isLikelyOppoStore(label, pkg)
                AppCandidate(
                    appName = label,
                    packageName = pkg,
                    isLikelyOppoApp = isLikelyOppo
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<AppCandidate> { it.isLikelyOppoApp }
                    .thenBy { it.appName.lowercase() }
            )
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun loadAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }

    private fun isLikelyOppoStore(appName: String, packageName: String): Boolean {
        val lowerPkg = packageName.lowercase()
        val lowerName = appName.lowercase()
        return lowerPkg.contains("oppo") ||
            lowerPkg.contains("heytap") ||
            lowerPkg.contains("coloros") ||
            lowerName.contains("oppo") ||
            lowerName.contains("欢太") ||
            lowerName.contains("商城")
    }
}
