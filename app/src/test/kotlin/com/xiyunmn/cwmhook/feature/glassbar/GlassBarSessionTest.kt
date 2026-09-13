package com.xiyunmn.cwmhook.feature.glassbar

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ExpandableListView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import com.xiyunmn.cwmhook.config.glassbar.GlassBarsConfig
import com.xiyunmn.cwmhook.core.glass.GlassBackdropView
import com.xiyunmn.cwmhook.host.CiweiMaoIds
import com.xiyunmn.cwmhook.host.CiweiMaoPackages
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Native layout contracts; GPU appearance and the host's hook dispatch require device validation. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class GlassBarSessionTest {
    private val activityController = Robolectric.buildActivity(Activity::class.java).apply {
        get().setTheme(android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
    }.setup()
    private val activity = activityController.get()
    private val ids = mutableMapOf<String, Int>()
    private val parent = RelativeLayout(activity)
    private lateinit var bar: ViewGroup
    private lateinit var source: View
    private lateinit var scroll: ViewGroup
    private var divider: View? = null
    private var primary: RelativeLayout? = null
    private var session: GlassBarSession? = null
    private var parentTop = 0
    private var screenWidth = 400
    private var screenHeight = 800
    private var originalHeight = 0
    private var wrapParent = false
    private val nativeBackground = ColorDrawable(Color.WHITE)
    private val config = GlassBarsConfig(
        mainEnabled = true, detailEnabled = true, catalogEnabled = true,
        blur = false, refraction = false, highlight = false, shadow = false, tilt = false, press = false,
    )

    private fun named(view: View, name: String): View = view.apply {
        id = ids.getOrPut(name) { View.generateViewId() }
    }

    private fun resolve(kind: GlassBarKind) = GlassBarBindingResolver.resolve(kind, parent) { root, name ->
        ids[name]?.let { root.findViewById(it) }
    }

    private fun scrollingPage(): ScrollView = ScrollView(activity).apply {
        setPadding(2, 3, 4, 7)
        addView(View(activity), ViewGroup.LayoutParams(400, 1800))
    }

    private fun create(kind: GlassBarKind = GlassBarKind.MAIN, attach: Boolean = true) {
        wrapParent = kind == GlassBarKind.DETAIL
        when (kind) {
            GlassBarKind.MAIN -> {
                bar = RadioGroup(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    named(this, CiweiMaoIds.MAIN_TAB_GROUP)
                    setPadding(0, 5, 0, 0)
                    repeat(5) { index ->
                        addView(RadioButton(activity).apply {
                            named(this, "tabbtn$index")
                            text = listOf("推荐", "分类", "书架", "发现", "我的")[index]
                            setButtonDrawable(null)
                        }, RadioGroup.LayoutParams(0, -1, 0.2f))
                    }
                    check(getChildAt(2).id)
                }
                scroll = scrollingPage()
                source = FrameLayout(activity).apply {
                    named(this, CiweiMaoIds.MAIN_TAB_CONTENT)
                    addView(scroll, FrameLayout.LayoutParams(-1, -1))
                }
                originalHeight = 50
            }
            GlassBarKind.DETAIL -> {
                bar = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    named(this, CiweiMaoIds.BOOK_DETAIL_BOTTOM_BAR)
                    divider = View(activity).also { addView(it, LinearLayout.LayoutParams(-1, 1)) }
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        repeat(3) { index ->
                            addView(RelativeLayout(activity).apply {
                                if (index == 1) {
                                    primary = this
                                    named(this, CiweiMaoIds.BOOK_DETAIL_READ_ACTION)
                                    background = ColorDrawable(Color.YELLOW)
                                }
                                addView(TextView(activity).apply { text = "Action $index" },
                                    RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.CENTER_IN_PARENT) })
                            }, LinearLayout.LayoutParams(0, -1, 1f))
                        }
                    }, LinearLayout.LayoutParams(-1, -1))
                }
                scroll = scrollingPage()
                source = named(scroll, CiweiMaoIds.BOOK_DETAIL_SCROLL)
                originalHeight = 57
            }
            GlassBarKind.CATALOG -> {
                bar = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    named(this, CiweiMaoIds.CATALOG_BOTTOM_BAR)
                    addView(RelativeLayout(activity), LinearLayout.LayoutParams(-1, -1, 1f))
                    addView(View(activity), LinearLayout.LayoutParams(1, 40))
                    addView(named(RelativeLayout(activity), CiweiMaoIds.CATALOG_DOWNLOAD_ACTION),
                        LinearLayout.LayoutParams(-1, -1, 1f))
                }
                val header = View(activity).apply { id = View.generateViewId() }
                parent.addView(header, RelativeLayout.LayoutParams(-1, 49))
                divider = named(View(activity), CiweiMaoIds.CATALOG_BOTTOM_DIVIDER).also {
                    parent.addView(it, RelativeLayout.LayoutParams(-1, 1).apply { addRule(RelativeLayout.ABOVE, bar.id) })
                }
                scroll = ExpandableListView(activity).apply { setPadding(2, 3, 4, 7) }
                source = named(scroll, CiweiMaoIds.CATALOG_LIST).apply {
                    layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, header.id) }
                }
                originalHeight = 48
            }
        }
        val params = source.layoutParams as? RelativeLayout.LayoutParams ?: RelativeLayout.LayoutParams(-1, -1)
        params.addRule(RelativeLayout.ABOVE, if (kind == GlassBarKind.CATALOG) requireNotNull(divider).id else bar.id)
        parent.addView(source, params)
        bar.background = nativeBackground
        parent.addView(bar, RelativeLayout.LayoutParams(-1, originalHeight).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) })
        activity.setContentView(parent, ViewGroup.LayoutParams(-1, if (wrapParent) -2 else -1))
        settle()
        if (attach) attach(kind)
    }

    private fun attach(kind: GlassBarKind) {
        session = GlassBarSession(requireNotNull(resolve(kind))) { throw AssertionError("Glass layout failed", it) }
            .also { it.attach(config, true) }
        settle()
    }

    private fun settle(times: Int = 5) {
        repeat(times) {
            activity.window.decorView.apply {
                measure(exact(screenWidth), exact(screenHeight))
                layout(0, 0, screenWidth, screenHeight)
            }
            val heightSpec = View.MeasureSpec.makeMeasureSpec(screenHeight,
                if (wrapParent) View.MeasureSpec.AT_MOST else View.MeasureSpec.EXACTLY)
            parent.measure(exact(screenWidth), heightSpec)
            parent.layout(0, parentTop, screenWidth, parentTop + parent.measuredHeight)
            parent.viewTreeObserver.dispatchOnGlobalLayout()
            parent.viewTreeObserver.dispatchOnPreDraw()
        }
    }

    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    private fun barParams() = bar.layoutParams as RelativeLayout.LayoutParams
    private fun layer() = requireNotNull(session).glass

    @After
    fun tearDown() {
        session?.close()
        activityController.pause().stop().destroy()
    }

    @Test
    fun mainPreservesNativeControlsAndRestoresLayoutAfterRepeatedUpdates() {
        create()
        val tabs = bar as RadioGroup
        val button = tabs.getChildAt(0)
        val id = button.id
        var clicks = 0
        button.setOnClickListener { clicks++ }
        assertEquals(parent.indexOfChild(bar) - 1, parent.indexOfChild(layer()))
        assertFalse(layer().isClickable)
        assertFalse(layer().isFocusable)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, layer().importantForAccessibility)
        assertEquals(800, source.height)
        assertEquals(64, bar.height)
        assertEquals(40, barParams().leftMargin)
        assertEquals(12, barParams().bottomMargin)
        assertEquals(7 + 64 + 12, scroll.paddingBottom)
        assertFalse(scroll.clipToPadding)
        assertNull(bar.background)
        assertFalse(bar.clipToOutline)
        assertFalse(bar.clipToPadding)
        assertFalse(bar.clipChildren)
        repeat(3) { session?.update(config); settle(8) }
        assertEquals(83, scroll.paddingBottom)
        assertEquals(1, (0 until parent.childCount).count { parent.getChildAt(it) is GlassBackdropView })
        button.performClick()
        assertEquals(id, tabs.checkedRadioButtonId)
        assertEquals(1, clicks)
        session?.close()
        session?.close()
        settle()
        assertSame(tabs, button.parent)
        assertEquals(id, button.id)
        assertEquals(5, tabs.childCount)
        assertEquals(0, (0 until parent.childCount).count { parent.getChildAt(it) is GlassBackdropView })
        assertEquals(bar.id, (source.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.ABOVE))
        assertEquals(originalHeight, bar.height)
        assertEquals(0, barParams().bottomMargin)
        assertEquals(0, barParams().leftMargin)
        assertEquals(7, scroll.paddingBottom)
        assertTrue(scroll.clipToPadding)
        assertFalse(bar.clipToOutline)
        assertTrue(bar.clipToPadding)
        assertTrue(bar.clipChildren)
        assertSame(nativeBackground, bar.background)
        button.performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun mainDetachRestoresTheHostsOwnClippingPolicyAndOutline() {
        create(attach = false)
        bar.outlineProvider = ViewOutlineProvider.BOUNDS
        bar.clipToOutline = true
        bar.clipChildren = false
        bar.clipToPadding = true
        attach(GlassBarKind.MAIN)
        assertFalse(bar.clipToOutline)
        assertFalse(bar.clipChildren)
        assertFalse(bar.clipToPadding)
        assertSame(ViewOutlineProvider.BOUNDS, bar.outlineProvider)
        parent.removeView(bar)
        assertTrue(requireNotNull(session).closed)
        assertTrue(bar.clipToOutline)
        assertFalse(bar.clipChildren)
        assertTrue(bar.clipToPadding)
        assertSame(ViewOutlineProvider.BOUNDS, bar.outlineProvider)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun enlargedEdgeTabIconsRenderFullyAcrossTheNativeTopPadding() {
        create(attach = false)
        val tabs = bar as RadioGroup
        tabs.getChildAt(1).visibility = View.GONE
        for (index in 0 until tabs.childCount) {
            (tabs.getChildAt(index) as RadioButton).apply {
                // Match the host: a top compound icon, 2dp button padding, 5dp group padding.
                background = null
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setPadding(0, 2, 0, 0)
                compoundDrawablePadding = 3
                textSize = 10f
                setTextColor(Color.BLACK)
                val icon = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.MAGENTA)
                    setBounds(0, 0, 24, 24)
                }
                setCompoundDrawables(null, icon, null, null)
            }
        }
        tabs.check(tabs.getChildAt(0).id)
        attach(GlassBarKind.MAIN)
        session?.update(config.copy(press = true))
        for (index in listOf(0, tabs.childCount - 1)) {
            val tab = tabs.getChildAt(index) as RadioButton
            tabs.check(tab.id)
            settle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            val location = IntArray(2)
            tab.getLocationOnScreen(location)
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    location[0] + tab.width / 2f, location[1] + tab.height / 2f, 0)
                try { session?.dispatchTouch(event, activity::dispatchTouchEvent) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            assertEquals(1.2f, tab.scaleX, 0.001f)
            assertEquals(1.2f, tab.scaleY, 0.001f)
            val expected = Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)
            val actual = Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)
            try {
                // Compare the real parent draw with the same native tab drawn without parent clips.
                Canvas(expected).apply {
                    translate(tab.left.toFloat(), tab.top.toFloat())
                    concat(tab.matrix)
                    tab.draw(this)
                }
                bar.draw(Canvas(actual))
                var iconTop = bar.height
                for (y in 0 until bar.height) for (x in 0 until bar.width) {
                    val pixel = expected.getPixel(x, y)
                    if (Color.alpha(pixel) > 0 && (pixel and 0x00FFFFFF) == 0x00FF00FF) {
                        iconTop = minOf(iconTop, y)
                        assertEquals("Enlarged ${tab.text} icon clipped at ($x, $y)", pixel, actual.getPixel(x, y))
                    }
                }
                assertTrue("The enlarged icon must cross the native padding edge", iconTop < bar.paddingTop)
            } finally {
                expected.recycle()
                actual.recycle()
                touch(MotionEvent.ACTION_CANCEL)
            }
            assertEquals(1f, tab.scaleX, 0f)
            assertEquals(1f, tab.scaleY, 0f)
        }
    }

    @Test
    fun detailRetainsPrimaryActionAndLatestHostSkinOnDisable() {
        create(GlassBarKind.DETAIL)
        assertTrue(bar.clipToOutline)
        val action = requireNotNull(primary)
        val text = action.getChildAt(0) as TextView
        val nightBackground = ColorDrawable(Color.DKGRAY)
        val hostActionBackground = ColorDrawable(Color.RED)
        val hostTextColors = ColorStateList.valueOf(Color.CYAN)
        activity.getSharedPreferences(CiweiMaoPackages.DEFAULT_PREF, 0).edit().putBoolean("isNight", true).commit()
        bar.background = nightBackground
        action.background = hostActionBackground
        text.setTextColor(hostTextColors)
        action.isEnabled = false
        scroll.setPadding(8, 9, 10, 20)
        settle()
        assertEquals(800, source.height)
        assertEquals(20 + 76, scroll.paddingBottom)
        assertEquals(16, barParams().leftMargin)
        assertEquals(788f, bar.y + bar.height, 0f)
        assertEquals(bar.width + 2 * layer().halo, layer().width)
        assertEquals(0f, requireNotNull(divider).alpha, 0f)
        assertNotSame(hostActionBackground, action.background)
        session?.close()
        settle()
        assertSame(nightBackground, bar.background)
        assertSame(hostActionBackground, action.background)
        assertSame(hostTextColors, text.textColors)
        assertFalse(action.isEnabled)
        assertEquals(20, scroll.paddingBottom)
        assertEquals(8, scroll.paddingLeft)
        assertEquals(9, scroll.paddingTop)
        assertEquals(10, scroll.paddingRight)
        assertEquals(1f, requireNotNull(divider).alpha, 0f)
    }

    @Test
    fun catalogKeepsHeaderAndInjectedExportAlongsideNativeDownload() {
        create(GlassBarKind.CATALOG)
        assertTrue(bar.clipToOutline)
        val download = requireNotNull(bar.findViewById<View>(ids[CiweiMaoIds.CATALOG_DOWNLOAD_ACTION]!!))
        val export = RelativeLayout(activity).apply { tag = "cwmhook_catalog_export_entry" }
        (bar as LinearLayout).addView(export, 0, LinearLayout.LayoutParams(-1, -1, 1f))
        bar.addView(View(activity), 1, LinearLayout.LayoutParams(1, 40))
        var clicks = 0
        download.setOnClickListener { clicks++ }
        export.setOnClickListener { clicks += 10 }
        settle()
        assertEquals(49, source.top)
        assertEquals(800, source.bottom)
        assertEquals(83, scroll.paddingBottom)
        assertTrue(download.width >= 96)
        assertEquals(bar.width + 2 * layer().halo, layer().width)
        assertTrue(export.width >= 96)
        assertEquals(0f, requireNotNull(divider).alpha, 0f)
        download.performClick()
        export.performClick()
        session?.close()
        settle()
        assertSame(bar, download.parent)
        assertSame(bar, export.parent)
        assertEquals(11, clicks)
        assertEquals(requireNotNull(divider).id, (source.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.ABOVE))
        assertEquals(49, source.top)
        assertEquals(48, bar.height)
        assertEquals(7, scroll.paddingBottom)
        assertEquals(1f, requireNotNull(divider).alpha, 0f)
    }

    @Test
    fun selectionFollowsRealRadioIdsAfterReorderingAndHidingTabs() {
        create()
        val tabs = bar as RadioGroup
        val selected = tabs.getChildAt(2)
        tabs.removeView(selected)
        tabs.addView(selected, 0)
        tabs.getChildAt(3).visibility = View.GONE
        tabs.check(selected.id)
        settle()
        assertEquals((selected.left + selected.right) / 2f, requireNotNull(layer().selectionCenterX), 0.1f)
        val last = tabs.getChildAt(4)
        last.performClick()
        settle()
        assertEquals(last.id, tabs.checkedRadioButtonId)
        assertEquals((last.left + last.right) / 2f, requireNotNull(layer().selectionCenterX), 0.1f)
        last.visibility = View.GONE
        settle()
        assertNull(layer().selectionCenterX)
    }

    @Test
    fun lazyMainPagesReceiveInsetsAndNestedListsDoNotAccumulateThem() {
        create()
        val old = scroll
        (source as FrameLayout).removeView(old)
        repeat(120) { (source as FrameLayout).addView(FrameLayout(activity), FrameLayout.LayoutParams(1, 1)) }
        val nested = ExpandableListView(activity).apply { setPadding(0, 0, 0, 11) }
        val content = FrameLayout(activity).apply { addView(nested, FrameLayout.LayoutParams(-1, 400)) }
        val next = ScrollView(activity).apply {
            setPadding(0, 0, 0, 3)
            addView(content, ViewGroup.LayoutParams(400, 1800))
        }
        (source as FrameLayout).addView(next, FrameLayout.LayoutParams(-1, -1))
        settle()
        shadowOf(Looper.getMainLooper()).idle()
        settle()
        assertEquals(7, old.paddingBottom)
        assertTrue(old.clipToPadding)
        assertEquals(3 + 76, next.paddingBottom)
        assertEquals(11, nested.paddingBottom)
        assertTrue(nested.clipToPadding)
        session?.close()
        assertEquals(3, next.paddingBottom)
        assertTrue(next.clipToPadding)
    }

    @Test
    fun hiddenBarsReleaseScrollSpaceAndTrackNativeTransforms() {
        create()
        bar.visibility = View.GONE
        settle()
        assertEquals(7, scroll.paddingBottom)
        assertEquals(View.INVISIBLE, layer().visibility)
        bar.visibility = View.VISIBLE
        bar.translationX = 3f
        bar.translationY = -6f
        bar.alpha = 0.4f
        bar.elevation = 4f
        settle()
        assertEquals(bar.x, layer().x + layer().halo, 0f)
        assertEquals(bar.y, layer().y + layer().halo, 0f)
        assertEquals(bar.alpha, layer().alpha, 0f)
        assertEquals(bar.elevation, layer().elevation, 0f)
        assertEquals(7 + 76 + 6, scroll.paddingBottom)
    }

    @Test
    fun clippedCatalogAndLandscapeKeepTheBarAndTrailingContentInsideTheViewport() {
        create(GlassBarKind.CATALOG)
        parentTop = 80
        settle()
        assertEquals(92, barParams().bottomMargin)
        assertEquals(788, parentTop + bar.bottom)
        assertEquals(7 + 64 + 12 + 80, scroll.paddingBottom)
        parentTop = 0
        screenWidth = 800
        screenHeight = 400
        settle()
        assertEquals(560, bar.width)
        assertEquals(388, bar.bottom)
        assertEquals(83, scroll.paddingBottom)
    }

    @Test
    fun repeatedEnableDisableAndDetachLeaveNoExtraLayers() {
        create()
        repeat(3) {
            session?.close()
            settle()
            attach(GlassBarKind.MAIN)
            assertEquals(83, scroll.paddingBottom)
            assertEquals(3, parent.childCount)
        }
        val glass = layer()
        parent.removeView(bar)
        assertTrue(requireNotNull(session).closed)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(glass.parent)
        assertEquals(7, scroll.paddingBottom)
        assertSame(nativeBackground, bar.background)
        assertEquals(50, bar.layoutParams.height)
    }

    @Test
    fun unknownLayoutAnchorLeavesTheNativePageUntouched() {
        create(attach = false)
        (source.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ABOVE, View.generateViewId())
        assertNull(resolve(GlassBarKind.MAIN))
        assertEquals(2, parent.childCount)
        assertSame(nativeBackground, bar.background)
        assertEquals(7, scroll.paddingBottom)
    }

    @Test
    @Config(sdk = [28])
    fun olderAndroidRetainsFloatingControlsWithoutLoadingTheNewRenderer() {
        create()
        assertEquals(64, bar.height)
        assertEquals(83, scroll.paddingBottom)
        val renderer = GlassBackdropView::class.java.getDeclaredField("renderer").apply { isAccessible = true }
        assertNull(renderer.get(layer()))
        val button = (bar as RadioGroup).getChildAt(0)
        button.performClick()
        assertEquals(button.id, (bar as RadioGroup).checkedRadioButtonId)
        session?.close()
        settle()
        assertEquals(50, bar.height)
        assertEquals(7, scroll.paddingBottom)
    }
}
