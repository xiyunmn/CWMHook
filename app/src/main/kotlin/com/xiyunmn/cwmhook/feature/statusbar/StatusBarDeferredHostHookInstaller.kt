package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.view.View
import android.view.Window
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

internal class StatusBarDeferredHostHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val applyWindow: (Window, String, Boolean, String?) -> Unit,
    private val updateReaderMenuSurface: (Window, Int?) -> Unit,
    private val captureBookDetailHero: (Activity, View) -> Unit,
    private val updateBookDetailScroll: (Activity, Int) -> Boolean,
    private val onBookDetailResume: (Activity) -> Unit,
    private val onBookDetailPause: (Activity) -> Unit,
    private val onBookDetailDestroy: (Activity) -> Unit,
    private val scheduleApplyForPageView: (View, String) -> Unit,
    private val scheduleKnownWindows: (String) -> Unit,
    private val applyFragmentWindow: (Any?, Method, Method?, String) -> Unit,
    private val logTag: String,
) {
    private var fragmentHooksInstalled = false
    private var viewPagerHooksInstalled = false
    private var skinHooksInstalled = false
    private var readerMenuHooksInstalled = false
    private var bookDetailHooksInstalled = false
    private val hookHelper = StatusBarHostHookHelper(logTag)
    private val fragmentHooks = StatusBarFragmentLifecycleHookInstaller(
        applyFragmentWindow = applyFragmentWindow,
        hookHelper = hookHelper,
        logTag = logTag,
    )
    private val viewPagerHooks = StatusBarViewPagerHookInstaller(
        scheduleApplyForPageView = scheduleApplyForPageView,
        hookHelper = hookHelper,
        logTag = logTag,
    )
    private val skinHooks = StatusBarSkinChangeHookInstaller(
        scheduleKnownWindows = scheduleKnownWindows,
        hookHelper = hookHelper,
        logTag = logTag,
    )
    private val readerMenuHooks = StatusBarReaderMenuHookInstaller(
        windowRegistry = windowRegistry,
        updateReaderMenuSurface = updateReaderMenuSurface,
        logTag = logTag,
    )
    private val bookDetailHooks = StatusBarBookDetailHookInstaller(
        windowRegistry = windowRegistry,
        applyWindow = applyWindow,
        captureBookDetailHero = captureBookDetailHero,
        updateBookDetailScroll = updateBookDetailScroll,
        onBookDetailResume = onBookDetailResume,
        onBookDetailPause = onBookDetailPause,
        onBookDetailDestroy = onBookDetailDestroy,
        hookHelper = hookHelper,
        logTag = logTag,
    )

    fun install(module: XposedModule, classLoader: ClassLoader) {
        installFragmentHooks(module, classLoader)
        installSkinHooks(module, classLoader)
        installReaderMenuHooks(module, classLoader)
        installBookDetailHooks(module, classLoader)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        if (!fragmentHooksInstalled) {
            ModuleFileLogger.i(logTag, "Retry fragment hooks: $reason")
            installFragmentHooks(module, classLoader)
        }
        if (!skinHooksInstalled) {
            ModuleFileLogger.i(logTag, "Retry skin hooks: $reason")
            installSkinHooks(module, classLoader)
        }
        if (!readerMenuHooksInstalled) {
            ModuleFileLogger.i(logTag, "Retry reader menu hooks: $reason")
            installReaderMenuHooks(module, classLoader)
        }
        if (!bookDetailHooksInstalled) {
            ModuleFileLogger.i(logTag, "Retry book detail hooks: $reason")
            installBookDetailHooks(module, classLoader)
        }
    }

    private fun installFragmentHooks(module: XposedModule, classLoader: ClassLoader) {
        if (fragmentHooksInstalled) {
            return
        }
        fragmentHooksInstalled = fragmentHooks.install(module, classLoader)
    }

    private fun installViewPagerHooks(module: XposedModule, classLoader: ClassLoader) {
        if (viewPagerHooksInstalled) {
            return
        }
        viewPagerHooksInstalled = viewPagerHooks.install(module, classLoader)
    }

    private fun installSkinHooks(module: XposedModule, classLoader: ClassLoader) {
        if (skinHooksInstalled) {
            return
        }
        skinHooksInstalled = skinHooks.install(module, classLoader)
    }

    private fun installReaderMenuHooks(module: XposedModule, classLoader: ClassLoader) {
        if (readerMenuHooksInstalled) {
            return
        }
        readerMenuHooksInstalled = readerMenuHooks.install(module, classLoader)
    }

    private fun installBookDetailHooks(module: XposedModule, classLoader: ClassLoader) {
        if (bookDetailHooksInstalled) return
        bookDetailHooksInstalled = bookDetailHooks.install(module, classLoader)
    }
}
