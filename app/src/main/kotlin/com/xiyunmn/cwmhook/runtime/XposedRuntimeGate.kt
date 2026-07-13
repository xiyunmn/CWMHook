package com.xiyunmn.cwmhook.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.xiyunmn.cwmhook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import java.util.Locale
import java.util.zip.ZipFile

object XposedRuntimeGate {
    fun isRootFramework(context: Context): Boolean {
        if (hasRootlessPatchArtifact(context)) {
            return false
        }
        val module = XposedCompat.module ?: return false
        val frameworkName = runCatching { module.frameworkName }
            .getOrNull()
            .orEmpty()
            .lowercase(Locale.ROOT)
        val properties = runCatching { module.frameworkProperties }.getOrNull()
        if (properties != null && (properties and XposedInterface.PROP_CAP_SYSTEM) != 0L) {
            return true
        }
        if (isKnownRootFramework(frameworkName)) {
            return true
        }
        return hasLegacyBridge()
    }

    private fun isKnownRootFramework(frameworkName: String): Boolean {
        if (frameworkName.isBlank()) return false
        return ROOT_FRAMEWORK_KEYWORDS.any { frameworkName.contains(it) } &&
            ROOTLESS_FRAMEWORK_KEYWORDS.none { frameworkName.contains(it) }
    }

    private fun hasLegacyBridge(): Boolean {
        val bridgeClassName = legacyBridgeClassName()
        val bridgeClass = runCatching {
            Class.forName(bridgeClassName, false, ClassLoader.getSystemClassLoader())
        }.getOrNull() ?: runCatching {
            Class.forName(bridgeClassName)
        }.getOrNull() ?: return false
        return runCatching {
            bridgeClass.getDeclaredMethod(legacyVersionMethodName()).invoke(null) as? Int
        }.getOrNull()?.let { it > 0 } ?: true
    }

    private fun legacyBridgeClassName(): String {
        return intArrayOf(
            100, 101, 46, 114, 111, 98, 118, 46, 97, 110, 100, 114, 111, 105, 100,
            46, 120, 112, 111, 115, 101, 100, 46, 88, 112, 111, 115, 101, 100,
            66, 114, 105, 100, 103, 101,
        ).joinToString(separator = "") { it.toChar().toString() }
    }

    private fun legacyVersionMethodName(): String {
        return intArrayOf(
            103, 101, 116, 88, 112, 111, 115, 101, 100, 86, 101, 114, 115, 105, 111, 110,
        ).joinToString(separator = "") { it.toChar().toString() }
    }

    private fun hasRootlessPatchArtifact(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        val appInfo = applicationInfo(appContext)
        if (appInfo?.metaData?.containsKey("lspatch") == true ||
            appInfo?.metaData?.containsKey("npatch") == true
        ) {
            return true
        }
        val sourcePaths = linkedSetOf<String>()
        listOf(
            appContext.applicationInfo?.sourceDir,
            appContext.applicationInfo?.publicSourceDir,
            appContext.packageResourcePath,
            appInfo?.sourceDir,
            appInfo?.publicSourceDir,
        ).filterNotNullTo(sourcePaths)
        if (sourcePaths.any { path ->
                val lower = path.replace('\\', '/').lowercase(Locale.ROOT)
                ROOTLESS_SOURCE_KEYWORDS.any { lower.contains(it) }
            }
        ) {
            return true
        }
        return sourcePaths.any(::hasRootlessZipEntry) ||
            ROOTLESS_ASSET_MARKERS.any { marker -> assetExists(appContext, marker) }
    }

    private fun applicationInfo(context: Context): ApplicationInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            }
        }.getOrNull()
    }

    private fun hasRootlessZipEntry(sourcePath: String): Boolean {
        if (!sourcePath.endsWith(".apk", ignoreCase = true)) {
            return false
        }
        return runCatching {
            ZipFile(sourcePath).use { zip ->
                ROOTLESS_ZIP_MARKERS.any { marker -> zip.getEntry(marker) != null } ||
                    zip.entries().asSequence().any { entry ->
                        ROOTLESS_ZIP_PREFIXES.any { prefix -> entry.name.startsWith(prefix) }
                    }
            }
        }.getOrDefault(false)
    }

    private fun assetExists(context: Context, path: String): Boolean {
        return runCatching {
            context.assets.open(path).close()
            true
        }.getOrDefault(false)
    }

    private val ROOT_FRAMEWORK_KEYWORDS = listOf(
        "lsposed",
        "relsposed",
        "edxposed",
        "xposed",
        "vector",
        "riru",
        "zygisk",
    )

    private val ROOTLESS_FRAMEWORK_KEYWORDS = listOf(
        "lspatch",
        "npatch",
        "funpatch",
        "fpa",
        "atom",
        "yuanzi",
    )

    private val ROOTLESS_SOURCE_KEYWORDS = listOf(
        "/lspatch/",
        "-lspatched.apk",
        "/npatch/",
        "-npatched.apk",
        "/funpatch/",
        "/fpa/",
        "/atom/",
        "/yuanzi/",
    )

    private val ROOTLESS_ASSET_MARKERS = listOf(
        "lspatch/config.json",
        "npatch/config.json",
    )

    private val ROOTLESS_ZIP_MARKERS = listOf(
        "assets/lspatch/config.json",
        "assets/lspatch/loader.dex",
        "assets/lspatch/metaloader.dex",
        "assets/lspatch/origin.apk",
        "assets/npatch/config.json",
        "assets/npatch/loader.bin",
        "assets/npatch/metaloader.dex",
        "assets/npatch/mtprovider.dex",
        "assets/npatch/origin.apk",
    )

    private val ROOTLESS_ZIP_PREFIXES = listOf(
        "assets/lspatch/modules/",
        "assets/npatch/modules/",
    )
}
