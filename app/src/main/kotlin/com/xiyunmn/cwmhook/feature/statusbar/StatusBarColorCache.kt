package com.xiyunmn.cwmhook.feature.statusbar

import android.content.Context
import android.content.SharedPreferences

internal class StatusBarCacheKeys(
    private val cacheVersion: String,
    private val mainFrameActivity: String,
) {
    fun cacheKey(skinKey: String, sceneKey: String): String {
        return "$cacheVersion|$skinKey|$sceneKey"
    }

    fun colorPrefKey(cacheKey: String): String {
        return "color:$cacheKey"
    }

    fun activityPrefKey(skinKey: String, activityName: String): String {
        return "activity:$cacheVersion:$skinKey:$activityName"
    }

    fun mainFrameHomePrefKey(skinKey: String): String {
        return "home:$cacheVersion:$skinKey:$mainFrameActivity"
    }

    fun activityPart(sceneKey: String): String {
        return sceneKey.substringBefore('|', sceneKey)
    }

    fun isMainFrameScene(sceneKey: String): Boolean {
        return activityPart(sceneKey) == mainFrameActivity
    }

    fun isMainFrameStartupScene(sceneKey: String): Boolean {
        return sceneKey == "$mainFrameActivity|decor:no-id"
    }

    fun isMainFrameHomeScene(sceneKey: String): Boolean {
        return isMainFrameStartupScene(sceneKey) ||
            activityPart(sceneKey) == mainFrameActivity &&
            sceneKey.contains("|pager:androidx.viewpager.widget.ViewPager#viewPage@0")
    }

    fun legacyMainFrameHomeSceneKey(): String {
        return "$mainFrameActivity|pager:androidx.viewpager.widget.ViewPager#viewPage@0"
    }
}

internal class StatusBarColorCache(
    context: Context,
    private val keys: StatusBarCacheKeys,
    prefName: String,
    private val keyListName: String,
    private val maxSceneCacheSize: Int,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)

    fun get(skinKey: String, sceneKey: String): Int? {
        if (keys.isMainFrameStartupScene(sceneKey)) {
            mainFrameStartupColor(skinKey)?.let { return it }
        }
        val exactKey = keys.colorPrefKey(keys.cacheKey(skinKey, sceneKey))
        if (prefs.contains(exactKey)) {
            return prefs.getInt(exactKey, 0)
        }
        val activityKey = keys.activityPrefKey(skinKey, keys.activityPart(sceneKey))
        return if (!keys.isMainFrameScene(sceneKey) && prefs.contains(activityKey)) {
            prefs.getInt(activityKey, 0)
        } else {
            null
        }
    }

    fun put(skinKey: String, sceneKey: String, color: Int) {
        val cacheKey = keys.cacheKey(skinKey, sceneKey)
        val storedKeys = prefs.getString(keyListName, "").orEmpty()
            .split('\n')
            .filter { it.isNotEmpty() && it != cacheKey }
            .toMutableList()
        storedKeys.add(cacheKey)

        val editor = prefs.edit()
            .putInt(keys.colorPrefKey(cacheKey), color)
        if (!keys.isMainFrameScene(sceneKey)) {
            editor.putInt(keys.activityPrefKey(skinKey, keys.activityPart(sceneKey)), color)
        }
        if (keys.isMainFrameHomeScene(sceneKey)) {
            editor.putInt(keys.mainFrameHomePrefKey(skinKey), color)
        }
        while (storedKeys.size > maxSceneCacheSize) {
            val removed = storedKeys.removeAt(0)
            editor.remove(keys.colorPrefKey(removed))
        }
        editor.putString(keyListName, storedKeys.joinToString("\n")).apply()
    }

    private fun mainFrameStartupColor(skinKey: String): Int? {
        val homeKey = keys.mainFrameHomePrefKey(skinKey)
        if (prefs.contains(homeKey)) {
            return prefs.getInt(homeKey, 0)
        }
        val legacyHomeKey = keys.colorPrefKey(
            keys.cacheKey(
                skinKey,
                keys.legacyMainFrameHomeSceneKey(),
            ),
        )
        return if (prefs.contains(legacyHomeKey)) prefs.getInt(legacyHomeKey, 0) else null
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
