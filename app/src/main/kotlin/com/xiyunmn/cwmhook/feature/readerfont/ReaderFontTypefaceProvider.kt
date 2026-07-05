package com.xiyunmn.cwmhook.feature.readerfont

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import java.io.File
import java.util.Locale

internal class ReaderFontTypefaceProvider(
    private val logTag: String,
) {
    private val cache = LinkedHashMap<String, Typeface>()

    fun isEnabled(context: Context): Boolean {
        return ReaderFontConfigStore.isEnabled(context)
    }

    fun currentCustomTypeface(context: Context): Typeface? {
        if (!isEnabled(context)) {
            return null
        }
        val path = currentTextTypePath(context)
        if (!isCustomFontPath(path)) {
            return null
        }
        return load(path)
    }

    fun load(path: String): Typeface? {
        val file = File(path)
        if (!file.isFile || !file.canRead() || !isFontFile(file.name)) {
            return null
        }
        cache[path]?.let { return it }
        return runCatching {
            Typeface.createFromFile(file).also { typeface ->
                cache[path] = typeface
                trimCache()
            }
        }.getOrElse { throwable ->
            ModuleFileLogger.throttled(
                key = "font-load-failed:$path",
                intervalMs = 2000L,
                priority = Log.WARN,
                tag = logTag,
                message = "Failed to load custom font: $path",
                throwable = throwable,
            )
            null
        }
    }

    fun validate(path: String): Boolean {
        return load(path) != null
    }

    fun currentTextTypePath(context: Context): String {
        return context.getSharedPreferences("front", Context.MODE_PRIVATE)
            .getString("textTypePath1", "syht.otf")
            .orEmpty()
    }

    fun currentTextType(context: Context): String {
        return context.getSharedPreferences("front", Context.MODE_PRIVATE)
            .getString("textType1", "jian")
            .orEmpty()
            .ifEmpty { "jian" }
    }

    fun isCustomFontPath(path: String): Boolean {
        return path.isNotBlank() &&
            path != "default" &&
            path != "syht.otf" &&
            path != "syst.otf" &&
            path != "sys_h.otf" &&
            isFontFile(path)
    }

    fun isFontFile(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc")
    }

    fun displayName(path: String): String {
        val name = File(path).name.ifBlank { path }
        return name.substringBeforeLast('.', name)
    }

    private fun trimCache() {
        while (cache.size > 8) {
            val iterator = cache.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }
}
