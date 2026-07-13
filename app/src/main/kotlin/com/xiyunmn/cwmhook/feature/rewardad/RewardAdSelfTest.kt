package com.xiyunmn.cwmhook.feature.rewardad

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.io.File
import java.lang.reflect.Field

internal object RewardAdSelfTest {
    private const val TAG = "CWMHook.RewardAdSelfTest"
    private const val MARKER = "/data/local/tmp/cwmhook_reward_ad_selftest"
    private const val FAKE_WEEK_EXP_MARKER = "/data/local/tmp/cwmhook_fake_week_exp"

    @Volatile
    private var installed = false
    @Volatile
    private var fired = false

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (installed) {
            return
        }
        val missionClass = XposedCompat.findClassOrNull(CiweiMaoClasses.MISSION_ACTIVITY, classLoader) ?: return
        val getBouns = runCatching {
            missionClass.getDeclaredMethod("getBouns").also { it.isAccessible = true }
        }.getOrNull() ?: return
        val hooked = XposedCompat.hookAfter(module, getBouns, "$TAG.MissionActivity.getBouns") { chain ->
            if (fired || !File(MARKER).exists()) {
                return@hookAfter
            }
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            fired = true
            File(MARKER).delete()
            activity.window.decorView.postDelayed({
                runSyntheticEntry(activity)
            }, 800L)
        }
        if (hooked) {
            installed = true
            ModuleFileLogger.i(TAG, "Reward ad self-test hook installed on getBouns")
        }
    }

    fun reset() {
        fired = false
    }

    private fun runSyntheticEntry(activity: Activity, attempt: Int = 0) {
        runCatching {
            val owner = activity.javaClass
            val boxFieldName = if (File(FAKE_WEEK_EXP_MARKER).exists()) "boxLay13" else "boxLay12"
            val boxLayout = findField(owner, boxFieldName)?.get(activity)
                ?: error("$boxFieldName is null")
            val root = boxLayout.javaClass.methods
                .first { it.name == "getChildAt" && it.parameterCount == 1 }
                .invoke(boxLayout, 0) as View
            val missionBox = findMissionBoxFromClickListeners(root)
                ?: error("MissionBoxData listener not found")
            val chestType = runCatching {
                missionBox.javaClass.getDeclaredMethod("getChest_type")
                    .also { it.isAccessible = true }
                    .invoke(missionBox)
            }.getOrNull()

            val resources = activity.resources
            val packageName = activity.packageName
            val imageId = resources.getIdentifier("boxImg", "id", packageName)
            var dividerId = resources.getIdentifier("boxDivider", "id", packageName)
            if (dividerId == 0) {
                dividerId = resources.getIdentifier("boxDivier", "id", packageName)
            }
            val image = root.findViewById<ImageView>(imageId)
                ?: (root as? ViewGroup)?.getChildAt(0) as? ImageView
            val divider = root.findViewById<View>(dividerId)
                ?: (root as? ViewGroup)?.getChildAt(1)
            owner.getDeclaredMethod(
                "playAdv",
                ImageView::class.java,
                View::class.java,
                missionBox.javaClass,
            ).also { it.isAccessible = true }
                .invoke(activity, image, divider, missionBox)
            ModuleFileLogger.i(TAG, "Reward ad self-test invoked playAdv chest_type=$chestType")
        }.onFailure { throwable ->
            if (attempt < 12 && !activity.isFinishing && !activity.isDestroyed) {
                ModuleFileLogger.w(TAG, "Reward ad self-test retry ${attempt + 1}: ${throwable.message}")
                activity.window.decorView.postDelayed({
                    runSyntheticEntry(activity, attempt + 1)
                }, 600L)
                return
            }
            ModuleFileLogger.e(TAG, "Reward ad self-test failed", throwable)
        }
    }

    private fun readClickListener(view: View): Any? {
        val listenerInfo = View::class.java.getDeclaredMethod("getListenerInfo")
            .also { it.isAccessible = true }
            .invoke(view)
        return listenerInfo.javaClass.getDeclaredField("mOnClickListener")
            .also { it.isAccessible = true }
            .get(listenerInfo)
    }

    private fun findMissionBoxFromClickListeners(view: View): Any? {
        readClickListener(view)?.let { listener ->
            listener.javaClass.declaredFields
                .onEach { it.isAccessible = true }
                .firstNotNullOfOrNull { field ->
                    if (field.type.name == CiweiMaoClasses.MISSION_BOX_DATA) {
                        field.get(listener)
                    } else {
                        null
                    }
                }?.let { return it }
        }
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            findMissionBoxFromClickListeners(group.getChildAt(index))?.let { return it }
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
        return null
    }
}
