package com.xiyunmn.cwmhook.feature.chapterbackup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

internal object ChapterBackupDirectoryPicker {
    const val REQUEST_SELECT_EXPORT_DIR = 0x434D424B

    fun launch(activity: Activity, logTag: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        try {
            activity.startActivityForResult(intent, REQUEST_SELECT_EXPORT_DIR)
        } catch (throwable: ActivityNotFoundException) {
            ModuleFileLogger.e(logTag, "No directory picker available", throwable)
            Toast.makeText(activity, "系统文件夹选择器不可用", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        logTag: String,
    ): Boolean {
        if (requestCode != REQUEST_SELECT_EXPORT_DIR) {
            return false
        }
        if (resultCode != Activity.RESULT_OK) {
            return true
        }
        val uri = data?.data ?: run {
            Toast.makeText(activity, "未选择导出路径", Toast.LENGTH_SHORT).show()
            return true
        }
        persistPermission(activity, uri, data.flags, logTag)
        val saved = ChapterBackupConfigStore.rememberExportTreeUri(activity, uri.toString())
        Toast.makeText(
            activity,
            if (saved) "章节导出路径已保存" else "路径保存失败，请查看日志",
            Toast.LENGTH_SHORT,
        ).show()
        ModuleFileLogger.i(logTag, "Chapter backup export tree saved: $uri")
        return true
    }

    private fun persistPermission(activity: Activity, uri: Uri, flags: Int, logTag: String) {
        val persistable = flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        runCatching {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                persistable.takeIf { it != 0 } ?: (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    ),
            )
        }.onFailure { throwable ->
            ModuleFileLogger.w(logTag, "Failed to persist chapter backup tree permission", throwable)
        }
    }
}
