package com.xiyunmn.cwmhook.feature.statusbar

import com.xiyunmn.cwmhook.host.CiweiMaoClasses

internal class StatusBarSceneRules(
    private val keys: StatusBarCacheKeys,
    private val readerActivity: String,
) {
    private val recommendFragmentMarker = "|fragment:${CiweiMaoClasses.RECOMMEND_FRAGMENT}@"
    private val recommendViewsFragmentMarker = "|fragment:${CiweiMaoClasses.RECOMMEND_VIEW_FRAGMENT_PREFIX}"
    private val rankFragmentMarker = "|fragment:${CiweiMaoClasses.RANK_FRAGMENT}@"
    private val findFragmentMarker = "|fragment:${CiweiMaoClasses.FIND_FRAGMENT}@"
    private val bookShelfFragmentMarker = "|fragment:${CiweiMaoClasses.BOOK_SHELF_FRAGMENT}@"
    private val mineFragmentMarker = "|fragment:${CiweiMaoClasses.MINE_FRAGMENT}@"

    fun isSpecialColorScene(sceneKey: String): Boolean {
        return isDirectColorScene(sceneKey) ||
            isMyTabScene(sceneKey) ||
            isReaderScene(sceneKey)
    }

    fun directColorSource(sceneKey: String, directColor: Boolean): String {
        if (!directColor) {
            return "rendered sample"
        }
        return when {
            isBookStoreHomeScene(sceneKey) -> "bookstore surface color"
            isBookShelfTabScene(sceneKey) -> "bookshelf surface color"
            isMainFrameThemeTopScene(sceneKey) -> "main tab theme color"
            isMyTabScene(sceneKey) -> "my header sample"
            isReaderScene(sceneKey) -> "reader menu sample"
            else -> "background scan"
        }
    }

    fun isBookStoreHomeScene(sceneKey: String): Boolean {
        return keys.isMainFrameHomeScene(sceneKey) ||
            sceneKey.contains(recommendFragmentMarker) ||
            sceneKey.contains(recommendViewsFragmentMarker)
    }

    fun isDirectColorScene(sceneKey: String): Boolean {
        return isBookStoreHomeScene(sceneKey) || isBookShelfTabScene(sceneKey) || isMainFrameThemeTopScene(sceneKey)
    }

    fun isMainFrameThemeTopScene(sceneKey: String): Boolean {
        if (!keys.isMainFrameScene(sceneKey) || isMyTabScene(sceneKey)) {
            return false
        }
        return sceneKey.contains(rankFragmentMarker) || sceneKey.contains(findFragmentMarker)
    }

    fun isBookShelfTabScene(sceneKey: String): Boolean {
        return keys.isMainFrameScene(sceneKey) && sceneKey.contains(bookShelfFragmentMarker)
    }

    fun isGenericMainFrameScene(sceneKey: String): Boolean {
        val marker = sceneKey.substringAfter('|', "")
        return keys.isMainFrameScene(sceneKey) &&
            !marker.startsWith("fragment:") &&
            (marker.startsWith("pager:") || marker.startsWith("decor:"))
    }

    fun isMyTabScene(sceneKey: String): Boolean {
        return sceneKey.contains(mineFragmentMarker)
    }

    fun isReaderScene(sceneKey: String): Boolean {
        return keys.activityPart(sceneKey).let { it == readerActivity || it == "${readerActivity}_LANDSCAPE" }
    }
}
