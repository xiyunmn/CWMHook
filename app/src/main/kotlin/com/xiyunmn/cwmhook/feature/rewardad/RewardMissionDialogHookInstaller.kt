package com.xiyunmn.cwmhook.feature.rewardad

import android.app.Activity
import android.view.View
import android.widget.Toast
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

internal object RewardMissionDialogHookInstaller {
    private const val TAG = "CWMHook.RewardMissionDialog"

    @Volatile
    private var installed = false

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (installed) {
            return
        }
        val dialogClass = hostClass(CiweiMaoClasses.MISSION_DIALOG, classLoader) ?: return
        val missionBoxClass = hostClass(CiweiMaoClasses.MISSION_BOX_DATA, classLoader) ?: return

        val showVipAd = declaredMethod(
            dialogClass,
            "showBoxDia_A_vip0",
            Activity::class.java,
            missionBoxClass,
            View.OnClickListener::class.java,
        ) ?: return
        val showAd = declaredMethod(
            dialogClass,
            "showBoxDia_Ad",
            Activity::class.java,
            missionBoxClass,
            View.OnClickListener::class.java,
        ) ?: return
        val showThanks = declaredMethod(
            dialogClass,
            "showBoxDia_B_adv",
            Activity::class.java,
            missionBoxClass,
        ) ?: return
        val showOpened = declaredMethod(
            dialogClass,
            "showBoxDia_open",
            Activity::class.java,
            missionBoxClass,
        ) ?: return

        val hooks = listOf(
            hookPreAd(module, showVipAd, "A_vip0"),
            hookPreAd(module, showAd, "Ad"),
            hookPostAd(module, showThanks, "B_adv"),
            hookPostAd(module, showOpened, "open"),
        )
        if (hooks.all { it }) {
            installed = true
            ModuleFileLogger.i(TAG, "Mission dialog hooks installed")
        }
    }

    fun isInstalled(): Boolean = installed

    private fun hookPreAd(module: XposedModule, method: Method, name: String): Boolean {
        return XposedCompat.interceptProtective(module, method, "$TAG.MissionDialog.$name") { chain ->
            val activity = chain.getArg(0) as? Activity
            val listener = chain.getArg(2) as? View.OnClickListener
            if (activity == null || listener == null || !isSupportedActivity(activity)) {
                return@interceptProtective chain.proceed()
            }
            runCatching { listener.onClick(null) }
                .onFailure { throwable ->
                    ModuleFileLogger.e(TAG, "Failed to invoke pre-ad listener: $name", throwable)
                }
            ModuleFileLogger.i(TAG, "Suppressed pre-ad mission dialog: $name")
            null
        }
    }

    private fun hookPostAd(module: XposedModule, method: Method, name: String): Boolean {
        return XposedCompat.interceptProtective(module, method, "$TAG.MissionDialog.$name") { chain ->
            val activity = chain.getArg(0) as? Activity
            if (activity == null || !isSupportedActivity(activity)) {
                return@interceptProtective chain.proceed()
            }
            showToast(activity, rewardMessage(chain.getArg(1)))
            ModuleFileLogger.i(TAG, "Suppressed post-ad mission dialog: $name")
            null
        }
    }

    private fun rewardMessage(missionBoxData: Any?): String {
        val rewards = runCatching {
            val bonusMap = missionBoxData?.javaClass
                ?.methods
                ?.firstOrNull { it.name == "getBonus_map" && it.parameterCount == 0 }
                ?.invoke(missionBoxData) as? Iterable<*>
            bonusMap
                ?.mapNotNull { bonus ->
                    bonus?.javaClass
                        ?.methods
                        ?.firstOrNull { it.name == "getText" && it.parameterCount == 0 }
                        ?.invoke(bonus)
                        ?.toString()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                }
                .orEmpty()
        }.getOrDefault(emptyList())
        return if (rewards.isEmpty()) {
            "奖励已领取"
        } else {
            "获得：${rewards.joinToString("、")}"
        }
    }

    private fun showToast(activity: Activity, message: String) {
        runCatching {
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "Failed to show mission dialog toast", throwable)
        }
    }

    private fun isSupportedActivity(activity: Activity): Boolean {
        return activity.javaClass.name == CiweiMaoClasses.MISSION_ACTIVITY
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
}
