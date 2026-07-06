package com.xiyunmn.cwmhook.core.hostui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.xiyunmn.cwmhook.host.CiweiMaoPackages

internal object HostSkinResolver {
    fun currentSkinKey(context: Context): String {
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val followSystem = settings.getBoolean("IsfollowNight", false)
        val isNight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && followSystem) {
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        } else {
            context.getSharedPreferences(CiweiMaoPackages.DEFAULT_PREF, Context.MODE_PRIVATE)
                .getBoolean("isNight", false)
        }
        return if (isNight) {
            "night"
        } else {
            settings.getString("skinType", "yellow") ?: "yellow"
        }
    }

    fun color(context: Context, name: String, fallback: Int): Int {
        return providerColor(context, name) ?: plainColor(context, name) ?: fallback
    }

    fun skinnedColor(context: Context, name: String, skinKey: String, fallback: Int): Int {
        providerColor(context, name)?.let { return it }
        skinnedNames(name, skinKey).forEach { candidate ->
            plainColor(context, candidate)?.let { return it }
        }
        return fallback
    }

    fun drawableId(context: Context, name: String): Int {
        return resourceId(context, name, "drawable").takeIf { it != 0 }
            ?: resourceId(context, name, "mipmap")
    }

    fun skinnedDrawableId(context: Context, name: String, skinKey: String): Int {
        skinnedNames(name, skinKey).forEach { candidate ->
            drawableId(context, candidate).takeIf { it != 0 }?.let { return it }
        }
        return 0
    }

    fun skinnedNames(name: String, skinKey: String): List<String> {
        return when (skinKey) {
            "night" -> listOf("${name}_night", name)
            "green", "pink" -> listOf("${name}_$skinKey", name)
            else -> listOf(name)
        }
    }

    private fun providerColor(context: Context, name: String): Int? {
        val id = resourceId(context, name, "color")
        if (id == 0) {
            return null
        }
        val provider = currentResourceProvider(context) ?: return null
        return runCatching {
            provider.javaClass
                .getMethod("f", Int::class.javaPrimitiveType, String::class.java)
                .invoke(provider, id, name) as Int
        }.getOrNull()
    }

    private fun plainColor(context: Context, name: String): Int? {
        val id = resourceId(context, name, "color")
        if (id == 0) {
            return null
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.resources.getColor(id, context.theme)
            } else {
                @Suppress("DEPRECATION")
                context.resources.getColor(id)
            }
        }.getOrNull()
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
