package com.xiyunmn.cwmhook.feature.chapterbackup

import android.app.Activity
import android.widget.Toast
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

object ChapterBackupFeature {
    private const val TAG = "CWMHook.ChapterExport"

    private val hookInstaller = ChapterBackupHookInstaller(TAG)

    fun install(module: XposedModule, classLoader: ClassLoader) {
        hookInstaller.install(module, classLoader)
        ModuleFileLogger.i(TAG, "Chapter backup feature install requested")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        hookInstaller.retryDeferredHooks(module, classLoader, reason)
    }

    fun launchDirectoryPicker(activity: Activity) {
        ChapterBackupDirectoryPicker.launch(activity, TAG)
    }

    fun clearExportDirectory(activity: Activity): Boolean {
        return ChapterBackupConfigStore.clearExportTreeUri(activity)
    }

    fun exportCachedBooks(activity: Activity) {
        if (!ChapterBackupConfigStore.readLocal(activity).enabled) {
            Toast.makeText(activity, "个人章节导出未启用", Toast.LENGTH_SHORT).show()
            return
        }
        hookInstaller.exportCachedBooks(
            activity,
            toastCallback(activity, "开始导出已缓存章节"),
        )
    }

    internal fun showCatalogExportSelector(
        activity: Activity,
        exporter: ChapterBackupExporter,
        bookInfo: Any?,
        downloadType: String?,
    ) {
        if (!ChapterBackupConfigStore.readLocal(activity).enabled) {
            Toast.makeText(activity, "个人章节导出未启用", Toast.LENGTH_SHORT).show()
            return
        }
        ChapterExportSelectionWindow(activity, exporter, bookInfo, downloadType).show()
    }

    private fun toastCallback(activity: Activity, startedMessage: String): ChapterBackupExporter.Callback {
        return object : ChapterBackupExporter.Callback {
            override fun onStarted() {
                Toast.makeText(activity, startedMessage, Toast.LENGTH_SHORT).show()
            }

            override fun onSuccess(result: ChapterBackupResult) {
                Toast.makeText(
                    activity,
                    "已导出 ${result.bookCount} 本、${result.chapterCount} 章",
                    Toast.LENGTH_LONG,
                ).show()
            }

            override fun onFailure(message: String) {
                Toast.makeText(activity, message.ifBlank { "章节导出失败" }, Toast.LENGTH_LONG).show()
            }
        }
    }
}
