package com.xiyunmn.cwmhook.feature.glassbar

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import android.widget.RelativeLayout
import com.xiyunmn.cwmhook.config.glassbar.GlassBarsConfig
import com.xiyunmn.cwmhook.core.glass.GlassBackdropView
import com.xiyunmn.cwmhook.core.glass.GlassEffectsSpec
import com.xiyunmn.cwmhook.core.hostui.HostSkinPalette
import com.xiyunmn.cwmhook.core.hostui.HostSkinResolver
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A reversible background sibling: no host control is replaced or reparented. Main thread only. */
internal class GlassBarSession(
    val binding: GlassBarBinding,
    private val failure: (Throwable) -> Unit,
) : AutoCloseable {
    private val bar = binding.bar
    private val parent = binding.parent
    private val ownership = GlassViewOwnership()
    private val background = ownership.background(bar)
    private val sides = ownership.horizontalMargins(bar)
    private val bottom = ownership.bottomMargin(bar)
    private val bottomTranslation = ownership.own({ bar.translationY }, { bar.translationY = it })
    private val reserve = ownership.aboveRule(binding.source)
    private val decorations = binding.decorations.map(ownership::alpha)
    private val height = ownership.own({ bar.layoutParams.height }, { value ->
        bar.layoutParams = bar.layoutParams.apply { height = value }
    })
    private val primaryBackground = binding.primaryAction?.let(ownership::background)
    private val primaryTexts = (binding.primaryAction as? ViewGroup)?.let { action ->
        (0 until action.childCount).map(action::getChildAt).filterIsInstance<TextView>().map { text ->
            ownership.own({ text.textColors }, { text.setTextColor(it) })
        }
    }.orEmpty()
    private val content = GlassContentInsets(binding)
    internal val glass = GlassBackdropView(bar.context, binding.source) { error ->
        ModuleFileLogger.w(TAG, "Glass material fallback: kind=${binding.kind}", error)
    }
    private val selection = binding.tabs?.let { GlassTabSelection(it, glass) }
    private val parentLocation = IntArray(2)
    private val rootLocation = IntArray(2)
    private val barLocation = IntArray(2)
    private val visibleFrame = Rect()
    private val visibleBar = Rect()
    private val reportedBounds = Rect()
    private var reportedViewport = -1
    private var observer: ViewTreeObserver? = null
    private var effects = GlassEffectsSpec()
    private var skinKey: String? = null
    private var night = false
    private var primaryTint: InsetDrawable? = null
    private var primaryTextColor: ColorStateList? = null
    private var applying = false
    private var active = false
    var closed = false
        private set
    private val cleanupHandler = Handler(Looper.getMainLooper())
    private val removeLayer = object : Runnable {
        override fun run() {
            ModuleViewTaskRegistry.untrack(this)
            (glass.parent as? ViewGroup)?.removeView(glass)
        }
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { refreshLayout() }
    private val drawListener = ViewTreeObserver.OnPreDrawListener {
        if (!closed) {
            runCatching {
                applyAppearance()
                mirrorBar()
            }.onFailure(failure)
        }
        true
    }
    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) {
            if (closed) return
            release()
            // ViewGroup is still iterating its child array. Remove the sibling after it returns.
            ModuleViewTaskRegistry.track(cleanupHandler, removeLayer)
            cleanupHandler.post(removeLayer)
        }
    }

    init {
        require(!bar.isWithin(binding.source) && !binding.source.isWithin(bar)) { "Cyclic glass source" }
    }

    fun attach(config: GlassBarsConfig, active: Boolean) {
        check(!closed)
        glass.tag = "cwmhook_glass_bottom_bar"
        // RelativeLayout caps even explicit child widths to its available space. Reserve the
        // offscreen halo in the margins so it cannot crop the material's right/bottom edges.
        parent.addView(glass, parent.indexOfChild(bar), RelativeLayout.LayoutParams(0, 0).apply {
            setMargins(-glass.halo, -glass.halo, -glass.halo, -glass.halo)
        })
        observer = parent.viewTreeObserver.also {
            it.addOnGlobalLayoutListener(layoutListener)
            it.addOnPreDrawListener(drawListener)
        }
        bar.addOnAttachStateChangeListener(detachListener)
        if (binding.kind == GlassBarKind.MAIN) {
            // Native tab icons grow into the RadioGroup's 5dp top padding at 1.2x scale.
            // The glass draws its own capsule; its mask must not also crop the host controls.
            ownership.own({ bar.clipToPadding }, { bar.clipToPadding = it }).set(false)
            ownership.own({ bar.clipChildren }, { bar.clipChildren = it }).set(false)
            ownership.own({ bar.clipToOutline }, { bar.clipToOutline = it }).set(false)
        } else {
            ownership.own({ bar.outlineProvider }, { bar.outlineProvider = it }).set(object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, min(view.width, view.height) / 2f)
                }
            })
            ownership.own({ bar.clipToOutline }, { bar.clipToOutline = it }).set(true)
        }
        update(config)
        setActive(active)
    }

    fun update(config: GlassBarsConfig) {
        if (closed) return
        selection?.cancelTouch()
        effects = GlassEffectsSpec(config.blur, config.refraction, config.highlight, config.shadow, config.tilt, config.press)
        skinKey = null
        refreshLayout()
    }

    fun setActive(value: Boolean) {
        if (closed) return
        active = value
        if (value) refreshLayout() else {
            selection?.cancelTouch()
            glass.setActive(false)
        }
    }

    fun dispatchTouch(event: MotionEvent, dispatchNative: (MotionEvent) -> Boolean): Boolean {
        if (!closed && active && selection != null) return selection.dispatchTouch(event, dispatchNative)
        val result = dispatchNative(event)
        if (!closed) glass.observeTouch(event.actionMasked, event.rawX, event.rawY)
        return result
    }

    private fun refreshLayout() {
        if (closed || applying || bar.parent !== parent) return
        applying = true
        try {
            applyAppearance()
            height.update { original -> max(original.coerceAtLeast(0), bar.dp(64)) }
            val root = parent.rootView
            root.getLocationOnScreen(rootLocation)
            parent.getLocationOnScreen(parentLocation)
            val parentBottom = parentLocation[1] + parent.height - parent.paddingBottom
            val rootBottom = rootLocation[1] + root.height
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val frameBottom = visibleFrame.bottom.takeIf { it > rootLocation[1] } ?: rootBottom
            val viewportBottom = min(GlassGeometry.viewportBottom(parentBottom, rootBottom), frameBottom)
            val windowBottom = if (Build.VERSION.SDK_INT >= 30) {
                runCatching { bar.context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds?.bottom }
                    .getOrNull() ?: rootBottom
            } else rootBottom
            val insets = parent.rootWindowInsets
            val navigation = if (Build.VERSION.SDK_INT >= 30) {
                insets?.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())?.bottom ?: 0
            } else {
                @Suppress("DEPRECATION")
                insets?.stableInsetBottom ?: 0
            }
            val consumed = GlassGeometry.consumedBottom(windowBottom, rootBottom, viewportBottom)
            bottom.update { original ->
                GlassGeometry.bottomMargin(original, navigation, consumed, bar.dp(12), parentBottom - viewportBottom)
            }
            val available = (parent.width - parent.paddingLeft - parent.paddingRight).coerceAtLeast(0)
            if (available > 0) {
                val requested = if (binding.kind == GlassBarKind.MAIN) (available * 0.80f).roundToInt() else available - bar.dp(32)
                val side = GlassGeometry.widthSideMargin(available, minimumWidth(), 0, min(requested, bar.dp(560)))
                sides.first.update { max(it, side) }
                sides.second.update { max(it, side) }
            }
            reserve.set(0)
            correctBottomPosition()
            bar.getLocationOnScreen(barLocation)
            content.update(barLocation[1], viewportBottom, bar.isShown)
            reportGeometry(viewportBottom, navigation)
            mirrorBar()
        } catch (error: Exception) {
            failure(error)
        } finally {
            applying = false
        }
    }

    private fun minimumWidth(): Int {
        val tabs = binding.tabs
        if (tabs != null) {
            val visible = (0 until tabs.childCount).map(tabs::getChildAt).filter { it.visibility != View.GONE }
            val column = visible.filterIsInstance<TextView>().maxOfOrNull {
                max(bar.dp(52), it.paint.measureText(it.text.toString()).roundToInt() + bar.dp(20))
            } ?: bar.dp(52)
            return visible.size * column + bar.paddingLeft + bar.paddingRight
        }
        val actions = if (binding.kind == GlassBarKind.CATALOG) {
            (0 until bar.childCount).count { bar.getChildAt(it) is ViewGroup && bar.getChildAt(it).visibility != View.GONE }
        } else 3
        return actions * bar.dp(if (binding.kind == GlassBarKind.CATALOG) 96 else 72)
    }

    private fun applyAppearance() {
        val key = HostSkinResolver.currentSkinKey(bar.context)
        if (skinKey != key || bar.background != null) {
            skinKey = key
            val palette = HostSkinPalette.from(bar.context)
            night = palette.night
            val accent = palette.accent
            primaryTint = InsetDrawable(GradientDrawable().apply {
                cornerRadius = bar.dp(32).toFloat()
                setColor(Color.argb(if (night) 72 else 48, Color.red(accent), Color.green(accent), Color.blue(accent)))
            }, bar.dp(5))
            primaryTextColor = ColorStateList.valueOf(palette.primaryText)
        }
        // Rebase ownership if a host skin update wrote new backgrounds or text colors.
        background.set(null)
        decorations.forEach { it.set(0f) }
        primaryTint?.let { primaryBackground?.set(it) }
        primaryTextColor?.let { color -> primaryTexts.forEach { it.set(color) } }
    }

    private fun mirrorBar() {
        correctBottomPosition()
        glass.visibility = if (bar.visibility == View.VISIBLE) View.VISIBLE else View.INVISIBLE
        // A ViewPager page can be VISIBLE while completely outside the viewport.
        glass.setActive(active && bar.isShown && bar.alpha > 0f && bar.getGlobalVisibleRect(visibleBar))
        if (bar.width <= 0 || bar.height <= 0) return
        val width = bar.width + 2 * glass.halo
        val height = bar.height + 2 * glass.halo
        if (glass.layoutParams.width != width || glass.layoutParams.height != height) {
            glass.layoutParams = glass.layoutParams.apply { this.width = width; this.height = height }
        }
        glass.translationX = bar.x - glass.left - glass.halo
        glass.translationY = bar.y - glass.top - glass.halo
        glass.elevation = bar.elevation
        glass.translationZ = bar.translationZ
        glass.alpha = bar.alpha
        glass.configure(bar.width, bar.height, effects, night)
        selection?.update()
    }

    private fun correctBottomPosition() {
        val margin = (bar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        val expectedBottom = parent.height - parent.paddingBottom - margin
        // In a wrap_content RelativeLayout (book detail), the bottom-alignment pass can ignore
        // bottomMargin. Correct only the missing lift and preserve the host's own animation.
        bottomTranslation.update { original -> original + min(0, expectedBottom - bar.bottom) }
    }

    private fun reportGeometry(viewportBottom: Int, navigation: Int) {
        if (bar.width <= 0 || bar.height <= 0) return
        val x = barLocation[0]
        val y = barLocation[1]
        if (reportedBounds.left == x && reportedBounds.top == y && reportedBounds.width() == bar.width &&
            reportedBounds.height() == bar.height && reportedViewport == viewportBottom) return
        reportedBounds.set(x, y, x + bar.width, y + bar.height)
        reportedViewport = viewportBottom
        ModuleFileLogger.i(TAG, "Glass geometry: kind=${binding.kind}, bar=$reportedBounds, " +
            "viewportBottom=$viewportBottom, nav=$navigation, parent=${parent.width}x${parent.height}, " +
            "bottomMargin=${(bar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin}, " +
            "translationY=${bar.translationY}, glass=${glass.width}x${glass.height}")
    }

    override fun close() {
        release()
        cleanupHandler.removeCallbacks(removeLayer)
        removeLayer.run()
    }

    private fun release() {
        if (closed) return
        closed = true
        observer?.takeIf { it.isAlive }?.let {
            it.removeOnGlobalLayoutListener(layoutListener)
            it.removeOnPreDrawListener(drawListener)
        }
        observer = null
        bar.removeOnAttachStateChangeListener(detachListener)
        selection?.close()
        glass.close()
        content.close()
        ownership.close()
    }

    private companion object { const val TAG = "CWMHook.GlassBars" }
}
