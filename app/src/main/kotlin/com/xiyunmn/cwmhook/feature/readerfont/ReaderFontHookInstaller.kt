package com.xiyunmn.cwmhook.feature.readerfont

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule

internal class ReaderFontHookInstaller(
    private val logTag: String,
) {
    private val typefaceProvider = ReaderFontTypefaceProvider(logTag)
    private val settingsInjector = ReaderFontSettingsInjector(typefaceProvider, logTag)
    private var fontHookInstalled = false
    private var textTypeInitHookInstalled = false
    private var textTypeChooseHookInstalled = false
    private var textTypeResultHookInstalled = false
    private var readerSettingHookInstalled = false

    fun install(module: XposedModule, classLoader: ClassLoader) {
        hookFontProvider(module, classLoader)
        hookTextTypeActivity(module, classLoader)
        hookActivityResult(module)
        hookReaderTextSetting(module, classLoader)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        if (
            !fontHookInstalled ||
            !textTypeInitHookInstalled ||
            !textTypeChooseHookInstalled ||
            !textTypeResultHookInstalled ||
            !readerSettingHookInstalled
        ) {
            ModuleFileLogger.i(logTag, "Retry reader font hooks: $reason")
        }
        install(module, classLoader)
    }

    fun startFontImport(activity: Activity) {
        settingsInjector.startFontPicker(activity)
    }

    private fun hookFontProvider(module: XposedModule, classLoader: ClassLoader) {
        if (fontHookInstalled) {
            return
        }
        val fontClass = XposedCompat.findClassOrNull(CiweiMaoClasses.COUSTM_FONT, classLoader) ?: run {
            ModuleFileLogger.i(logTag, "CoustmFont not visible yet, font hook deferred")
            return
        }
        val method = runCatching {
            fontClass.getDeclaredMethod("setFont", Context::class.java).also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(logTag, "CoustmFont.setFont(Context) not found", throwable)
            return
        }
        val hooked = XposedCompat.interceptProtective(module, method, "$logTag.CoustmFont.setFont") { chain ->
            val context = chain.getArg(0) as? Context
            val custom = context?.let { typefaceProvider.currentCustomTypeface(it) }
            custom ?: chain.proceed()
        }
        if (hooked) {
            fontHookInstalled = true
            ModuleFileLogger.i(logTag, "Reader font provider hook installed")
        }
    }

    private fun hookTextTypeActivity(module: XposedModule, classLoader: ClassLoader) {
        val activityClass = XposedCompat.findClassOrNull(CiweiMaoClasses.READER_TEXT_TYPE_ACTIVITY, classLoader) ?: run {
            ModuleFileLogger.i(logTag, "TextTypeActivity not visible yet, UI hooks deferred")
            return
        }
        if (!textTypeInitHookInstalled) {
            val initView = runCatching {
                activityClass.getDeclaredMethod("initView").also { it.isAccessible = true }
            }.getOrElse { throwable ->
                ModuleFileLogger.e(logTag, "TextTypeActivity.initView not found", throwable)
                return
            }
            val hooked = XposedCompat.hookAfter(module, initView, "$logTag.TextTypeActivity.initView") { chain ->
                (chain.thisObject as? Activity)?.let { settingsInjector.inject(it, activityClass) }
            }
            if (hooked) {
                textTypeInitHookInstalled = true
                ModuleFileLogger.i(logTag, "TextTypeActivity init hook installed")
            }
        }
        if (!textTypeChooseHookInstalled) {
            val setChoseImage = runCatching {
                activityClass.getDeclaredMethod("setChoseImage").also { it.isAccessible = true }
            }.getOrElse { throwable ->
                ModuleFileLogger.e(logTag, "TextTypeActivity.setChoseImage not found", throwable)
                return
            }
            val hooked = XposedCompat.hookAfter(module, setChoseImage, "$logTag.TextTypeActivity.setChoseImage") { chain ->
                (chain.thisObject as? Activity)?.let { settingsInjector.updateCustomChecks(it) }
            }
            if (hooked) {
                textTypeChooseHookInstalled = true
                ModuleFileLogger.i(logTag, "TextTypeActivity choose hook installed")
            }
        }
    }

    private fun hookActivityResult(module: XposedModule) {
        if (textTypeResultHookInstalled) {
            return
        }
        val method = Activity::class.java.getDeclaredMethod(
            "onActivityResult",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Intent::class.java,
        ).also { it.isAccessible = true }
        val hooked = XposedCompat.hookAfter(module, method, "$logTag.Activity.onActivityResult") { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val requestCode = chain.getArg(0) as? Int ?: return@hookAfter
            val resultCode = chain.getArg(1) as? Int ?: return@hookAfter
            val data = chain.getArg(2) as? Intent
            if (activity.javaClass.name == CiweiMaoClasses.READER_TEXT_TYPE_ACTIVITY) {
                settingsInjector.handleActivityResult(
                    activity = activity,
                    activityClass = activity.javaClass,
                    requestCode = requestCode,
                    resultCode = resultCode,
                    data = data,
                )
            } else {
                settingsInjector.handleImportResult(activity, requestCode, resultCode, data)
            }
        }
        if (hooked) {
            textTypeResultHookInstalled = true
            ModuleFileLogger.i(logTag, "TextTypeActivity result hook installed")
        }
    }

    private fun hookReaderTextSetting(module: XposedModule, classLoader: ClassLoader) {
        if (readerSettingHookInstalled) {
            return
        }
        val layoutClass = XposedCompat.findClassOrNull(CiweiMaoClasses.READER_TEXT_SETTING_LAYOUT, classLoader) ?: run {
            ModuleFileLogger.i(logTag, "ReaderTextSettingLayout not visible yet, text label hook deferred")
            return
        }
        val method = runCatching {
            layoutClass.getDeclaredMethod("dgInit").also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(logTag, "ReaderTextSettingLayout.dgInit not found", throwable)
            return
        }
        val hooked = XposedCompat.hookAfter(module, method, "$logTag.ReaderTextSettingLayout.dgInit") { chain ->
            settingsInjector.updateReaderSettingLabel(chain.thisObject)
        }
        if (hooked) {
            readerSettingHookInstalled = true
            ModuleFileLogger.i(logTag, "ReaderTextSettingLayout label hook installed")
        }
    }
}
