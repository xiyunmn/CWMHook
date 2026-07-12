package com.xiyunmn.cwmhook.feature.statusbar

import com.xiyunmn.cwmhook.host.CiweiMaoClasses

internal class StatusBarSceneRules(
    private val keys: StatusBarCacheKeys,
    private val readerActivity: String,
) {
    fun isSpecialColorScene(sceneKey: String): Boolean {
        return isMainFrameScene(sceneKey) || isReaderScene(sceneKey) || isBookDetailScene(sceneKey)
    }

    fun directColorSource(sceneKey: String, directColor: Boolean): String {
        if (!directColor) return "target rendered sample"
        return when {
            isBookStoreHomeScene(sceneKey) -> "bookstore target background"
            isBookShelfTabScene(sceneKey) -> "bookshelf title background"
            isMainFrameThemeTopScene(sceneKey) -> "main tab title background"
            isMyTabScene(sceneKey) -> "my header sample"
            isReaderScene(sceneKey) -> "reader menu sample"
            isBookDetailScene(sceneKey) -> "book detail top state"
            else -> "activity title background"
        }
    }

    fun isBookStoreHomeScene(sceneKey: String): Boolean = sceneKey == "main-tab:store"

    fun isDirectColorScene(sceneKey: String): Boolean {
        return isBookStoreHomeScene(sceneKey) || isBookShelfTabScene(sceneKey) || isMainFrameThemeTopScene(sceneKey)
    }

    fun isMainFrameThemeTopScene(sceneKey: String): Boolean {
        return sceneKey == "main-tab:rank" || sceneKey == "main-tab:find"
    }

    fun isBookShelfTabScene(sceneKey: String): Boolean = sceneKey == "main-tab:shelf"

    fun isGenericMainFrameScene(sceneKey: String): Boolean = isMainFrameScene(sceneKey)

    fun isMyTabScene(sceneKey: String): Boolean = sceneKey == "main-tab:mine"

    fun isMainFrameScene(sceneKey: String): Boolean = sceneKey.startsWith("main-tab:")

    fun isReaderActivity(activityName: String): Boolean {
        return activityName == readerActivity || activityName == "${readerActivity}_LANDSCAPE"
    }

    fun isReaderScene(sceneKey: String): Boolean {
        val activityName = sceneKey.substringBefore('|', sceneKey)
        return isReaderActivity(activityName)
    }

    fun isBookDetailScene(sceneKey: String): Boolean {
        return sceneKey.substringBefore('|', sceneKey) == CiweiMaoClasses.BOOK_DETAIL_ACTIVITY
    }
}
