package com.xiyunmn.cwmhook.feature.rewardad

import android.app.Activity
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.HashMap
import java.util.WeakHashMap

internal object RewardAdSkipHookInstaller {
    private const val TAG = "CWMHook.RewardAdSkip"
    private const val CALLBACK_DELAY_MS = 120L
    private const val PLAY_END_AFTER_REWARD_MS = 40L
    private const val CLOSE_AFTER_REWARD_MS = 120L
    private const val SETTLEMENT_POLL_MS = 120L
    private const val SETTLEMENT_TIMEOUT_MS = 30_000L
    private const val PRELOAD_DELAY_MS = 350L

    private val syntheticAds = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val pendingSettlements = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private val missionPreloads = Collections.synchronizedMap(WeakHashMap<Activity, PreloadState>())

    @Volatile
    private var installed = false
    @Volatile
    private var missionHooksInstalled = false

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (installed && missionHooksInstalled) {
            return
        }
        val rewardAdClass = hostClass(CiweiMaoClasses.WM_REWARD_AD, classLoader) ?: return
        val rewardInfoClass = hostClass(CiweiMaoClasses.WM_REWARD_INFO, classLoader) ?: return
        val adInfoClass = hostClass(CiweiMaoClasses.WM_AD_INFO, classLoader) ?: return

        val methods = resolveMethods(rewardAdClass, rewardInfoClass, adInfoClass) ?: return
        val rewardInfoConstructor = rewardInfoConstructor(rewardInfoClass) ?: return
        if (!installed) {
            val hooked = XposedCompat.interceptProtective(
                module,
                methods.show,
                "$TAG.WMRewardAd.show",
            ) { chain ->
                val activity = chain.getArg(0) as? Activity
                if (activity == null || !isSupportedActivity(activity) || activity.isFinishing || activity.isDestroyed) {
                    return@interceptProtective chain.proceed()
                }
                // Temporary CTF compare switch:
                // create file /data/local/tmp/cwmhook_force_real_ad to force real ad playback.
                if (RewardAdProbe.forceRealAd()) {
                    RewardAdProbe.logSynthetic("show.forceReal", activity)
                    RewardAdProbe.markMode("REAL")
                    return@interceptProtective chain.proceed()
                }
                val rewardAd = chain.thisObject
                val preloadState = missionPreloads[activity]
                if (preloadState != null && preloadState.rewardAd == null) {
                    preloadState.rewardAd = rewardAd
                }
                if (preloadState != null && !preloadState.claimRequested) {
                    RewardAdProbe.logSynthetic(
                        "preload.ready",
                        activity,
                        "chestType=${preloadState.chestType}",
                    )
                    return@interceptProtective true
                }

                RewardAdProbe.markMode("FORGED")
                RewardNetworkCapture.arm(
                    reason = "show-forged",
                    classLoader = activity.classLoader ?: activity.javaClass.classLoader,
                )
                val alreadyHandled = synchronized(syntheticAds) { syntheticAds.contains(rewardAd) }
                if (alreadyHandled) {
                    ModuleFileLogger.w(TAG, "Duplicate show suppressed: activity=${activity.javaClass.name}")
                    RewardAdProbe.logSynthetic("show.duplicate", activity)
                    return@interceptProtective true
                }
                val adInfo = runCatching { methods.getAdInfo.invoke(rewardAd) }
                    .onFailure { throwable -> ModuleFileLogger.e(TAG, "Failed to read loaded AdInfo", throwable) }
                    .getOrNull()
                if (adInfo == null) {
                    ModuleFileLogger.w(TAG, "Loaded AdInfo unavailable, falling back to real ad")
                    RewardAdProbe.logSynthetic("show.fallback.noAdInfo", activity)
                    return@interceptProtective chain.proceed()
                }

                // Prefer full SDK reward path (adapterDidStartPlayingAd / adapterDidRewardAd),
                // which is where WindMill S2S reward reporting happens.
                val sdkPath = dispatchViaController(rewardAd, adInfo, activity)
                if (sdkPath) {
                    synchronized(syntheticAds) { syntheticAds.add(rewardAd) }
                    missionPreloads.remove(activity)
                    ModuleFileLogger.i(
                        TAG,
                        "Reward ad skipped via SDK controller path: activity=${activity.javaClass.name} network=${networkName(adInfo)}",
                    )
                    RewardAdProbe.logSynthetic(
                        "show.skipped.sdkPath",
                        activity,
                        "network=${networkName(adInfo)} adInfo=${RewardAdProbe.describeAdInfo(adInfo)}",
                    )
                    return@interceptProtective true
                }

                // Fallback: host-listener-only forge (no S2S).
                val rewardInfo = forgeRewardInfo(rewardInfoConstructor, adInfo)
                if (rewardInfo == null) {
                    RewardAdProbe.logSynthetic("show.fallback.rewardInfo", activity)
                    return@interceptProtective chain.proceed()
                }
                RewardAdProbe.logSynthetic(
                    "rewardInfo.forged",
                    activity,
                    RewardAdProbe.describeRewardInfo(rewardInfo),
                )

                val started = runCatching {
                    methods.onPlayStart.invoke(rewardAd, adInfo, false)
                }.onFailure { throwable ->
                    ModuleFileLogger.e(TAG, "Failed to dispatch synthetic play start", throwable)
                }.isSuccess
                if (!started) {
                    RewardAdProbe.logSynthetic("show.fallback.playStart", activity, "network=${networkName(adInfo)}")
                    return@interceptProtective chain.proceed()
                }
                RewardAdProbe.logSynthetic(
                    "playStart.dispatched.listenerOnly",
                    activity,
                    "network=${networkName(adInfo)} adInfo=${RewardAdProbe.describeAdInfo(adInfo)}",
                )

                synchronized(syntheticAds) { syntheticAds.add(rewardAd) }
                missionPreloads.remove(activity)
                val completionPosted = ModuleViewTaskRegistry.post(
                    activity.window.decorView,
                    CALLBACK_DELAY_MS,
                ) {
                    dispatchCompletion(activity, rewardAd, adInfo, rewardInfo, methods)
                }
                if (!completionPosted) {
                    ModuleFileLogger.w(TAG, "Completion post rejected, dispatching immediately")
                    dispatchCompletion(activity, rewardAd, adInfo, rewardInfo, methods)
                }
                ModuleFileLogger.i(
                    TAG,
                    "Reward ad display skipped (listener-only): activity=${activity.javaClass.name} network=${networkName(adInfo)}",
                )
                RewardAdProbe.logSynthetic("show.skipped.listenerOnly", activity, "network=${networkName(adInfo)}")
                true
            }
            if (hooked) {
                installed = true
                ModuleFileLogger.i(TAG, "WMRewardAd.show hook installed")
            }
        }
        installMissionPreloadHooks(module, classLoader, methods)
    }

    fun isInstalled(): Boolean = installed

    fun clearRuntimeState() {
        synchronized(syntheticAds) { syntheticAds.clear() }
        synchronized(pendingSettlements) { pendingSettlements.clear() }
        missionPreloads.clear()
    }

    private fun installMissionPreloadHooks(
        module: XposedModule,
        classLoader: ClassLoader,
        methods: RewardMethods,
    ) {
        if (missionHooksInstalled) {
            return
        }
        val missionClass = hostClass(CiweiMaoClasses.MISSION_ACTIVITY, classLoader) ?: return
        val allMissionTaskClass = hostClass(CiweiMaoClasses.ALL_MISSION_LIST_TASK, classLoader) ?: return
        val playAdv = findMethod(missionClass, "playAdv") ?: return
        val allMissionGetData = findMethod(allMissionTaskClass, "getData") ?: return

        val playHooked = XposedCompat.interceptProtective(module, playAdv, "$TAG.MissionActivity.playAdv.fast") { chain ->
            val activity = chain.thisObject as? Activity ?: return@interceptProtective chain.proceed()
            val missionBoxData = chain.getArg(2) ?: return@interceptProtective chain.proceed()
            if (activity.isFinishing || activity.isDestroyed) {
                return@interceptProtective chain.proceed()
            }
            writeField(activity, "cacheImg", chain.getArg(0))
            writeField(activity, "cacheDivider", chain.getArg(1))
            writeField(activity, "cacheMissionBoxData", missionBoxData)
            writeBooleanField(activity, "isGetAdvBouns", false)
            writeBooleanField(activity, "isCreateAdvBouns", false)
            RewardAdProbe.markMode("FORGED")
            RewardNetworkCapture.arm(
                reason = "playAdv-fast",
                classLoader = activity.classLoader ?: activity.javaClass.classLoader,
            )
            RewardAdProbe.snapshotAssetsBeforePlay(activity)

            if (tryUsePreloaded(activity, missionBoxData, methods)) {
                return@interceptProtective null
            }
            if (startFreshMissionLoad(activity, missionBoxData)) {
                return@interceptProtective null
            }
            chain.proceed()
        }

        val listHooked = XposedCompat.hookAfter(module, allMissionGetData, "$TAG.AllMissionListTask.preload") { chain ->
            val activity = readField(chain.thisObject, "context") as? Activity ?: return@hookAfter
            if (activity.javaClass.name != CiweiMaoClasses.MISSION_ACTIVITY || activity.isFinishing || activity.isDestroyed) {
                return@hookAfter
            }
            val missionBoxData = selectPreloadBox(chain.getArg(0)) ?: run {
                missionPreloads.remove(activity)
                return@hookAfter
            }
            val chestType = chestTypeOf(missionBoxData) ?: return@hookAfter
            val current = missionPreloads[activity]
            if (current != null && current.chestType == chestType && isRewardAdReady(current.rewardAd)) {
                return@hookAfter
            }
            ModuleViewTaskRegistry.post(activity.window.decorView, PRELOAD_DELAY_MS) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    startPreload(activity, missionBoxData)
                }
            }
        }
        if (playHooked && listHooked) {
            missionHooksInstalled = true
            ModuleFileLogger.i(TAG, "Mission reward ad preload hooks installed")
        }
    }

    private fun tryUsePreloaded(
        activity: Activity,
        missionBoxData: Any,
        methods: RewardMethods,
    ): Boolean {
        val chestType = chestTypeOf(missionBoxData) ?: return false
        val preloadState = missionPreloads[activity] ?: return false
        if (preloadState.chestType != chestType) {
            missionPreloads.remove(activity)
            return false
        }
        preloadState.claimRequested = true
        val rewardAd = preloadState.rewardAd ?: readField(activity, "rewardVideoAd")
        if (rewardAd == null) {
            RewardAdProbe.logSynthetic("preload.awaitLoad", activity, "chestType=$chestType")
            return true
        }
        if (!isRewardAdReady(rewardAd)) {
            RewardAdProbe.logSynthetic("preload.awaitReady", activity, "chestType=$chestType")
            return true
        }
        preloadState.rewardAd = rewardAd
        val shown = runCatching {
            methods.show.invoke(rewardAd, activity, HashMap<String, String>()) as? Boolean
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "Failed to show preloaded reward ad", throwable)
        }.getOrDefault(false) == true
        if (!shown) {
            missionPreloads.remove(activity)
            RewardAdProbe.logSynthetic("preload.showFailed", activity, "chestType=$chestType")
            return false
        }
        RewardAdProbe.logSynthetic("preload.claimed", activity, "chestType=$chestType")
        return true
    }

    private fun startFreshMissionLoad(activity: Activity, missionBoxData: Any): Boolean {
        if (readField(activity, "adT")?.toString().isNullOrBlank()) {
            RewardAdProbe.logSynthetic("fastLoad.noAdType", activity)
            return true
        }
        missionPreloads.remove(activity)
        writeField(activity, "cacheMissionBoxData", missionBoxData)
        return invokeLoadTobid(activity, reason = "fresh")
    }

    private fun startPreload(activity: Activity, missionBoxData: Any): Boolean {
        if (readField(activity, "adT")?.toString().isNullOrBlank()) {
            return false
        }
        val chestType = chestTypeOf(missionBoxData) ?: return false
        val current = missionPreloads[activity]
        if (current != null && current.chestType == chestType) {
            if (current.claimRequested || isRewardAdReady(current.rewardAd)) {
                return true
            }
        }
        val state = PreloadState(chestType = chestType)
        missionPreloads[activity] = state
        writeField(activity, "cacheMissionBoxData", missionBoxData)
        RewardAdProbe.logSynthetic("preload.start", activity, "chestType=$chestType")
        val started = invokeLoadTobid(activity, reason = "preload")
        if (!started) {
            missionPreloads.remove(activity)
        }
        return started
    }

    private fun invokeLoadTobid(activity: Activity, reason: String): Boolean {
        ensureTobidInitialized(activity)
        val loadTobid = findMethod(activity.javaClass, "loadTobid") ?: run {
            ModuleFileLogger.e(TAG, "MissionActivity.loadTobid not found")
            RewardAdProbe.logSynthetic("fastLoad.missingLoadTobid", activity, reason)
            return false
        }
        return runCatching {
            loadTobid.invoke(activity)
        }.onSuccess {
            RewardAdProbe.logSynthetic("fastLoad.loadTobid", activity, reason)
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "Failed to invoke MissionActivity.loadTobid", throwable)
            RewardAdProbe.logSynthetic("fastLoad.failed", activity, "$reason ${throwable.message.orEmpty()}")
        }.isSuccess
    }

    private fun ensureTobidInitialized(activity: Activity) {
        val classLoader = activity.classLoader ?: activity.javaClass.classLoader
        runCatching {
            val pushHelperClass = Class.forName(CiweiMaoClasses.PUSH_HELPER, false, classLoader)
            val getInstance = pushHelperClass.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            val initialized = findField(pushHelperClass, "isInitAD_tobid")?.getBoolean(instance) == true
            if (!initialized) {
                pushHelperClass.getMethod("initTob").invoke(null)
                ModuleFileLogger.i(TAG, "WindMill Tobid initialized before reward load")
            }
        }.onFailure { throwable ->
            ModuleFileLogger.w(TAG, "Failed to initialize WindMill Tobid before reward load", throwable)
        }
    }

    private fun selectPreloadBox(result: Any?): Any? {
        return runCatching {
            val value = invokeNoArg(result ?: return null, "getValue") ?: return null
            val week = invokeNoArg(value, "getWeek_task_info") ?: return null
            if (toInt(invokeNoArg(week, "getAccess")) != 1) {
                return null
            }
            val totalExp = toInt(invokeNoArg(week, "getTotal_exp"))
                ?: toInt(invokeNoArg(value, "getTotal_exp"))
                ?: 0
            val boxes = invokeNoArg(week, "getChest_info_map") as? Iterable<*> ?: return null
            boxes.firstOrNull { box ->
                box != null &&
                    toInt(invokeNoArg(box, "getOpened")) == 0 &&
                    (toInt(invokeNoArg(box, "getExp")) ?: Int.MAX_VALUE) <= totalExp
            }
        }.onFailure { throwable ->
            ModuleFileLogger.w(TAG, "Failed to select reward ad preload box", throwable)
        }.getOrNull()
    }

    private fun isRewardAdReady(rewardAd: Any?): Boolean {
        if (rewardAd == null) {
            return false
        }
        return runCatching {
            rewardAd.javaClass.methods
                .firstOrNull { it.name == "isReady" && it.parameterCount == 0 }
                ?.invoke(rewardAd) as? Boolean
        }.getOrDefault(false) == true
    }

    private fun chestTypeOf(missionBoxData: Any?): Int? {
        return toInt(missionBoxData?.let { invokeNoArg(it, "getChest_type") })
    }

    private fun toInt(value: Any?): Int? {
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> value?.toString()?.toIntOrNull()
        }
    }

    private fun dispatchCompletion(
        activity: Activity,
        rewardAd: Any,
        adInfo: Any,
        rewardInfo: Any,
        methods: RewardMethods,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        val completed = runCatching {
            methods.onReward.invoke(rewardAd, adInfo, rewardInfo)
            methods.onPlayEnd.invoke(rewardAd, adInfo)
            methods.onClosed.invoke(rewardAd, adInfo)
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "Failed to dispatch synthetic reward completion", throwable)
        }.isSuccess
        if (!completed) {
            RewardAdProbe.logSynthetic("completion.failed", activity)
            return
        }
        ModuleFileLogger.i(TAG, "Synthetic reward callbacks completed: activity=${activity.javaClass.name}")
        RewardAdProbe.logSynthetic(
            "completion.ok",
            activity,
            "network=${networkName(adInfo)} adInfo=${RewardAdProbe.describeAdInfo(adInfo)} rewardInfo=${RewardAdProbe.describeRewardInfo(rewardInfo)}",
        )
        if (activity.javaClass.name == CiweiMaoClasses.MISSION_ACTIVITY) {
            scheduleMissionSettlement(activity)
        }
    }

    private fun scheduleMissionSettlement(activity: Activity) {
        val accepted = synchronized(pendingSettlements) { pendingSettlements.add(activity) }
        if (!accepted) {
            ModuleFileLogger.w(TAG, "Mission settlement already pending")
            return
        }
        pollMissionSettlement(activity, android.os.SystemClock.uptimeMillis())
    }

    private fun pollMissionSettlement(activity: Activity, startedAt: Long) {
        if (activity.isFinishing || activity.isDestroyed || XposedCompat.isRetiring()) {
            finishSettlement(activity)
            return
        }
        val rewardReady = readBooleanField(activity, "isGetAdvBouns")
        val recordReady = readBooleanField(activity, "isCreateAdvBouns")
        if (rewardReady == true && recordReady == true) {
            finishSettlement(activity)
            // Host ad path only updates UI here. CreateAd(228) is the server-side grant.
            // Do NOT call openBox/GetWeekboxTask(227): that is the no-ad chest path and
            // returns herror2-Invalid padding on the ad path.
            val getBouns = findMethod(activity.javaClass, "getBouns")
            if (getBouns == null) {
                ModuleFileLogger.e(TAG, "MissionActivity.getBouns not found")
                RewardAdProbe.logSynthetic("settlement.missingGetBouns", activity)
                return
            }
            runCatching { getBouns.invoke(activity) }
                .onSuccess {
                    ModuleFileLogger.i(TAG, "Mission UI settled via getBouns after CreateAd")
                    RewardAdProbe.logSynthetic("settlement.invokedGetBouns", activity)
                    RewardAdProbe.refreshAssetsAfterGrant(activity)
                }
                .onFailure { throwable ->
                    if (isBenignMissionSettlementFailure(throwable)) {
                        ModuleFileLogger.w(TAG, "Mission reward settlement completed with host analytics failure")
                        RewardAdProbe.logSynthetic(
                            "settlement.hostAnalyticsFailed",
                            activity,
                            throwable.cause?.message.orEmpty(),
                        )
                        writeBooleanField(activity, "isGetAdvBouns", false)
                        writeBooleanField(activity, "isCreateAdvBouns", false)
                        RewardAdProbe.refreshAssetsAfterGrant(activity)
                        return
                    }
                    ModuleFileLogger.e(TAG, "Mission reward settlement failed", throwable)
                    RewardAdProbe.logSynthetic(
                        "settlement.getBounsFailed",
                        activity,
                        throwable.message.orEmpty(),
                    )
                }
            return
        }
        val elapsed = android.os.SystemClock.uptimeMillis() - startedAt
        if (elapsed >= SETTLEMENT_TIMEOUT_MS) {
            finishSettlement(activity)
            ModuleFileLogger.w(
                TAG,
                "Mission reward settlement timed out: rewardReady=$rewardReady recordReady=$recordReady",
            )
            RewardAdProbe.logSynthetic(
                "settlement.timeout",
                activity,
                "rewardReady=$rewardReady recordReady=$recordReady elapsedMs=$elapsed",
            )
            return
        }
        val posted = ModuleViewTaskRegistry.post(
            activity.window.decorView,
            SETTLEMENT_POLL_MS,
        ) {
            pollMissionSettlement(activity, startedAt)
        }
        if (!posted) {
            finishSettlement(activity)
            ModuleFileLogger.w(TAG, "Mission reward settlement poll rejected")
        }
    }

    private fun isBenignMissionSettlementFailure(throwable: Throwable): Boolean {
        val cause = (throwable as? java.lang.reflect.InvocationTargetException)?.cause ?: throwable.cause
        return cause is NullPointerException &&
            cause.stackTrace.any { frame ->
                frame.className == CiweiMaoClasses.SENSORS_UTILS &&
                    frame.methodName == "sendWeekBouns"
            }
    }

    private fun finishSettlement(activity: Activity) {
        synchronized(pendingSettlements) { pendingSettlements.remove(activity) }
    }

    private fun readBooleanField(target: Any, name: String): Boolean? {
        return findField(target.javaClass, name)?.let { field ->
            runCatching { field.getBoolean(target) }
                .onFailure { throwable -> ModuleFileLogger.e(TAG, "Failed to read field: $name", throwable) }
                .getOrNull()
        }
    }

    private fun writeBooleanField(target: Any, name: String, value: Boolean) {
        findField(target.javaClass, name)?.let { field ->
            runCatching { field.setBoolean(target, value) }
                .onFailure { throwable -> ModuleFileLogger.e(TAG, "Failed to write field: $name", throwable) }
        }
    }

    /**
     * Drive the full WindMill controller reward path so S2S reward report can fire:
     * set current AdInfo -> adapterDidStartPlayingAd -> adapterDidRewardAd.
     */
    private fun dispatchViaController(rewardAd: Any, adInfo: Any, activity: Activity): Boolean {
        return runCatching {
            val controller = findControllerForAdInfo(rewardAd, adInfo)
            if (controller == null) {
                RewardAdProbe.logSynthetic("sdkPath.noController", activity)
                return false
            }
            val request = readField(rewardAd, "mRequest")
            val ready = findReadyStrategyAndAdapter(controller, adInfo, activity)
            if (ready == null) {
                RewardAdProbe.logSynthetic("sdkPath.noReadyAdapter", activity)
                return false
            }
            val (strategy, adapter) = ready

            val loadId = alignControllerRewardState(controller, request, strategy, adInfo, activity)

            val custom = hashMapOf<String, Any?>(
                "rewardVerify" to true,
                "errorCode" to 0,
                "errorMsg" to null,
                "transId" to loadId,
            )
            runCatching {
                val method = strategy.javaClass.methods.firstOrNull {
                    it.name == "e" &&
                        it.parameterCount == 1 &&
                        Map::class.java.isAssignableFrom(it.parameterTypes[0])
                }
                method?.isAccessible = true
                method?.invoke(strategy, custom)
            }

            val startMethod = controller.javaClass.methods.firstOrNull {
                it.name == "adapterDidStartPlayingAd" && it.parameterCount == 3
            }?.also { it.isAccessible = true }
            val rewardMethod = controller.javaClass.methods.firstOrNull {
                it.name == "adapterDidRewardAd" && it.parameterCount == 3
            }?.also { it.isAccessible = true }
            val endMethod = controller.javaClass.methods.firstOrNull {
                it.name == "adapterDidPlayEndAd" && it.parameterCount == 2
            }?.also { it.isAccessible = true }
            val closeMethod = controller.javaClass.methods.firstOrNull {
                it.name == "adapterDidCloseAd" && it.parameterCount == 2
            }?.also { it.isAccessible = true }

            if (startMethod == null || rewardMethod == null) {
                RewardAdProbe.logSynthetic(
                    "sdkPath.missingMethods",
                    activity,
                    "start=${startMethod != null} reward=${rewardMethod != null}",
                )
                return false
            }

            // 1) start playing -> host createAd via onVideoAdPlayStart
            startMethod.invoke(controller, adapter, strategy, emptyMap<String, String>())
            RewardAdProbe.logSynthetic(
                "sdkPath.startPlaying",
                activity,
                "adapter=${adapter.javaClass.simpleName} strategy=${strategy.javaClass.simpleName} loadId=$loadId",
            )

            // 2) Give CreateAd and WindMill reporting a short head start, then finish
            // with the same reward -> playEnd -> close order observed on real playback.
            val posted = ModuleViewTaskRegistry.post(activity.window.decorView, CALLBACK_DELAY_MS) {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@post
                }
                runCatching {
                    rewardMethod.invoke(controller, adapter, strategy, true)
                    RewardAdProbe.logSynthetic("sdkPath.rewarded", activity, "loadId=$loadId")
                    ModuleViewTaskRegistry.post(activity.window.decorView, PLAY_END_AFTER_REWARD_MS) {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            runCatching { endMethod?.invoke(controller, adapter, strategy) }
                        }
                    }
                    val closePosted = ModuleViewTaskRegistry.post(activity.window.decorView, CLOSE_AFTER_REWARD_MS) {
                        if (activity.isFinishing || activity.isDestroyed) {
                            return@post
                        }
                        closeMethod?.invoke(controller, adapter, strategy)
                        if (activity.javaClass.name == CiweiMaoClasses.MISSION_ACTIVITY) {
                            scheduleMissionSettlement(activity)
                        }
                    }
                    if (!closePosted) {
                        closeMethod?.invoke(controller, adapter, strategy)
                        if (activity.javaClass.name == CiweiMaoClasses.MISSION_ACTIVITY) {
                            scheduleMissionSettlement(activity)
                        }
                    }
                }.onFailure { throwable ->
                    ModuleFileLogger.e(TAG, "SDK controller reward dispatch failed", throwable)
                    RewardAdProbe.logSynthetic("sdkPath.rewardFailed", activity, throwable.message.orEmpty())
                }
            }
            if (!posted) {
                rewardMethod.invoke(controller, adapter, strategy, true)
                endMethod?.invoke(controller, adapter, strategy)
                closeMethod?.invoke(controller, adapter, strategy)
                RewardAdProbe.logSynthetic("sdkPath.rewarded.immediate", activity, "loadId=$loadId")
                if (activity.javaClass.name == CiweiMaoClasses.MISSION_ACTIVITY) {
                    scheduleMissionSettlement(activity)
                }
            }
            true
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "SDK controller path failed", throwable)
            RewardAdProbe.logSynthetic("sdkPath.exception", activity, throwable.message.orEmpty())
        }.getOrDefault(false)
    }

    private fun findControllerForAdInfo(rewardAd: Any, adInfo: Any): Any? {
        val candidates = listOfNotNull(
            readField(rewardAd, "nextController"),
            readField(rewardAd, "controller"),
        )
        if (candidates.isEmpty()) {
            return null
        }
        val targetLoadId = invokeNoArg(adInfo, "getLoadId")?.toString().orEmpty()
        val targetNetworkId = invokeNoArg(adInfo, "getNetworkId")?.toString().orEmpty()
        val targetNetworkPlacement = invokeNoArg(adInfo, "getNetworkPlacementId")?.toString().orEmpty()
        return candidates.firstOrNull { controller ->
            val currentAdInfo = invokeNoArg(controller, "d") ?: return@firstOrNull false
            val loadId = invokeNoArg(currentAdInfo, "getLoadId")?.toString().orEmpty()
            val networkId = invokeNoArg(currentAdInfo, "getNetworkId")?.toString().orEmpty()
            val networkPlacement = invokeNoArg(currentAdInfo, "getNetworkPlacementId")?.toString().orEmpty()
            (targetLoadId.isBlank() || loadId == targetLoadId) &&
                (targetNetworkId.isBlank() || networkId == targetNetworkId) &&
                (targetNetworkPlacement.isBlank() || networkPlacement == targetNetworkPlacement)
        } ?: candidates.first()
    }

    private fun alignControllerRewardState(
        controller: Any,
        request: Any?,
        strategy: Any,
        adInfo: Any,
        activity: Activity,
    ): String {
        val adInfoLoadId = invokeNoArg(adInfo, "getLoadId")?.toString().orEmpty()
        val controllerLoadId = readField(controller, "B")?.toString().orEmpty()
        val requestLoadId = request?.let { invokeNoArg(it, "getLoadId")?.toString().orEmpty() }.orEmpty()
        val strategyLoadId = invokeNoArg(strategy, "aC")?.toString().orEmpty()
        val loadId = listOf(controllerLoadId, strategyLoadId, requestLoadId, adInfoLoadId)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        RewardAdProbe.logSynthetic(
            "sdkPath.align.before",
            activity,
            "B=$controllerLoadId request=$requestLoadId strategy=$strategyLoadId adInfo=$adInfoLoadId chosen=$loadId",
        )

        if (loadId.isNotBlank()) {
            writeField(controller, "B", loadId)
            request?.let {
                invokeOneArg(it, "setLoadId", String::class.java, loadId)
                writeField(it, "loadId", loadId)
            }
            invokeOneArg(strategy, "n", String::class.java, loadId)
        }
        writeField(controller, "J", false)
        runCatching {
            val setActivity = controller.javaClass.methods.firstOrNull {
                it.name == "a" &&
                    it.parameterCount == 1 &&
                    Activity::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            setActivity?.isAccessible = true
            setActivity?.invoke(controller, activity)
        }.onFailure { throwable ->
            ModuleFileLogger.w(TAG, "Failed to attach controller activity", throwable)
        }

        // Ensure controller current AdInfo is set (host callbacks use controller.q).
        runCatching {
            val fill = controller.javaClass.methods.firstOrNull {
                it.name == "b" &&
                    it.parameterCount == 2 &&
                    it.parameterTypes[0].name.contains("WindMillAdRequest")
            }
            if (fill != null && request != null) {
                fill.isAccessible = true
                fill.invoke(controller, request, strategy)
            } else {
                writeField(controller, "q", adInfo)
            }
        }.onFailure { throwable ->
            ModuleFileLogger.w(TAG, "Failed to refresh controller AdInfo", throwable)
            writeField(controller, "q", adInfo)
        }

        val alignedControllerLoadId = readField(controller, "B")?.toString().orEmpty()
        val alignedRequestLoadId = request?.let { invokeNoArg(it, "getLoadId")?.toString().orEmpty() }.orEmpty()
        val alignedStrategyLoadId = invokeNoArg(strategy, "aC")?.toString().orEmpty()
        RewardAdProbe.logSynthetic(
            "sdkPath.align.after",
            activity,
            "B=$alignedControllerLoadId request=$alignedRequestLoadId strategy=$alignedStrategyLoadId",
        )
        return loadId
    }

    private fun findReadyStrategyAndAdapter(controller: Any, adInfo: Any, activity: Activity): Pair<Any, Any>? {
        // These maps live on common.k, the controller superclass. Scanning only
        // controller.declaredFields misses both and always falls back to listener-only.
        // JADX displays the colliding field as f22496e, while its runtime name is e.
        val readyMap = (readField(controller, "e") ?: readField(controller, "f22496e")) as? Map<*, *>
        val readyStrategies = readyMap
            ?.values
            ?.filterNotNull()
            .orEmpty()
        val adapterMap = readField(controller, "u") as? Map<*, *>
        val lookup = controller.javaClass.methods.firstOrNull { method ->
            method.name == "f" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].name == "com.windmill.sdk.strategy.a"
        }?.also { it.isAccessible = true }
        val targetNetworkId = invokeNoArg(adInfo, "getNetworkId")?.toString().orEmpty()
        val targetNetworkPlacement = invokeNoArg(adInfo, "getNetworkPlacementId")?.toString().orEmpty()
        val targetLoadId = invokeNoArg(adInfo, "getLoadId")?.toString().orEmpty()

        val matches = mutableListOf<Pair<Any, Any>>()
        val fallbacks = mutableListOf<Pair<Any, Any>>()

        for (strategy in readyStrategies) {
            val strategyKey = invokeNoArg(strategy, "aw")
            val adapter = runCatching { lookup?.invoke(controller, strategy) }.getOrNull()
                ?: adapterMap?.get(strategyKey)
                ?: continue
            val isReady = runCatching {
                adapter.javaClass.methods.firstOrNull { method ->
                    method.name == "isReady" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0].isAssignableFrom(strategy.javaClass)
                }?.let { method ->
                    method.isAccessible = true
                    method.invoke(adapter, strategy) as? Boolean
                }
            }.getOrNull()
            if (isReady != false) {
                val networkId = invokeNoArg(strategy, "aG")?.toString().orEmpty()
                val networkPlacement = invokeNoArg(strategy, "aP")?.toString().orEmpty()
                val loadId = invokeNoArg(strategy, "aC")?.toString().orEmpty()
                val pair = strategy to adapter
                if ((targetNetworkId.isBlank() || networkId == targetNetworkId) &&
                    (targetNetworkPlacement.isBlank() || networkPlacement == targetNetworkPlacement) &&
                    (targetLoadId.isBlank() || loadId == targetLoadId)
                ) {
                    matches.add(pair)
                } else {
                    fallbacks.add(pair)
                }
                RewardAdProbe.logSynthetic(
                    "sdkPath.readyCandidate",
                    activity,
                    "adapter=${adapter.javaClass.simpleName} networkId=$networkId placement=$networkPlacement loadId=$loadId " +
                        "targetNetworkId=$targetNetworkId targetPlacement=$targetNetworkPlacement targetLoadId=$targetLoadId match=${pair in matches}",
                )
            }
        }
        return matches.firstOrNull() ?: fallbacks.firstOrNull()
    }

    private fun forgeRewardInfo(constructor: Constructor<*>, adInfo: Any): Any? {
        val loadId = invokeNoArg(adInfo, "getLoadId")?.toString().orEmpty()
        val userId = invokeNoArg(adInfo, "getUserId")?.toString().orEmpty()
        if (loadId.isBlank()) {
            ModuleFileLogger.w(TAG, "AdInfo.loadId blank, forged reward may fail server verify")
        }
        return runCatching {
            constructor.newInstance(true, loadId, "", userId)
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "Failed to create forged reward info with loadId", throwable)
        }.getOrNull()
    }

    private fun invokeNoArg(target: Any, name: String): Any? {
        return runCatching {
            target.javaClass.methods
                .firstOrNull { it.name == name && it.parameterCount == 0 }
                ?.invoke(target)
        }.getOrNull()
    }

    private fun invokeOneArg(target: Any, name: String, argType: Class<*>, arg: Any?) {
        runCatching {
            target.javaClass.methods
                .firstOrNull {
                    it.name == name &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0].isAssignableFrom(argType)
                }
                ?.also { it.isAccessible = true }
                ?.invoke(target, arg)
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "Failed to invoke $name(${argType.name})", throwable)
        }
    }

    private fun readField(target: Any, name: String): Any? {
        return findField(target.javaClass, name)?.let { field ->
            runCatching { field.get(target) }.getOrNull()
        }
    }

    private fun writeField(target: Any, name: String, value: Any?) {
        findField(target.javaClass, name)?.let { field ->
            runCatching { field.set(target, value) }
                .onFailure { throwable ->
                    ModuleFileLogger.e(TAG, "Failed to write field: $name", throwable)
                }
        }
    }

    private fun networkName(adInfo: Any): String {
        return runCatching {
            adInfo.javaClass.getMethod("getNetworkName").invoke(adInfo)?.toString().orEmpty()
        }.getOrDefault("")
    }

    private fun isSupportedActivity(activity: Activity): Boolean {
        return activity.javaClass.name == CiweiMaoClasses.MISSION_ACTIVITY ||
            activity.javaClass.name == CiweiMaoClasses.WEB_ACTIVITY
    }

    private fun resolveMethods(
        rewardAdClass: Class<*>,
        rewardInfoClass: Class<*>,
        adInfoClass: Class<*>,
    ): RewardMethods? {
        val hashMapClass = HashMap::class.java
        val show = declaredMethod(rewardAdClass, "show", Activity::class.java, hashMapClass) ?: return null
        val getAdInfo = declaredMethod(rewardAdClass, "getAdInfo") ?: return null
        val onPlayStart = declaredMethod(
            rewardAdClass,
            "onVideoAdPlayStart",
            adInfoClass,
            Boolean::class.javaPrimitiveType!!,
        ) ?: return null
        val onReward = declaredMethod(
            rewardAdClass,
            "onVideoAdReward",
            adInfoClass,
            rewardInfoClass,
        ) ?: return null
        val onPlayEnd = declaredMethod(rewardAdClass, "onVideoAdPlayEnd", adInfoClass) ?: return null
        val onClosed = declaredMethod(rewardAdClass, "onVideoAdClosed", adInfoClass) ?: return null
        return RewardMethods(show, getAdInfo, onPlayStart, onReward, onPlayEnd, onClosed)
    }

    private fun rewardInfoConstructor(rewardInfoClass: Class<*>): Constructor<*>? {
        return runCatching {
            rewardInfoClass.getDeclaredConstructor(
                Boolean::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                String::class.java,
            ).also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "WMRewardInfo constructor not found", throwable)
            null
        }
    }

    private fun hostClass(name: String, classLoader: ClassLoader): Class<*>? {
        return XposedCompat.findClassOrNull(name, classLoader).also { clazz ->
            if (clazz == null) {
                ModuleFileLogger.i(TAG, "Host class not visible yet: $name")
            }
        }
    }

    private fun declaredMethod(clazz: Class<*>, name: String, vararg types: Class<*>): Method? {
        return runCatching {
            clazz.getDeclaredMethod(name, *types).also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "Method not found: ${clazz.name}.$name", throwable)
            null
        }
    }

    private fun findMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val match = current.declaredMethods.firstOrNull { it.name == name }
            if (match != null) {
                match.isAccessible = true
                return match
            }
            current = current.superclass
        }
        return null
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredField(name).also { it.isAccessible = true }
            }
            current = current.superclass
        }
        ModuleFileLogger.e(TAG, "Field not found: ${clazz.name}.$name")
        return null
    }

    private data class RewardMethods(
        val show: Method,
        val getAdInfo: Method,
        val onPlayStart: Method,
        val onReward: Method,
        val onPlayEnd: Method,
        val onClosed: Method,
    )

    private data class PreloadState(
        val chestType: Int,
        var rewardAd: Any? = null,
        var claimRequested: Boolean = false,
    )
}
