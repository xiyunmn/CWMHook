package com.xiyunmn.cwmhook.feature.startupprobe

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.xiyunmn.cwmhook.config.debug.DebugConfigStore
import com.xiyunmn.cwmhook.config.startupopt.StartupOptimizeConfig
import com.xiyunmn.cwmhook.config.startupopt.StartupOptimizeConfigStore
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import com.xiyunmn.cwmhook.host.CiweiMaoPackages
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

object StartupNetworkTaskProbe {
    private const val TAG = "CWMHook.StartupNetwork"
    private const val STARTUP_WINDOW_MS = 30_000L
    private const val AUXILIARY_DELAY_WINDOW_MS = 4_000L
    private const val AUXILIARY_DELAY_MS = 1_800L
    private const val MAX_TASK_LINES = 120
    private const val MAX_NET_LINES = 120
    private const val SLOW_NET_MS = 900L

    private val taskLines = AtomicInteger()
    private val netLines = AtomicInteger()
    private val delayedTaskStartMs = Collections.synchronizedMap(WeakHashMap<Any, Long>())
    private val mainHandlerDelegate = lazy { Handler(Looper.getMainLooper()) }
    private val mainHandler by mainHandlerDelegate

    @Volatile
    private var baseElapsedMs = 0L

    @Volatile
    private var baseTaskHooked = false

    @Volatile
    private var netUtilsHooked = false

    @Volatile
    private var startupConfig = StartupOptimizeConfigStore.defaultConfig()

    @Volatile
    private var probeEnabled = false

    @Volatile
    private var lastConfigReadMs = 0L

    @Volatile
    private var taskContextField: Field? = null

    fun configure(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastConfigReadMs < 1_500L) {
            return
        }
        lastConfigReadMs = now
        startupConfig = runCatching {
            StartupOptimizeConfigStore.readLocal(context)
        }.getOrDefault(StartupOptimizeConfigStore.defaultConfig())
        probeEnabled = runCatching {
            DebugConfigStore.readLocal(context).detailedFileLogEnabled
        }.getOrDefault(false)
    }

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (baseElapsedMs == 0L) {
            baseElapsedMs = SystemClock.elapsedRealtime()
        }
        installBaseTaskProbe(module, classLoader)
        installNetUtilsProbe(module, classLoader)
    }

    fun prepareForHotReload() {
        if (mainHandlerDelegate.isInitialized()) {
            mainHandler.removeCallbacksAndMessages(null)
        }
        delayedTaskStartMs.clear()
        taskContextField = null
    }

    private fun installBaseTaskProbe(module: XposedModule, classLoader: ClassLoader) {
        if (baseTaskHooked) {
            return
        }
        val baseTaskClass = XposedCompat.findClassOrNull(CiweiMaoClasses.BASE_TASK_NEW, classLoader) ?: return
        val executeMethod = runCatching {
            baseTaskClass.getDeclaredMethod("execute", Array<Any>::class.java).also { it.isAccessible = true }
        }.getOrNull() ?: return
        taskContextField = runCatching {
            baseTaskClass.getDeclaredField("context").also { it.isAccessible = true }
        }.getOrNull()

        val hooked = XposedCompat.interceptProtective(module, executeMethod, "$TAG.BaseTaskNew.execute") { chain ->
            val task = chain.thisObject
            val context = taskContext(task)
            if (context != null) {
                configure(context)
            }
            val config = startupConfig
            val shouldProbe = probeEnabled
            if (!config.enabled && !shouldProbe) {
                return@interceptProtective chain.proceed()
            }

            val startMs = SystemClock.elapsedRealtime()
            val shouldLog = shouldProbe && shouldLogTask(startMs)
            val taskName = task.javaClass.name
            val caller = if (shouldLog) callerFromStack() else ""

            delayedTaskStartMs.remove(task)?.let { scheduledMs ->
                val result = chain.proceed()
                if (shouldLog) {
                    logTask(
                        startMs = startMs,
                        taskName = taskName,
                        category = taskCategory(taskName),
                        args = chain.getArg(0) as? Array<*>,
                        scheduleCostMs = SystemClock.elapsedRealtime() - startMs,
                        caller = "delayed:${startMs - scheduledMs}ms",
                    )
                }
                return@interceptProtective result
            }

            if (config.enabled && shouldDelayTask(config, taskName, startMs)) {
                val args = (chain.getArg(0) as? Array<*>)?.copyOf() ?: emptyArray<Any?>()
                delayedTaskStartMs[task] = startMs
                mainHandler.postDelayed({
                    runCatching {
                        executeMethod.invoke(task, args as Any)
                    }.onFailure { throwable ->
                        delayedTaskStartMs.remove(task)
                        ModuleFileLogger.e(
                            TAG,
                            "Delayed startup task failed: task=${taskName.substringAfterLast('.')}",
                            throwable,
                        )
                    }
                }, AUXILIARY_DELAY_MS)
                if (shouldLog) {
                    logTask(
                        startMs = startMs,
                        taskName = taskName,
                        category = "${taskCategory(taskName)}-delayed",
                        args = args,
                        scheduleCostMs = 0L,
                        caller = caller,
                    )
                }
                return@interceptProtective null
            }

            val result = chain.proceed()
            if (shouldLog) {
                logTask(
                    startMs = startMs,
                    taskName = taskName,
                    category = taskCategory(taskName),
                    args = chain.getArg(0) as? Array<*>,
                    scheduleCostMs = SystemClock.elapsedRealtime() - startMs,
                    caller = caller,
                )
            }
            result
        }
        if (hooked) {
            baseTaskHooked = true
            ModuleFileLogger.i(TAG, "BaseTaskNew execute probe installed")
        }
    }

    private fun installNetUtilsProbe(module: XposedModule, classLoader: ClassLoader) {
        if (netUtilsHooked) {
            return
        }
        val netUtilsClass = XposedCompat.findClassOrNull(CiweiMaoClasses.NET_UTILS, classLoader) ?: return
        val hookedAny = hookNativeLogSwitch(module, netUtilsClass) or
            hookNetMethod(
                module = module,
                netUtilsClass = netUtilsClass,
                name = "track",
                parameterTypes = arrayOf(
                    Context::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                ),
            ) or
            hookNetMethod(
                module = module,
                netUtilsClass = netUtilsClass,
                name = "trackF",
                parameterTypes = arrayOf(
                    Context::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                ),
            ) or
            hookNetMethod(
                module = module,
                netUtilsClass = netUtilsClass,
                name = "trackAuthor",
                parameterTypes = arrayOf(
                    Context::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                ),
            ) or
            hookNetMethod(
                module = module,
                netUtilsClass = netUtilsClass,
                name = "trackAuthorF",
                parameterTypes = arrayOf(
                    Context::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                ),
            ) or
            hookNetMethod(
                module = module,
                netUtilsClass = netUtilsClass,
                name = "trackS",
                parameterTypes = arrayOf(Context::class.java, String::class.java, String::class.java),
            )
        if (hookedAny) {
            netUtilsHooked = true
            ModuleFileLogger.i(TAG, "NetUtils startup probe installed")
        }
    }

    private fun hookNativeLogSwitch(module: XposedModule, netUtilsClass: Class<*>): Boolean {
        val method = runCatching {
            netUtilsClass.getDeclaredMethod("isDisplayLog", Boolean::class.javaPrimitiveType).also {
                it.isAccessible = true
            }
        }.getOrNull() ?: return false

        return XposedCompat.interceptProtective(module, method, "$TAG.NetUtils.isDisplayLog") { chain ->
            val requested = chain.getArg(0) as? Boolean
            val config = startupConfig
            if (config.enabled && config.disableNativeNetworkLog && requested == true) {
                ModuleFileLogger.throttled(
                    key = "$TAG.disableNativeLog",
                    intervalMs = 60_000L,
                    priority = Log.INFO,
                    tag = TAG,
                    message = "Native network verbose log disabled",
                )
                chain.proceed(arrayOf(false))
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookNetMethod(
        module: XposedModule,
        netUtilsClass: Class<*>,
        name: String,
        parameterTypes: Array<Class<*>>,
    ): Boolean {
        val method = runCatching {
            netUtilsClass.getDeclaredMethod(name, *parameterTypes).also { it.isAccessible = true }
        }.getOrNull() ?: return false

        return XposedCompat.interceptProtective(module, method, "$TAG.NetUtils.$name") { chain ->
            val context = chain.getArg(0) as? Context
            if (context != null) {
                configure(context)
            }
            if (!probeEnabled) {
                return@interceptProtective chain.proceed()
            }

            val startMs = SystemClock.elapsedRealtime()
            val call = describeNetCall(method, chain.getArgs())
            val caller = if (shouldPrepareCaller(startMs)) callerFromStack() else ""
            try {
                val result = chain.proceed()
                logNetCallIfNeeded(startMs, call, caller, result as? String, null)
                result
            } catch (throwable: Throwable) {
                logNetCallIfNeeded(startMs, call, caller, null, throwable)
                throw throwable
            }
        }
    }

    private fun taskContext(task: Any): Context? {
        return runCatching { taskContextField?.get(task) as? Context }.getOrNull()
    }

    private fun shouldLogTask(nowMs: Long): Boolean {
        val age = nowMs - baseElapsedMs
        if (age > STARTUP_WINDOW_MS) {
            return false
        }
        return taskLines.incrementAndGet() <= MAX_TASK_LINES
    }

    private fun shouldPrepareCaller(nowMs: Long): Boolean {
        return nowMs - baseElapsedMs <= STARTUP_WINDOW_MS && netLines.get() < MAX_NET_LINES
    }

    private fun logNetCallIfNeeded(
        startMs: Long,
        call: String,
        caller: String,
        response: String?,
        throwable: Throwable?,
    ) {
        val costMs = SystemClock.elapsedRealtime() - startMs
        val ageMs = startMs - baseElapsedMs
        val withinStartupWindow = ageMs <= STARTUP_WINDOW_MS && netLines.incrementAndGet() <= MAX_NET_LINES
        val ok = throwable == null && !response.isNullOrBlank() && !response.startsWith("herror")
        val message = "native request +${ageMs}ms $call cost=${costMs}ms ok=$ok " +
            "bytes=${response?.length ?: 0} caller=${caller.ifBlank { "-" }}"
        if (withinStartupWindow) {
            ModuleFileLogger.i(TAG, message)
        } else if (costMs >= SLOW_NET_MS) {
            ModuleFileLogger.throttled(
                key = "$TAG.slow.$call",
                intervalMs = 30_000L,
                priority = Log.INFO,
                tag = TAG,
                message = message,
                throwable = throwable,
            )
        }
    }

    private fun logTask(
        startMs: Long,
        taskName: String,
        category: String,
        args: Array<*>?,
        scheduleCostMs: Long,
        caller: String,
    ) {
        ModuleFileLogger.i(
            TAG,
            "task scheduled +${startMs - baseElapsedMs}ms " +
                "task=${taskName.substringAfterLast('.')} " +
                "category=$category " +
                "args=${args?.size ?: 0} " +
                "scheduleCost=${scheduleCostMs}ms " +
                "caller=$caller",
        )
    }

    private fun describeNetCall(method: Method, args: List<Any>): String {
        val name = method.name
        return when (name) {
            "track", "trackF", "trackAuthor", "trackAuthorF" -> {
                val subUrl = args.getOrNull(1) as? Int
                val userType = args.getOrNull(3) as? Int
                "$name subUrl=${subUrl ?: -1} userType=${userType ?: -1}"
            }
            "trackS" -> {
                val endpoint = (args.getOrNull(1) as? String).orEmpty()
                "$name endpoint=${redactEndpoint(endpoint)}"
            }
            else -> name
        }
    }

    private fun redactEndpoint(endpoint: String): String {
        val noQuery = endpoint.substringBefore('?')
        return noQuery.takeLast(96).ifBlank { "-" }
    }

    private fun taskCategory(className: String): String {
        val simple = className.substringAfterLast('.')
        return when {
            simple == "GetStartPageTask" -> "startup-page-prefetch"
            simple == "GetAdCheckTask" -> "startup-ad-check"
            simple == "GetThirdTask" -> "third-switch"
            simple == "GetCheckTask" || simple == "CheckTask1" -> "startup-check"
            simple == "UpdateTask" -> "update-check"
            simple == "SendImeiTask" -> "device-report"
            simple == "CheckComputerTask" -> "environment-report"
            simple == "PsonHomePropTask" || simple == "GeneralTask" -> "profile"
            simple == "YinsiTask" -> "privacy"
            else -> "business"
        }
    }

    private fun shouldDelayTask(config: StartupOptimizeConfig, className: String, nowMs: Long): Boolean {
        if (!config.delayAuxiliaryStartupTasks || nowMs - baseElapsedMs > AUXILIARY_DELAY_WINDOW_MS) {
            return false
        }
        return className.substringAfterLast('.') in auxiliaryStartupTasks
    }

    private val auxiliaryStartupTasks = setOf(
        "UpdateTask",
        "GetCheckTask",
        "CheckTask1",
        "GetThirdTask",
        "SignRecordTask",
        "GetNewUserTask",
        "PsonHomePropTask",
        "SendShenceTask",
    )

    private fun callerFromStack(): String {
        return Thread.currentThread().stackTrace
            .asSequence()
            .firstOrNull { frame ->
                frame.className.startsWith(CiweiMaoPackages.NOVEL) &&
                    !frame.className.contains(".task.newtask.") &&
                    frame.className != CiweiMaoClasses.NET_UTILS
            }
            ?.let { frame ->
                "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
            }
            ?: "-"
    }
}
