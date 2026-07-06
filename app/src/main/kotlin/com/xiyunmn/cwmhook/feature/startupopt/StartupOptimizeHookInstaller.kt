package com.xiyunmn.cwmhook.feature.startupopt

import android.app.Activity
import android.os.Handler
import com.xiyunmn.cwmhook.config.startupopt.StartupOptimizeConfig
import com.xiyunmn.cwmhook.config.startupopt.StartupOptimizeConfigStore
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

object StartupOptimizeHookInstaller {
    private const val TAG = "CWMHook.StartupOptimize"
    private const val THIRD_PARTY_SPLASH_MESSAGE = 12

    private val continuedWelcomeActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private val skippedAdvertisementActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    private var splashHooked = false
    private var welcomeDelayedMessageHooked = false
    private var welcomeSplashHooked = false
    private var welcomePrefetchHooked = false
    private var mainPrefetchHooked = false
    private var advertisementHooked = false
    @Volatile
    private var cachedConfig: StartupOptimizeConfig? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        installSplashHook(module, classLoader)
        installWelcomeDelayedMessageHook(module)
        installWelcomeSplashHook(module, classLoader)
        installStartPagePrefetchHooks(module, classLoader)
        installAdvertisementFallbackHook(module, classLoader)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        if (allHooksInstalled()) {
            return
        }
        ModuleFileLogger.i(TAG, "Retry startup optimize hooks: $reason")
        install(module, classLoader)
    }

    private fun installSplashHook(module: XposedModule, classLoader: ClassLoader) {
        if (splashHooked) {
            return
        }
        val splashClass = findHostClass(CiweiMaoClasses.SPLASH_ACTIVITY, classLoader) ?: return
        val method = declaredMethod(splashClass, "goAdvSelf") ?: return
        val hooked = XposedCompat.interceptProtective(module, method, "$TAG.SplashActivity.goAdvSelf") { chain ->
            val activity = chain.thisObject as? Activity
            val config = activity?.startupOptimizeConfig()
            if (config?.shouldSkipSelfSplash() == true) {
                ModuleFileLogger.i(TAG, "Self splash skipped before AdvertisementActivity")
                false
            } else {
                chain.proceed()
            }
        }
        if (hooked) {
            splashHooked = true
            ModuleFileLogger.i(TAG, "SplashActivity.goAdvSelf hook installed")
        }
    }

    private fun installWelcomeDelayedMessageHook(module: XposedModule) {
        if (welcomeDelayedMessageHooked) {
            return
        }
        val method = runCatching {
            Handler::class.java.getDeclaredMethod(
                "sendEmptyMessageDelayed",
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
            )
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "Handler.sendEmptyMessageDelayed not found", throwable)
            return
        }
        val hooked = XposedCompat.interceptProtective(
            module,
            method,
            "$TAG.WelcomeActivity.thirdPartyDelay",
        ) { chain ->
            val handler = chain.thisObject as? Handler
            val what = chain.getArg(0) as? Int
            if (handler?.javaClass?.name == CiweiMaoClasses.WELCOME_ACTIVITY_HANDLER &&
                what == THIRD_PARTY_SPLASH_MESSAGE
            ) {
                val activity = outerActivity(handler)
                val config = activity?.startupOptimizeConfig()
                if (config?.shouldSkipThirdPartySplash() == true) {
                    val delay = chain.getArg(1) as? Long
                    continueWelcomeToMain(activity, "skip third-party splash delay=$delay")
                    true
                } else {
                    chain.proceed()
                }
            } else {
                chain.proceed()
            }
        }
        if (hooked) {
            welcomeDelayedMessageHooked = true
            ModuleFileLogger.i(TAG, "WelcomeActivity third-party delay hook installed")
        }
    }

    private fun installWelcomeSplashHook(module: XposedModule, classLoader: ClassLoader) {
        if (welcomeSplashHooked) {
            return
        }
        val welcomeClass = findHostClass(CiweiMaoClasses.WELCOME_ACTIVITY, classLoader) ?: return
        val method = declaredMethod(welcomeClass, "showAdvTob") ?: return
        val hooked = XposedCompat.interceptProtective(module, method, "$TAG.WelcomeActivity.showAdvTob") { chain ->
            val activity = chain.thisObject as? Activity
            val config = activity?.startupOptimizeConfig()
            if (activity != null && config?.shouldSkipThirdPartySplash() == true) {
                continueWelcomeToMain(activity, "skip showAdvTob")
                null
            } else {
                chain.proceed()
            }
        }
        if (hooked) {
            welcomeSplashHooked = true
            ModuleFileLogger.i(TAG, "WelcomeActivity.showAdvTob hook installed")
        }
    }

    private fun installStartPagePrefetchHooks(module: XposedModule, classLoader: ClassLoader) {
        if (!welcomePrefetchHooked) {
            findHostClass(CiweiMaoClasses.WELCOME_ACTIVITY, classLoader)
                ?.let { declaredMethod(it, "getStartImg") }
                ?.let { method ->
                    val hooked = XposedCompat.interceptProtective(
                        module,
                        method,
                        "$TAG.WelcomeActivity.getStartImg",
                    ) { chain ->
                        val activity = chain.thisObject as? Activity
                        val config = activity?.startupOptimizeConfig()
                        if (config?.shouldDisableStartPagePrefetch() == true) {
                            ModuleFileLogger.i(TAG, "WelcomeActivity start page prefetch skipped")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    if (hooked) {
                        welcomePrefetchHooked = true
                        ModuleFileLogger.i(TAG, "WelcomeActivity.getStartImg hook installed")
                    }
                }
        }

        if (!mainPrefetchHooked) {
            findHostClass(CiweiMaoClasses.MAIN_FRAME_ACTIVITY, classLoader)
                ?.let { declaredMethod(it, "getStartImg") }
                ?.let { method ->
                    val hooked = XposedCompat.interceptProtective(
                        module,
                        method,
                        "$TAG.MainFrameActivity.getStartImg",
                    ) { chain ->
                        val activity = chain.thisObject as? Activity
                        val config = activity?.startupOptimizeConfig()
                        if (config?.shouldDisableStartPagePrefetch() == true) {
                            ModuleFileLogger.i(TAG, "MainFrameActivity start page prefetch skipped")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    if (hooked) {
                        mainPrefetchHooked = true
                        ModuleFileLogger.i(TAG, "MainFrameActivity.getStartImg hook installed")
                    }
                }
        }
    }

    private fun installAdvertisementFallbackHook(module: XposedModule, classLoader: ClassLoader) {
        if (advertisementHooked) {
            return
        }
        val advertisementClass = findHostClass(CiweiMaoClasses.ADVERTISEMENT_ACTIVITY, classLoader) ?: return
        val method = declaredMethod(advertisementClass, "onResume") ?: return
        val hooked = XposedCompat.hookAfter(module, method, "$TAG.AdvertisementActivity.onResume") { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val config = activity.startupOptimizeConfig()
            if (!config.shouldSkipAdvertisementActivity() || activity.isFinishing) {
                return@hookAfter
            }
            if (!skippedAdvertisementActivities.add(activity)) {
                return@hookAfter
            }
            val handler = declaredField(activity.javaClass, "handler")?.get(activity) as? Handler
            if (handler != null) {
                handler.removeMessages(0)
                handler.sendEmptyMessage(0)
                ModuleFileLogger.i(TAG, "AdvertisementActivity fallback skipped through host handler")
            } else {
                ModuleFileLogger.w(TAG, "AdvertisementActivity handler unavailable, fallback skip ignored")
            }
        }
        if (hooked) {
            advertisementHooked = true
            ModuleFileLogger.i(TAG, "AdvertisementActivity fallback hook installed")
        }
    }

    private fun continueWelcomeToMain(activity: Activity, reason: String) {
        if (activity.isFinishing) {
            return
        }
        if (!continuedWelcomeActivities.add(activity)) {
            ModuleFileLogger.throttled(
                key = "$TAG.continue.${activity.javaClass.name}",
                intervalMs = 30_000L,
                priority = android.util.Log.INFO,
                tag = TAG,
                message = "WelcomeActivity continue ignored, already handled: reason=$reason",
            )
            return
        }
        val method = findNoArgMethod(activity.javaClass, "goMain")
        if (method == null) {
            ModuleFileLogger.w(TAG, "WelcomeActivity.goMain unavailable: reason=$reason")
            return
        }
        runCatching {
            method.invoke(activity)
            ModuleFileLogger.i(TAG, "WelcomeActivity continued to main: reason=$reason")
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "WelcomeActivity continue failed: reason=$reason", throwable)
        }
    }

    private fun Activity.startupOptimizeConfig(): StartupOptimizeConfig {
        cachedConfig?.let { return it }
        return StartupOptimizeConfigStore.readLocal(this).also { cachedConfig = it }
    }

    private fun StartupOptimizeConfig.shouldSkipSelfSplash(): Boolean {
        return enabled && skipSelfSplash
    }

    private fun StartupOptimizeConfig.shouldSkipThirdPartySplash(): Boolean {
        return enabled && skipThirdPartySplash
    }

    private fun StartupOptimizeConfig.shouldDisableStartPagePrefetch(): Boolean {
        return enabled && disableStartPagePrefetch
    }

    private fun StartupOptimizeConfig.shouldSkipAdvertisementActivity(): Boolean {
        return enabled && skipAdvertisementActivity
    }

    private fun findHostClass(name: String, classLoader: ClassLoader): Class<*>? {
        return XposedCompat.findClassOrNull(name, classLoader).also { clazz ->
            if (clazz == null) {
                ModuleFileLogger.i(TAG, "Host class not visible yet: $name")
            }
        }
    }

    private fun declaredMethod(clazz: Class<*>, name: String): Method? {
        return runCatching {
            clazz.getDeclaredMethod(name).also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "Method not found: ${clazz.name}.$name", throwable)
            null
        }
    }

    private fun findNoArgMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(name).also { it.isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }

    private fun declaredField(clazz: Class<*>, name: String): Field? {
        return runCatching {
            clazz.getDeclaredField(name).also { it.isAccessible = true }
        }.getOrNull()
    }

    private fun outerActivity(handler: Handler): Activity? {
        return declaredField(handler.javaClass, "this$0")?.get(handler) as? Activity
    }

    private fun allHooksInstalled(): Boolean {
        return splashHooked &&
            welcomeDelayedMessageHooked &&
            welcomeSplashHooked &&
            welcomePrefetchHooked &&
            mainPrefetchHooked &&
            advertisementHooked
    }
}
