package com.xiyunmn.cwmhook.feature.chapterbackup

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View

internal object ChapterBackupSkinBridge {
    fun color(context: Context, name: String, fallback: Int): Int {
        val id = resourceId(context, name, "color")
        if (id == 0) {
            return fallback
        }
        currentResourceProvider(context)?.let { provider ->
            runCatching {
                provider.javaClass
                    .getMethod("f", Int::class.javaPrimitiveType, String::class.java)
                    .invoke(provider, id, name) as Int
            }.onSuccess { return it }
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.resources.getColor(id, context.theme)
            } else {
                @Suppress("DEPRECATION")
                context.resources.getColor(id)
            }
        }.getOrDefault(fallback)
    }

    fun drawableId(context: Context, name: String): Int {
        return resourceId(context, name, "drawable").takeIf { it != 0 }
            ?: resourceId(context, name, "mipmap")
    }

    fun applyAttr(view: View, attr: String, resId: Int) {
        if (resId == 0) {
            return
        }
        runCatching {
            val skinClass = Class.forName("org.qcode.qskinloader.k", false, view.context.classLoader)
            val binder = skinClass.getMethod("c", View::class.java).invoke(null, view)
            binder.javaClass
                .getMethod("d", String::class.java, Int::class.javaPrimitiveType)
                .invoke(binder, attr, resId)
            binder.javaClass
                .getMethod("c", Boolean::class.javaPrimitiveType)
                .invoke(binder, false)
        }
    }

    fun refreshTree(root: View) {
        runCatching {
            val skinClass = Class.forName("org.qcode.qskinloader.k", false, root.context.classLoader)
            val manager = skinClass.getMethod("a").invoke(null)
            manager.javaClass
                .getMethod("c", View::class.java, Boolean::class.javaPrimitiveType)
                .invoke(manager, root, true)
        }
    }

    fun neutralBackground(night: Boolean): Int {
        return if (night) 0xFF222222.toInt() else 0xFFF7F7F7.toInt()
    }

    fun neutralPanel(night: Boolean): Int {
        return if (night) 0xFF353535.toInt() else Color.WHITE
    }

    private fun resourceId(context: Context, name: String, type: String): Int {
        return context.resources.getIdentifier(name, type, context.packageName)
    }

    private fun currentResourceProvider(context: Context): Any? {
        return runCatching {
            val managerClass = Class.forName("org.qcode.qskinloader.o.d", false, context.classLoader)
            val manager = managerClass.getMethod("h").invoke(null)
            manager.javaClass.getMethod("i").invoke(manager)
        }.getOrNull()
    }
}
