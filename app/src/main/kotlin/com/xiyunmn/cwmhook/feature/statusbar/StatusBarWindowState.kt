package com.xiyunmn.cwmhook.feature.statusbar

internal class StatusBarWindowState(
    private val cacheKeyBuilder: (skinKey: String, sceneKey: String) -> String,
    private val maxMemoryColors: Int,
) {
    var activeSceneKey: String = "window"
    var activeSkinKey: String = ""
    var cachedColor: Int? = null
    var colorDirty: Boolean = true
    var pendingApply: Boolean = false
    var pendingSceneKey: String? = null
    var pendingSample: Boolean = false
    var lastSampleAt: Long = 0L
    var hasFocus: Boolean = false
    var everFocused: Boolean = false
    var readerOriginalWindowState: ReaderOriginalWindowState? = null
    private val sceneColors: LinkedHashMap<String, Int> = LinkedHashMap()

    fun setFocus(focused: Boolean) {
        hasFocus = focused
        everFocused = everFocused || focused
    }

    fun activate(sceneKey: String, skinKey: String, persistedColor: Int?): Boolean {
        if (activeSceneKey == sceneKey && activeSkinKey == skinKey) {
            if (cachedColor == null && persistedColor != null) {
                cachedColor = persistedColor
                colorDirty = false
                return true
            }
            return cachedColor != null
        }

        cachedColor?.let { rememberActiveColor(it) }
        activeSceneKey = sceneKey
        activeSkinKey = skinKey

        val memoryColor = sceneColors[cacheKeyBuilder(skinKey, sceneKey)]
        cachedColor = memoryColor ?: persistedColor
        colorDirty = cachedColor == null
        return cachedColor != null
    }

    fun markDirty(clearCached: Boolean = false) {
        colorDirty = true
        if (clearCached) {
            cachedColor = null
        }
    }

    fun isCleanFor(sceneKey: String, skinKey: String): Boolean {
        return activeSceneKey == sceneKey &&
            activeSkinKey == skinKey &&
            cachedColor != null &&
            !colorDirty &&
            !pendingSample
    }

    fun rememberActiveColor(color: Int) {
        val key = cacheKeyBuilder(activeSkinKey, activeSceneKey)
        sceneColors.remove(key)
        sceneColors[key] = color
        while (sceneColors.size > maxMemoryColors) {
            sceneColors.entries.iterator().also {
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
    }
}
