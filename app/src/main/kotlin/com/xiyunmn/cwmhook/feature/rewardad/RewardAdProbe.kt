package com.xiyunmn.cwmhook.feature.rewardad

import android.app.Activity
import android.util.Log
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.io.File
import java.lang.reflect.Method
import org.json.JSONObject

/**
 * Temporary diagnostic probes for reward-ad forge debugging.
 * Logs CreateAdTask / GetWeekboxTask outcomes and MissionActivity grant flags.
 * Remove once the settlement path is confirmed.
 */
internal object RewardAdProbe {
    private const val TAG = "CWMHook.RewardAdProbe"
    private const val FAKE_WEEK_EXP_MARKER = "/data/local/tmp/cwmhook_fake_week_exp"

    @Volatile
    private var createAdProbed = false
    @Volatile
    private var weekboxProbed = false
    @Volatile
    private var allMissionProbed = false
    @Volatile
    private var missionProbed = false

    @Volatile
    private var balanceBefore: AssetBalance? = null
    @Volatile
    private var lastCreateAdBonus: String = ""
    @Volatile
    private var lastMode: String = "UNKNOWN"
    @Volatile
    private var lastCreateAdRequest: String = ""

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (allProbesInstalled()) {
            return
        }
        installCreateAdProbe(module, classLoader)
        installWeekboxProbe(module, classLoader)
        installAllMissionProbe(module, classLoader)
        installMissionGrantProbes(module, classLoader)
        installRewardInfoProbe(module, classLoader)
        emit("Reward ad probes status: createAd=$createAdProbed weekbox=$weekboxProbed allMission=$allMissionProbed mission=$missionProbed forceReal=${forceRealAd()}")
    }

    fun forceRealAd(): Boolean {
        return runCatching {
            java.io.File("/data/local/tmp/cwmhook_force_real_ad").exists()
        }.getOrDefault(false)
    }

    private fun fakeWeekExp(): Boolean {
        return runCatching { File(FAKE_WEEK_EXP_MARKER).exists() }.getOrDefault(false)
    }

    fun markMode(mode: String) {
        lastMode = mode
        emit("compare-mode=$mode forceReal=${forceRealAd()}")
    }

    fun describeAdInfo(adInfo: Any?): String {
        if (adInfo == null) {
            return "adInfo=null"
        }
        return runCatching {
            val network = invokeNoArg(adInfo, "getNetworkName")
            val placement = invokeNoArg(adInfo, "getPlacementId")
            val loadId = invokeNoArg(adInfo, "getLoadId")
            val ecpm = invokeNoArg(adInfo, "getEcpm") ?: invokeNoArg(adInfo, "geteCPM")
            val networkId = invokeNoArg(adInfo, "getNetworkId")
            val networkPlacement = invokeNoArg(adInfo, "getNetworkPlacementId")
            "network=$network networkId=$networkId placement=$placement networkPlacement=$networkPlacement loadId=$loadId ecpm=$ecpm"
        }.getOrElse { "adInfoDescribeFailed=${it.javaClass.simpleName}" }
    }

    fun describeRewardInfo(rewardInfo: Any?): String {
        if (rewardInfo == null) {
            return "rewardInfo=null"
        }
        return runCatching {
            val reward = invokeNoArg(rewardInfo, "isReward")
            val trans = invokeNoArg(rewardInfo, "getTrans_id")
            val third = invokeNoArg(rewardInfo, "getThird_trans_id")
            val user = invokeNoArg(rewardInfo, "getUser_id")
            val custom = invokeNoArg(rewardInfo, "getCustomData")
            "isReward=$reward trans_id=$trans third_trans_id=$third user_id=$user custom=$custom"
        }.getOrElse { "rewardInfoDescribeFailed=${it.javaClass.simpleName}" }
    }

    fun logSynthetic(
        stage: String,
        activity: Activity,
        details: String = "",
    ) {
        val flags = readMissionFlags(activity)
        emit("synthetic:$stage activity=${activity.javaClass.simpleName} $flags ${details.trim()}".trim())
    }

    fun snapshotAssetsBeforePlay(activity: Activity) {
        queryProps(activity, reason = "before-play") { balance, raw ->
            balanceBefore = balance
            emit("assets before-play: $balance raw=${trimForLog(raw)}")
        }
    }

    /**
     * After CreateAd(228) claims success, refresh host props (subUrl=68) and compare balances.
     */
    fun refreshAssetsAfterGrant(activity: Activity) {
        // Delay a bit so: 1) before-play snapshot can finish, 2) server-side grant can settle.
        val posted = ModuleViewTaskRegistry.post(activity.window.decorView, 1_200L) {
            if (!activity.isFinishing && !activity.isDestroyed) {
                queryProps(activity, reason = "after-grant") { balance, raw ->
                    val before = balanceBefore
                    val delta = if (before == null) {
                        "no-before-snapshot"
                    } else {
                        "deltaHlb=${diff(before.restHlb, balance.restHlb)} " +
                            "deltaGiftHlb=${diff(before.restGiftHlb, balance.restGiftHlb)} " +
                            "deltaRecommend=${diff(before.restRecommend, balance.restRecommend)}"
                    }
                    emit(
                        "assets after-grant: $balance createAdBonus=$lastCreateAdBonus $delta raw=${trimForLog(raw)}",
                    )
                }
            }
        }
        if (!posted) {
            emit("assets after-grant post rejected, querying immediately")
            queryProps(activity, reason = "after-grant") { balance, raw ->
                emit("assets after-grant: $balance createAdBonus=$lastCreateAdBonus raw=${trimForLog(raw)}")
            }
        }
    }

    private fun queryProps(
        activity: Activity,
        reason: String,
        onResult: (AssetBalance, String) -> Unit,
    ) {
        val classLoader = activity.classLoader ?: return
        runCatching {
            val taskClass = Class.forName(CiweiMaoClasses.PSON_HOME_PROP_TASK, false, classLoader)
            val baseTaskClass = Class.forName(CiweiMaoClasses.BASE_TASK_NEW, false, classLoader)
            val successCbClass = Class.forName(
                "${CiweiMaoClasses.BASE_TASK_NEW}\$AsyncTaskSuccessCallback",
                false,
                classLoader,
            )
            val failCbClass = Class.forName(
                "${CiweiMaoClasses.BASE_TASK_NEW}\$AsyncTaskFailCallback",
                false,
                classLoader,
            )
            val task = taskClass.getConstructor(android.content.Context::class.java).newInstance(activity)
            val successProxy = java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                arrayOf(successCbClass),
            ) { _, method, args ->
                if (method.name == "successCallback") {
                    val result = args?.getOrNull(0)
                    val raw = if (result == null) {
                        ""
                    } else {
                        invokeNoArg(result, "getMessage")?.toString().orEmpty()
                    }
                    val balance = parseAssetBalance(raw)
                    onResult(balance, raw)
                }
                null
            }
            val failProxy = java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                arrayOf(failCbClass),
            ) { _, method, args ->
                if (method.name == "failCallback") {
                    val result = args?.getOrNull(0)
                    emit("PsonHomePropTask $reason FAILED: ${describeResult(result)}")
                }
                null
            }
            baseTaskClass.getMethod("setAsyncTaskSuccessCallback", successCbClass)
                .invoke(task, successProxy)
            baseTaskClass.getMethod("setAsyncTaskFailCallback", failCbClass)
                .invoke(task, failProxy)
            val execute = findExecute(taskClass)
            if (execute != null) {
                execute.invoke(task, emptyArray<Any>())
                emit("PsonHomePropTask.execute reason=$reason")
            } else {
                emit("PsonHomePropTask.execute not found reason=$reason")
            }
        }.onFailure { throwable ->
            emit("PsonHomePropTask $reason failed: ${throwable.javaClass.simpleName}:${throwable.message}")
            ModuleFileLogger.e(TAG, "Failed to query assets reason=$reason", throwable)
        }
    }

    private fun parseAssetBalance(raw: String): AssetBalance {
        return AssetBalance(
            restHlb = extractJsonString(raw, "rest_hlb"),
            restGiftHlb = extractJsonString(raw, "rest_gift_hlb"),
            restRecommend = extractJsonString(raw, "rest_recommend"),
            restYp = extractJsonString(raw, "rest_yp"),
        )
    }

    private fun extractJsonString(raw: String, key: String): String {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(raw)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun extractCreateAdBonus(raw: String): String {
        val hlb = Regex("\"hlb\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.getOrNull(1)
        val exp = Regex("\"exp\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.getOrNull(1)
        val record = Regex("\"record\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.getOrNull(1)
        return "record=$record hlb=$hlb exp=$exp"
    }

    private fun diff(before: String, after: String): String {
        val b = before.toLongOrNull()
        val a = after.toLongOrNull()
        return if (b != null && a != null) (a - b).toString() else "?"
    }

    private data class AssetBalance(
        val restHlb: String,
        val restGiftHlb: String,
        val restRecommend: String,
        val restYp: String,
    ) {
        override fun toString(): String {
            return "rest_hlb=$restHlb rest_gift_hlb=$restGiftHlb rest_recommend=$restRecommend rest_yp=$restYp"
        }
    }

    private fun emit(message: String) {
        // Temporary multi-sink for live diagnosis.
        Log.i(TAG, message)
        ModuleFileLogger.i(TAG, message)
        runCatching {
            XposedCompat.module?.log(Log.INFO, TAG, message)
        }
    }

    private fun allProbesInstalled(): Boolean {
        return createAdProbed && weekboxProbed && allMissionProbed && missionProbed
    }

    private fun installCreateAdProbe(module: XposedModule, classLoader: ClassLoader) {
        if (createAdProbed) {
            return
        }
        val taskClass = hostClass(CiweiMaoClasses.CREATE_AD_TASK, classLoader) ?: return
        val execute = findExecute(taskClass) ?: run {
            ModuleFileLogger.w(TAG, "CreateAdTask.execute not found")
            return
        }
        val doHttp = findAnyMethod(taskClass, "doHttpRequest")
        val getData = findAnyMethod(taskClass, "getData")

        val executeHooked = XposedCompat.hookAfter(module, execute, "$TAG.CreateAdTask.execute") { chain ->
            if (!isInstance(chain.thisObject, CiweiMaoClasses.CREATE_AD_TASK)) {
                return@hookAfter
            }
            val args = chain.getArgs().joinToString(",") { summarizeArg(it) }
            lastCreateAdRequest = args
            emit("CreateAdTask.execute mode=$lastMode args=[$args]")
        }
        var doHttpHooked = true
        if (doHttp != null) {
            doHttpHooked = XposedCompat.hookAfter(module, doHttp, "$TAG.CreateAdTask.doHttpRequest") { chain ->
                if (!isInstance(chain.thisObject, CiweiMaoClasses.CREATE_AD_TASK)) {
                    return@hookAfter
                }
                val args = chain.getArgs().joinToString(",") { summarizeArg(it) }
                lastCreateAdRequest = args
                emit("CreateAdTask.doHttpRequest mode=$lastMode args=[$args] // orderNo|sub_type|src|ad_type")
            }
        }
        var getDataHooked = true
        if (getData != null) {
            getDataHooked = XposedCompat.hookAfter(module, getData, "$TAG.CreateAdTask.getData") { chain ->
                if (!isInstance(chain.thisObject, CiweiMaoClasses.CREATE_AD_TASK)) {
                    return@hookAfter
                }
                val result = chain.getArg(0)
                val raw = if (result == null) {
                    ""
                } else {
                    invokeNoArg(result, "getMessage")?.toString().orEmpty()
                }
                lastCreateAdBonus = extractCreateAdBonus(raw)
                emit(
                    "CreateAdTask.getData mode=$lastMode request=[$lastCreateAdRequest] " +
                        "${describeResult(result)} parsedBonus=$lastCreateAdBonus",
                )
            }
        }
        if (executeHooked && doHttpHooked && getDataHooked) {
            createAdProbed = true
            emit("CreateAdTask probes installed")
        }
    }

    private fun installWeekboxProbe(module: XposedModule, classLoader: ClassLoader) {
        if (weekboxProbed) {
            return
        }
        val taskClass = hostClass(CiweiMaoClasses.GET_WEEKBOX_TASK, classLoader) ?: return
        val execute = findExecute(taskClass) ?: run {
            ModuleFileLogger.w(TAG, "GetWeekboxTask.execute not found")
            return
        }
        val getData = findAnyMethod(taskClass, "getData")
        val executeHooked = XposedCompat.hookAfter(module, execute, "$TAG.GetWeekboxTask.execute") { chain ->
            if (!isInstance(chain.thisObject, CiweiMaoClasses.GET_WEEKBOX_TASK)) {
                return@hookAfter
            }
            val args = chain.getArgs().joinToString(",") { summarizeArg(it) }
            emit("GetWeekboxTask.execute args=[$args]")
        }
        var getDataHooked = true
        if (getData != null) {
            getDataHooked = XposedCompat.hookAfter(module, getData, "$TAG.GetWeekboxTask.getData") { chain ->
                if (!isInstance(chain.thisObject, CiweiMaoClasses.GET_WEEKBOX_TASK)) {
                    return@hookAfter
                }
                val result = chain.getArg(0)
                emit("GetWeekboxTask.getData ${describeResult(result)}")
            }
        }
        if (executeHooked && getDataHooked) {
            weekboxProbed = true
            emit("GetWeekboxTask probes installed")
        }
    }

    private fun installAllMissionProbe(module: XposedModule, classLoader: ClassLoader) {
        if (allMissionProbed) {
            return
        }
        val taskClass = hostClass(CiweiMaoClasses.ALL_MISSION_LIST_TASK, classLoader) ?: return
        val getData = findAnyMethod(taskClass, "getData") ?: return
        val hooked = XposedCompat.interceptProtective(module, getData, "$TAG.AllMissionListTask.getData") { chain ->
            if (!isInstance(chain.thisObject, CiweiMaoClasses.ALL_MISSION_LIST_TASK)) {
                return@interceptProtective chain.proceed()
            }
            val result = chain.getArg(0)
            if (fakeWeekExp()) {
                fakeWeekExpInResult(result)
            }
            val output = chain.proceed()
            emit("AllMissionListTask.getData ${describeResult(result)} ${describeWeekBoxes(result)}")
            output
        }
        if (hooked) {
            allMissionProbed = true
            emit("AllMissionListTask probe installed")
        }
    }

    private fun fakeWeekExpInResult(result: Any?) {
        if (result == null) {
            return
        }
        runCatching {
            val raw = invokeNoArg(result, "getMessage")?.toString().orEmpty()
            if (raw.isBlank()) {
                return
            }
            val root = JSONObject(raw)
            val data = root.optJSONObject("data") ?: return
            val week = data.optJSONObject("week_task_info") ?: return
            val boxes = week.optJSONArray("chest_info_map") ?: return
            var maxExp = week.optInt("total_exp", 0)
            for (index in 0 until boxes.length()) {
                val box = boxes.optJSONObject(index) ?: continue
                maxExp = maxOf(maxExp, box.optInt("exp", 0))
            }
            if (maxExp <= 0) {
                return
            }
            week.put("total_exp", maxExp)
            data.put("total_exp", maxExp)
            result.javaClass.methods
                .firstOrNull {
                    it.name == "setMessage" &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == String::class.java
                }
                ?.invoke(result, root.toString())
            emit("AllMissionListTask fake week exp enabled totalExp=$maxExp")
        }.onFailure { throwable ->
            emit("AllMissionListTask fake week exp failed: ${throwable.javaClass.simpleName}:${throwable.message}")
        }
    }

    private fun describeWeekBoxes(result: Any?): String {
        return runCatching {
            val value = invokeNoArg(result ?: return "", "getValue") ?: return ""
            val week = invokeNoArg(value, "getWeek_task_info") ?: return "week=null"
            val total = invokeNoArg(week, "getTotal_exp")
            val access = invokeNoArg(week, "getAccess")
            val boxes = invokeNoArg(week, "getChest_info_map") as? List<*> ?: return "week access=$access totalExp=$total boxes=null"
            val boxText = boxes.joinToString(";") { box ->
                if (box == null) {
                    "null"
                } else {
                    "type=${invokeNoArg(box, "getChest_type")} exp=${invokeNoArg(box, "getExp")} " +
                        "opened=${invokeNoArg(box, "getOpened")} adMust=${invokeNoArg(box, "getAd_must")} " +
                        "adFinished=${invokeNoArg(box, "getAd_finished")}"
                }
            }
            "week access=$access totalExp=$total boxes=[$boxText]"
        }.getOrElse { "weekDescribeFailed=${it.javaClass.simpleName}:${it.message}" }
    }

    private fun installMissionGrantProbes(module: XposedModule, classLoader: ClassLoader) {
        if (missionProbed) {
            return
        }
        val missionClass = hostClass(CiweiMaoClasses.MISSION_ACTIVITY, classLoader) ?: return
        val names = listOf("createAd", "getBouns", "startAni", "openBox", "sendBouns", "playAdv")
        var hookedCount = 0
        names.forEach { name ->
            val method = findAnyMethod(missionClass, name) ?: run {
                ModuleFileLogger.w(TAG, "MissionActivity.$name not found for probe")
                return@forEach
            }
            val hooked = XposedCompat.hookAfter(module, method, "$TAG.MissionActivity.$name") { chain ->
                val activity = chain.thisObject as? Activity
                val args = chain.getArgs().joinToString(",") { summarizeArg(it) }
                val flags = activity?.let { readMissionFlags(it) }.orEmpty()
                emit("MissionActivity.$name mode=$lastMode args=[$args] $flags")
                if (name == "playAdv" && activity != null) {
                    if (!forceRealAd()) {
                        // default path is forged unless force-real marker exists
                        markMode("FORGED")
                        RewardNetworkCapture.arm(
                            reason = "playAdv-forged",
                            classLoader = activity.classLoader ?: activity.javaClass.classLoader,
                        )
                    } else {
                        markMode("REAL")
                        RewardNetworkCapture.arm(
                            reason = "playAdv-real",
                            classLoader = activity.classLoader ?: activity.javaClass.classLoader,
                        )
                    }
                    snapshotAssetsBeforePlay(activity)
                }
                if (name == "getBouns" && activity != null && (lastMode == "REAL" || lastMode == "FORGED")) {
                    // Real ad path settles in onResume->getBouns; refresh assets after that.
                    refreshAssetsAfterGrant(activity)
                    RewardNetworkCapture.disarm("${lastMode.lowercase()}-getBouns")
                }
                if (name == "playAdv" || name == "createAd") {
                    // WindMill classes may load only when the mission ad path starts.
                    val classLoader = activity?.classLoader
                        ?: chain.thisObject?.javaClass?.classLoader
                    if (classLoader != null) {
                        RewardAdSkipHookInstaller.install(module, classLoader)
                        installRewardInfoProbe(module, classLoader)
                    }
                }
            }
            if (hooked) {
                hookedCount++
            }
        }
        if (hookedCount > 0) {
            missionProbed = true
            emit("MissionActivity grant probes installed: $hookedCount/${names.size}")
        }
    }

    @Volatile
    private var rewardInfoProbed = false

    private fun installRewardInfoProbe(module: XposedModule, classLoader: ClassLoader) {
        if (rewardInfoProbed) {
            return
        }
        val rewardAdClass = hostClass(CiweiMaoClasses.WM_REWARD_AD, classLoader) ?: return
        val rewardInfoClass = hostClass(CiweiMaoClasses.WM_REWARD_INFO, classLoader) ?: return
        val adInfoClass = hostClass(CiweiMaoClasses.WM_AD_INFO, classLoader) ?: return
        val onReward = findAnyMethod(rewardAdClass, "onVideoAdReward")
            ?: runCatching {
                rewardAdClass.getDeclaredMethod("onVideoAdReward", adInfoClass, rewardInfoClass)
                    .also { it.isAccessible = true }
            }.getOrNull()
        if (onReward == null) {
            emit("WMRewardAd.onVideoAdReward not found for probe")
            return
        }
        val hooked = XposedCompat.hookAfter(module, onReward, "$TAG.WMRewardAd.onVideoAdReward") { chain ->
            val adInfo = chain.getArgs().getOrNull(0)
            val rewardInfo = chain.getArgs().getOrNull(1)
            emit(
                "WMRewardAd.onVideoAdReward mode=$lastMode " +
                    "adInfo=${describeAdInfo(adInfo)} rewardInfo=${describeRewardInfo(rewardInfo)}",
            )
        }
        if (hooked) {
            rewardInfoProbed = true
            emit("WMRewardAd.onVideoAdReward probe installed")
        }
    }

    private fun describeResult(result: Any?): String {
        if (result == null) {
            return "result=null"
        }
        return runCatching {
            val success = invokeNoArg(result, "isSuccess")
            val code = invokeNoArg(result, "getCode")
            val message = invokeNoArg(result, "getMessage")?.toString().orEmpty()
            val value = invokeNoArg(result, "getValue")
            val valueDesc = describeCreateAdValue(value)
            "success=$success code=$code message=${trimForLog(message)} value=$valueDesc"
        }.getOrElse { throwable ->
            "resultDescribeFailed=${throwable.javaClass.simpleName}:${throwable.message}"
        }
    }

    private fun describeCreateAdValue(value: Any?): String {
        if (value == null) {
            return "null"
        }
        return runCatching {
            val record = invokeNoArg(value, "getRecord")?.toString()
            val bonus = invokeNoArg(value, "getBonus")
            val bonusText = bonus?.let { invokeNoArg(it, "getText") }?.toString()
            "CreateAdData(record=$record, bonus=$bonusText, class=${value.javaClass.simpleName})"
        }.getOrElse {
            "class=${value.javaClass.name}"
        }
    }

    private fun invokeNoArg(target: Any, name: String): Any? {
        return target.javaClass.methods
            .firstOrNull { it.name == name && it.parameterCount == 0 }
            ?.invoke(target)
    }

    private fun readMissionFlags(activity: Activity): String {
        if (activity.javaClass.name != CiweiMaoClasses.MISSION_ACTIVITY) {
            return ""
        }
        val get = readBoolean(activity, "isGetAdvBouns")
        val create = readBoolean(activity, "isCreateAdvBouns")
        val plan = readString(activity, "planType")
        val adT = readString(activity, "adT")
        val chest = readChestType(activity)
        return "isGetAdvBouns=$get isCreateAdvBouns=$create planType=$plan adT=$adT chestType=$chest"
    }

    private fun readChestType(activity: Activity): String {
        val box = readField(activity, "cacheMissionBoxData") ?: return "?"
        return runCatching {
            invokeNoArg(box, "getChest_type")?.toString().orEmpty()
        }.getOrDefault("?")
    }

    private fun readBoolean(target: Any, name: String): String {
        val field = findField(target.javaClass, name) ?: return "?"
        return runCatching { field.getBoolean(target).toString() }.getOrDefault("?")
    }

    private fun readString(target: Any, name: String): String {
        val field = findField(target.javaClass, name) ?: return "?"
        return runCatching { field.get(target)?.toString().orEmpty() }.getOrDefault("?")
    }

    private fun readField(target: Any, name: String): Any? {
        val field = findField(target.javaClass, name) ?: return null
        return runCatching { field.get(target) }.getOrNull()
    }

    private fun summarizeArg(arg: Any?): String {
        if (arg == null) {
            return "null"
        }
        return when (arg) {
            is Array<*> -> arg.joinToString("|") { summarizeArg(it) }
            is String, is Number, is Boolean -> arg.toString()
            is Activity -> arg.javaClass.simpleName
            else -> arg.javaClass.simpleName
        }
    }

    private fun trimForLog(text: String, max: Int = 240): String {
        val compact = text.replace('\n', ' ').replace('\r', ' ')
        return if (compact.length <= max) compact else compact.take(max) + "…"
    }

    private fun findExecute(taskClass: Class<*>): Method? {
        return findAnyMethod(taskClass, "execute")
    }

    private fun findAnyMethod(clazz: Class<*>, name: String): Method? {
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

    private fun findField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredField(name).also { it.isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }

    private fun hostClass(name: String, classLoader: ClassLoader): Class<*>? {
        return XposedCompat.findClassOrNull(name, classLoader).also { clazz ->
            if (clazz == null) {
                ModuleFileLogger.i(TAG, "Host class not visible yet: $name")
            }
        }
    }

    private fun isInstance(target: Any?, className: String): Boolean {
        return target?.javaClass?.name == className
    }
}
