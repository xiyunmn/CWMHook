package com.xiyunmn.cwmhook.feature.rewardad

import android.util.Log
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
/**
 * One-shot deep capture for REAL reward playback.
 * Goal: collect WindMill S2S + SDK outbound requests needed for forged replay.
 */
internal object RewardNetworkCapture {
    private const val TAG = "CWMHook.RewardNetCap"

    @Volatile
    private var captureArmed = false
    @Volatile
    private var httpInstalled = false
    @Volatile
    private var windMillS2sInstalled = false
    @Volatile
    private var controllerInstalled = false
    @Volatile
    private var trackInstalled = false
    @Volatile
    private var pointInstalled = false
    @Volatile
    private var hostTrackInstalled = false
    @Volatile
    private var okHttpInstalled = false
    @Volatile
    private var volleyInstalled = false
    @Volatile
    private var lastModule: XposedModule? = null
    @Volatile
    private var lastClassLoader: ClassLoader? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        lastModule = module
        lastClassLoader = classLoader
        installAll(module, classLoader)
    }

    fun installAll(module: XposedModule, classLoader: ClassLoader) {
        lastModule = module
        lastClassLoader = classLoader
        installHttpUrlConnectionProbe(module)
        installOkHttpProbe(module, classLoader)
        installVolleyProbe(module, classLoader)
        installWindMillS2sProbe(module, classLoader)
        installWindMillRewardControllerProbe(module, classLoader)
        installTrackManagerProbe(module, classLoader)
        installPointCommitProbe(module, classLoader)
        installHostTrackProbe(module, classLoader)
        emit(
            "Reward network capture status " +
                "http=$httpInstalled okhttp=$okHttpInstalled volley=$volleyInstalled " +
                "wmS2s=$windMillS2sInstalled controller=$controllerInstalled " +
                "track=$trackInstalled point=$pointInstalled hostTrack=$hostTrackInstalled",
        )
    }

    fun arm(reason: String, classLoader: ClassLoader? = null) {
        captureArmed = true
        // Classes may only appear after ad SDK loads.
        val module = lastModule
        val cl = classLoader
            ?: lastClassLoader
            ?: Thread.currentThread().contextClassLoader
        if (module != null && cl != null) {
            installAll(module, cl)
        }
        emit("CAPTURE ARMED reason=$reason")
    }

    fun disarm(reason: String) {
        captureArmed = false
        emit("CAPTURE DISARMED reason=$reason")
    }

    private fun emit(message: String) {
        Log.i(TAG, message)
        ModuleFileLogger.i(TAG, message)
        runCatching { XposedCompat.module?.log(Log.INFO, TAG, message) }
    }

    private fun interestingUrl(url: String): Boolean {
        val u = url.lowercase()
        return listOf(
            "reward",
            "s2s",
            "tobid",
            "sigmob",
            "windmill",
            "csj",
            "pangle",
            "bytedance",
            "pangolin",
            "gromore",
            "snssdk",
            "toutiao",
            "is.snssdk",
            "log.",
            "ad.",
            "api-access",
            "server_reward",
            "callback",
            "verify",
        ).any { u.contains(it) }
    }

    private fun trim(text: String?, max: Int = 1200): String {
        val value = text.orEmpty().replace('\n', ' ').replace('\r', ' ')
        return if (value.length <= max) value else value.take(max) + "…"
    }

    private fun installWindMillS2sProbe(module: XposedModule, classLoader: ClassLoader) {
        if (windMillS2sInstalled) {
            return
        }
        val clazz = XposedCompat.findClassOrNull("com.windmill.sdk.strategy.j", classLoader) ?: run {
            emit("WindMill strategy.j not visible yet")
            return
        }
        val methods = clazz.declaredMethods.filter {
            it.name == "a" &&
                it.parameterCount >= 2 &&
                it.parameterTypes[0] == String::class.java
        }
        if (methods.isEmpty()) {
            emit("WindMill strategy.j.a(...) not found")
            return
        }
        var hooked = 0
        methods.forEach { method ->
            method.isAccessible = true
            val ok = XposedCompat.hookAfter(module, method, "$TAG.WindMill.strategy.j.a.${method.parameterCount}") { chain ->
                if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                    return@hookAfter
                }
                val args = chain.getArgs()
                val url = args.getOrNull(0)?.toString().orEmpty()
                val body = args.getOrNull(1)?.toString().orEmpty()
                val type = args.getOrNull(2)
                val key = args.getOrNull(3)?.toString().orEmpty()
                emit(
                    "WM_S2S send arity=${method.parameterCount} type=$type key=$key " +
                        "url=${trim(url, 500)} body=${trim(body, 2500)}",
                )
            }
            if (ok) hooked++
        }
        clazz.declaredMethods.filter { method ->
            (method.name == "deliverResponse" || method.name == "deliverError") && method.parameterCount == 1
        }.forEach { method ->
            method.isAccessible = true
            val ok = XposedCompat.hookAfter(module, method, "$TAG.WindMill.strategy.j.${method.name}") { chain ->
                if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                    return@hookAfter
                }
                val request = chain.thisObject ?: return@hookAfter
                val type = readField(request, "f22763d") ?: readField(request, "e")
                val body = readField(request, "f22761b") ?: readField(request, "b")
                val arg = chain.getArg(0)
                emit(
                    "WM_S2S ${method.name} type=$type result=${trim(arg?.toString(), 700)} " +
                        "body=${trim(body?.toString(), 1200)}",
                )
            }
            if (ok) hooked++
        }
        if (hooked > 0) {
            windMillS2sInstalled = true
            emit("WindMill S2S probe installed count=$hooked")
        }
    }

    private fun installWindMillRewardControllerProbe(module: XposedModule, classLoader: ClassLoader) {
        if (controllerInstalled) {
            return
        }
        val clazz = XposedCompat.findClassOrNull("com.windmill.sdk.common.s", classLoader) ?: return
        var hooked = 0
        listOf("adapterDidStartPlayingAd", "adapterDidRewardAd", "adapterDidPlayEndAd", "adapterDidCloseAd").forEach { name ->
            val method = clazz.declaredMethods.firstOrNull { it.name == name }?.also { it.isAccessible = true }
            if (method == null) {
                emit("controller.$name not found")
                return@forEach
            }
            val ok = XposedCompat.hookAfter(module, method, "$TAG.controller.$name") { chain ->
                if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                    return@hookAfter
                }
                val args = chain.getArgs().joinToString("|") { arg ->
                    when (arg) {
                        null -> "null"
                        is Boolean, is Number, is String -> arg.toString()
                        else -> arg.javaClass.simpleName
                    }
                }
                val strategy = chain.getArgs().getOrNull(1)
                val strategyDump = dumpStrategy(strategy)
                emit("controller.$name args=[$args] $strategyDump")
            }
            if (ok) hooked++
        }
        if (hooked > 0) {
            controllerInstalled = true
            emit("WindMill controller reward probes installed count=$hooked")
        }
    }

    private fun dumpStrategy(strategy: Any?): String {
        if (strategy == null) {
            return "strategy=null"
        }
        return runCatching {
            val network = invokeNoArg(strategy, "aI")
            val channelId = invokeNoArg(strategy, "aG")
            val placement = invokeNoArg(strategy, "aP")
            val loadId = invokeNoArg(strategy, "aC")
            val custom = invokeNoArg(strategy, "I")
            "strategy network=$network channelId=$channelId placement=$placement loadId=$loadId custom=$custom"
        }.getOrElse { "strategyDumpFailed" }
    }

    private fun installHttpUrlConnectionProbe(module: XposedModule) {
        if (httpInstalled) {
            return
        }
        val open = runCatching {
            URL::class.java.getDeclaredMethod("openConnection")
        }.getOrNull() ?: return
        val openHooked = XposedCompat.hookAfter(module, open, "$TAG.URL.openConnection") { chain ->
            if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                return@hookAfter
            }
            val url = chain.thisObject?.toString().orEmpty()
            if (!interestingUrl(url)) {
                return@hookAfter
            }
            emit("HTTP openConnection url=${trim(url, 700)}")
        }

        val connect = runCatching {
            HttpURLConnection::class.java.getDeclaredMethod("connect")
        }.getOrNull()
        var connectHooked = true
        if (connect != null) {
            connectHooked = XposedCompat.hookAfter(module, connect, "$TAG.HttpURLConnection.connect") { chain ->
                if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                    return@hookAfter
                }
                val conn = chain.thisObject as? HttpURLConnection ?: return@hookAfter
                val url = runCatching { conn.url?.toString().orEmpty() }.getOrDefault("")
                if (!interestingUrl(url)) {
                    return@hookAfter
                }
                val method = runCatching { conn.requestMethod }.getOrDefault("?")
                emit("HTTP connect method=$method url=${trim(url, 700)}")
            }
        }
        if (openHooked && connectHooked) {
            httpInstalled = true
            emit("HttpURLConnection probes installed")
        }
    }

    private fun installOkHttpProbe(module: XposedModule, classLoader: ClassLoader) {
        if (okHttpInstalled) {
            return
        }
        val realCall = XposedCompat.findClassOrNull("okhttp3.internal.connection.RealCall", classLoader)
            ?: XposedCompat.findClassOrNull("okhttp3.RealCall", classLoader)
            ?: XposedCompat.findClassOrNull("okhttp3.internal.http.RealInterceptorChain", classLoader)
        if (realCall == null) {
            emit("OkHttp RealCall not found")
            return
        }
        var hooked = 0
        realCall.declaredMethods.filter {
            it.name == "execute" || it.name == "enqueue" || it.name == "proceed"
        }.forEach { method ->
            method.isAccessible = true
            val ok = XposedCompat.hookAfter(module, method, "$TAG.OkHttp.${method.name}") { chain ->
                if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                    return@hookAfter
                }
                val callOrChain = chain.thisObject ?: return@hookAfter
                val request = invokeNoArg(callOrChain, "request") ?: return@hookAfter
                val url = invokeNoArg(request, "url")?.toString().orEmpty()
                if (!interestingUrl(url)) {
                    return@hookAfter
                }
                val methodName = invokeNoArg(request, "method")?.toString().orEmpty()
                val body = runCatching { invokeNoArg(request, "body")?.toString() }.getOrNull()
                emit("OKHTTP ${method.name} method=$methodName url=${trim(url, 700)} body=${trim(body, 800)}")
            }
            if (ok) hooked++
        }
        if (hooked > 0) {
            okHttpInstalled = true
            emit("OkHttp probes installed on ${realCall.name} count=$hooked")
        }
    }

    private fun installVolleyProbe(module: XposedModule, classLoader: ClassLoader) {
        if (volleyInstalled) {
            return
        }
        val requestQueue = XposedCompat.findClassOrNull("com.czhj.volley.RequestQueue", classLoader)
            ?: XposedCompat.findClassOrNull("com.android.volley.RequestQueue", classLoader)
        if (requestQueue == null) {
            emit("Volley RequestQueue not found")
            return
        }
        val add = requestQueue.declaredMethods.firstOrNull {
            it.name == "add" && it.parameterCount == 1
        }?.also { it.isAccessible = true } ?: run {
            emit("Volley RequestQueue.add not found")
            return
        }
        val ok = XposedCompat.hookAfter(module, add, "$TAG.Volley.RequestQueue.add") { chain ->
            if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                return@hookAfter
            }
            val req = chain.getArg(0) ?: return@hookAfter
            val url = invokeNoArg(req, "getUrl")?.toString()
                ?: invokeNoArg(req, "getOriginUrl")?.toString()
                ?: req.toString()
            if (!interestingUrl(url)) {
                return@hookAfter
            }
            val method = invokeNoArg(req, "getMethod")
            val body = runCatching {
                val bytes = invokeNoArg(req, "getBody") as? ByteArray
                if (bytes != null) String(bytes) else null
            }.getOrNull()
            emit(
                "VOLLEY add class=${req.javaClass.name} method=$method " +
                    "url=${trim(url, 700)} body=${trim(body, 2000)}",
            )
        }
        if (ok) {
            volleyInstalled = true
            emit("Volley RequestQueue.add probe installed")
        }
    }

    private fun installTrackManagerProbe(module: XposedModule, classLoader: ClassLoader) {
        if (trackInstalled) {
            return
        }
        val clazz = XposedCompat.findClassOrNull("com.czhj.sdk.common.track.TrackManager", classLoader) ?: return
        var hooked = 0
        clazz.declaredMethods.filter { it.name.contains("send", ignoreCase = true) }.forEach { method ->
            method.isAccessible = true
            val ok = XposedCompat.hookAfter(module, method, "$TAG.TrackManager.${method.name}") { chain ->
                if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                    return@hookAfter
                }
                val args = chain.getArgs().joinToString("|") { trim(it?.toString(), 300) }
                emit("TrackManager.${method.name} args=[$args]")
            }
            if (ok) hooked++
        }
        if (hooked > 0) {
            trackInstalled = true
            emit("TrackManager probes installed count=$hooked")
        }
    }

    private fun installPointCommitProbe(module: XposedModule, classLoader: ClassLoader) {
        if (pointInstalled) {
            return
        }
        val candidates = listOf(
            "com.windmill.sdk.point.PointEntityWind",
            "com.windmill.sdk.point.PointEntityBase",
            "com.czhj.sdk.common.mta.PointEntityBase",
        )
        var hooked = 0
        candidates.forEach { name ->
            val clazz = XposedCompat.findClassOrNull(name, classLoader) ?: return@forEach
            val commit = clazz.declaredMethods.firstOrNull { it.name == "commit" && it.parameterCount == 0 }
                ?.also { it.isAccessible = true } ?: return@forEach
            val ok = XposedCompat.hookAfter(module, commit, "$TAG.$name.commit") { chain ->
                if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                    return@hookAfter
                }
                val entity = chain.thisObject ?: return@hookAfter
                val dump = runCatching {
                    listOf(
                        "getAc_type", "getCategory", "getEvent", "getLoad_id", "getPlatform_trans_id",
                        "getReward", "getIs_reward", "getReward_action", "getPlacement_id", "getPlatform",
                        "getAggr_channel_id", "getAggr_placement_id", "getEcpm",
                    ).joinToString(" ") { m ->
                        val v = invokeNoArg(entity, m)
                        "${m.removePrefix("get")}=$v"
                    }
                }.getOrElse { "dumpFailed" }
                emit("POINT commit class=${entity.javaClass.simpleName} $dump entity=${trim(entity.toString(), 500)}")
            }
            if (ok) {
                hooked++
                emit("Point commit probe installed: $name")
            }
        }
        if (hooked > 0) {
            pointInstalled = true
        }
    }

    private fun installHostTrackProbe(module: XposedModule, classLoader: ClassLoader) {
        if (hostTrackInstalled) {
            return
        }
        val netUtils = XposedCompat.findClassOrNull(CiweiMaoClasses.NET_UTILS, classLoader) ?: return
        var hooked = 0
        netUtils.declaredMethods.filter { it.name == "track" }.forEach { method ->
            method.isAccessible = true
            val ok = XposedCompat.hookAfter(module, method, "$TAG.NetUtils.track") { chain ->
                if (!captureArmed && !RewardAdProbe.forceRealAd()) {
                    return@hookAfter
                }
                val args = chain.getArgs()
                val subUrl = args.firstOrNull { it is Number }?.toString()
                val body = args.firstOrNull { it is String && it.length > 2 }?.toString()
                emit("HOST track subUrl=$subUrl body=${trim(body, 1000)}")
            }
            if (ok) hooked++
        }
        if (hooked > 0) {
            hostTrackInstalled = true
            emit("Host NetUtils.track probes installed count=$hooked")
        }
    }

    private fun invokeNoArg(target: Any, name: String): Any? {
        return runCatching {
            target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(target)
        }.getOrNull()
    }

    private fun readField(target: Any, name: String): Any? {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            runCatching {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                return field.get(target)
            }
            current = current.superclass
        }
        return null
    }
}
