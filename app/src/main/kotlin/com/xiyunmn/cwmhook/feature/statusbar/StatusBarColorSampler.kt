package com.xiyunmn.cwmhook.feature.statusbar

import android.util.Log
import android.view.View
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import kotlin.math.max

internal class StatusBarColorSampler(
    private val viewTreeTools: StatusBarViewTreeTools,
    private val scrimController: StatusBarScrimController,
    private val maxBackgroundScanViews: Int,
    private val sampleBitmapWidth: Int,
    private val sampleBitmapHeight: Int,
    private val logTag: String,
) {
    private val renderedSampler = StatusBarRenderedColorSampler(sampleBitmapWidth, sampleBitmapHeight)

    fun sampleMyHeaderColor(appRoot: View, topInset: Int): Int? {
        val header = viewTreeTools.findViewByResourceName(appRoot, "mypersoninfo") ?: return null
        if (!header.isShown || header.width <= 0 || header.height <= 0) {
            return null
        }
        val density = header.resources.displayMetrics.density
        val positions = listOf(
            (8f * density).toInt(),
            (topInset / 2).coerceAtLeast(1),
            (24f * density).toInt(),
        ).map { it.coerceIn(1, max(1, header.height - 1)) }.distinct()
        positions.forEach { y ->
            sampleRenderedStrip(header, y)?.let { return it }
        }
        return null
    }

    fun sampleReaderMenuColor(appRoot: View, topInset: Int): Int? {
        val titleBar = viewTreeTools.findViewByResourceName(appRoot, "titleBar")?.takeIf { it.isShown } ?: return null
        val title = viewTreeTools.findViewByResourceName(titleBar, "title")?.takeIf { it.isShown } ?: titleBar
        scrimController.solidBackgroundColor(title)?.let { return it }
        val sampleY = minOf(max(1, topInset / 3), max(1, title.height - 1))
        return sampleRenderedStrip(title, sampleY)
    }

    fun scanTopBackgroundColor(appRoot: View, topInset: Int): Int? {
        if (!appRoot.isShown || appRoot.width <= 0 || appRoot.height <= 0) {
            return null
        }

        val rootWidth = positiveOrFallback(appRoot.width, appRoot.resources.displayMetrics.widthPixels)
        val rootHeight = positiveOrFallback(appRoot.height, appRoot.resources.displayMetrics.heightPixels)
        val bandTop = topInset
        val bandBottom = topInset + max(topInset, (64f * appRoot.resources.displayMetrics.density).toInt())
        val anchorY = topInset + max(2, topInset / 8)
        var best: BackgroundCandidate? = null

        viewTreeTools.traverseViewTreeWithDepth(appRoot, maxBackgroundScanViews) { view, depth ->
            val color = scrimController.solidBackgroundColor(view) ?: return@traverseViewTreeWithDepth
            if (scrimController.isScrim(view) || view.id == android.R.id.statusBarBackground) {
                return@traverseViewTreeWithDepth
            }
            if (!view.isShown || view.alpha < 0.92f) {
                return@traverseViewTreeWithDepth
            }

            val width = positiveOrFallback(view.width, view.measuredWidth)
            val height = positiveOrFallback(view.height, view.measuredHeight)
            if (width < rootWidth * 0.55f || height < max(6, topInset / 5)) {
                return@traverseViewTreeWithDepth
            }

            val top = viewTreeTools.topRelativeToRoot(view, appRoot)
            val bottom = top + height
            val overlap = minOf(bottom, bandBottom) - maxOf(top, bandTop)
            val coversAnchor = top <= anchorY && bottom >= anchorY
            if (overlap <= 0 && !coversAnchor) {
                return@traverseViewTreeWithDepth
            }

            val widthScore = (width.coerceAtMost(rootWidth) * 240) / max(1, rootWidth)
            val overlapScore = (overlap.coerceAtLeast(0).coerceAtMost(bandBottom - bandTop) * 180) /
                max(1, bandBottom - bandTop)
            val anchorScore = if (coversAnchor) 180 else 0
            val depthScore = depth * 36
            val distancePenalty = kotlin.math.abs(top - topInset) / 2
            val rootPlatePenalty = if (depth <= 1 && height > rootHeight * 0.72f) 170 else 0
            val fullHeightPenalty = if (height > rootHeight * 0.9f) 70 else 0
            val score = widthScore + overlapScore + anchorScore + depthScore - distancePenalty -
                rootPlatePenalty - fullHeightPenalty

            if (best == null || score > best!!.score) {
                best = BackgroundCandidate(
                    color = color,
                    score = score,
                    description = "${view.javaClass.name}#${viewTreeTools.resourceEntryName(view)}" +
                        "[${width}x$height,top=$top,depth=$depth]",
                )
            }
        }
        best?.let {
            ModuleFileLogger.throttled(
                key = "bgcandidate:${System.identityHashCode(appRoot)}",
                intervalMs = 500L,
                priority = Log.DEBUG,
                tag = logTag,
                message = "background candidate color=${formatColor(it.color)} score=${it.score} view=${it.description}",
            )
        }
        return best?.color
    }

    fun sampleRenderedStatusBarColor(appRoot: View, topInset: Int): Int? {
        if (!appRoot.isShown || appRoot.width <= 0 || appRoot.height <= 0) {
            return null
        }
        val last = appRoot.height - 1
        val positions = listOf(
            (topInset / 2).coerceIn(1, last),
            (topInset + 2).coerceIn(1, last),
            1,
        ).distinct()
        positions.forEach { sampleY ->
            sampleRenderedStrip(appRoot, sampleY)?.let { return it }
        }
        return null
    }

    private fun sampleRenderedStrip(appRoot: View, sampleY: Int): Int? {
        return renderedSampler.sample(appRoot, sampleY)
    }

    private fun positiveOrFallback(value: Int, fallback: Int): Int {
        return if (value > 0) value else max(fallback, 0)
    }

    private fun formatColor(color: Int?): String {
        return color?.let { "#%08X".format(java.util.Locale.US, it) } ?: "null"
    }
}
