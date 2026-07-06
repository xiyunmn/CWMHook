package com.xiyunmn.cwmhook.feature.chapterbackup

import android.content.Context
import android.view.View
import com.xiyunmn.cwmhook.core.hostui.HostSkinResolver

internal object ChapterBackupSkinBridge {
    fun color(context: Context, name: String, fallback: Int): Int {
        return HostSkinResolver.color(context, name, fallback)
    }

    fun drawableId(context: Context, name: String): Int {
        return HostSkinResolver.drawableId(context, name)
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

}
