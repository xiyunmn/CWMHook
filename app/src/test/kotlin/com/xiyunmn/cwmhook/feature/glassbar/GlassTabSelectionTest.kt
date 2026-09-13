package com.xiyunmn.cwmhook.feature.glassbar

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import com.xiyunmn.cwmhook.core.glass.GlassBackdropView
import com.xiyunmn.cwmhook.core.glass.GlassEffectsSpec
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Real RadioButton touch dispatch: native taps and drag cancellation must not duplicate clicks. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class GlassTabSelectionTest {
    private val controller = Robolectric.buildActivity(Activity::class.java).apply {
        get().setTheme(android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
    }.setup()
    private val activity = controller.get()
    private val parent = FrameLayout(activity)
    private val source = View(activity)
    private val bar = RadioGroup(activity).apply { orientation = RadioGroup.HORIZONTAL }
    private val glass = GlassBackdropView(activity, source) { throw AssertionError(it) }
    private val selection = GlassTabSelection(bar, glass)
    private val tabs = List(3) { RadioButton(activity).apply { id = View.generateViewId() } }
    private val colors = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Color.RED, Color.GRAY))
    private val origin = IntArray(2)
    private val clicks = mutableListOf<Int>()
    private val nativeActions = mutableListOf<Int>()
    private var downTime = 0L

    @Before
    fun setUp() {
        parent.addView(source, FrameLayout.LayoutParams(-1, -1))
        parent.addView(glass, FrameLayout.LayoutParams(300 + 2 * glass.halo, 60 + 2 * glass.halo).apply {
            leftMargin = 20 - glass.halo
            topMargin = 500 - glass.halo
        })
        parent.addView(bar, FrameLayout.LayoutParams(300, 60).apply { leftMargin = 20; topMargin = 500 })
        tabs.forEachIndexed { index, tab ->
            tab.text = "Tab $index"
            tab.setTextColor(colors)
            tab.setButtonDrawable(null)
            val icon = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_checked), ColorDrawable(Color.RED))
                addState(intArrayOf(), ColorDrawable(Color.GRAY))
                setBounds(0, 0, 16, 16)
            }
            tab.setCompoundDrawables(null, icon, null, null)
            tab.setOnClickListener { clicks += index }
            bar.addView(tab, RadioGroup.LayoutParams(150, 60))
        }
        tabs[1].visibility = View.GONE
        bar.check(tabs[0].id)
        activity.setContentView(parent)
        layout()
        configure(press = false)
    }

    private fun layout() {
        activity.window.decorView.apply {
            measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            layout(0, 0, 400, 800)
        }
        bar.getLocationOnScreen(origin)
    }

    private fun configure(press: Boolean) {
        glass.configure(300, 60, GlassEffectsSpec(press = press, tilt = false), false)
        glass.setActive(true)
        selection.update()
    }

    private fun send(action: Int, x: Float, y: Float = 30f) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(downTime, now, action, origin[0] + x, origin[1] + y, 0)
        try {
            selection.dispatchTouch(event) { native ->
                nativeActions += native.actionMasked
                activity.dispatchTouchEvent(native)
            }
        } finally { event.recycle() }
    }

    private fun iconColor(tab: RadioButton): Int = (tab.compoundDrawables[1].current as ColorDrawable).color

    @After
    fun tearDown() {
        selection.close()
        glass.close()
        controller.pause().stop().destroy()
    }

    @Test
    fun dragPreviewsNativeIconsWithoutNavigatingAndCommitsExactlyOnce() {
        send(MotionEvent.ACTION_DOWN, 75f)
        send(MotionEvent.ACTION_MOVE, 225f)
        assertEquals(tabs[0].id, bar.checkedRadioButtonId)
        assertEquals(emptyList<Int>(), clicks)
        assertEquals(225f, requireNotNull(glass.selectionCenterX), 0.1f)
        assertEquals(Color.RED, tabs[2].currentTextColor)
        assertEquals(Color.GRAY, tabs[0].currentTextColor)
        assertEquals(Color.RED, iconColor(tabs[2]))
        assertEquals(Color.GRAY, iconColor(tabs[0]))
        send(MotionEvent.ACTION_UP, 225f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(tabs[2].id, bar.checkedRadioButtonId)
        assertEquals(listOf(2), clicks)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), nativeActions)
        assertSame(colors, tabs[0].textColors)
        assertSame(colors, tabs[2].textColors)
        assertEquals(Color.GRAY, iconColor(tabs[0]))
        assertEquals(Color.RED, iconColor(tabs[2]))
    }

    @Test
    fun ordinaryTapOnAnotherTabUsesItsExistingClickListenerOnce() {
        send(MotionEvent.ACTION_DOWN, 225f)
        send(MotionEvent.ACTION_UP, 225f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(tabs[2].id, bar.checkedRadioButtonId)
        assertEquals(listOf(2), clicks)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), nativeActions)
    }

    @Test
    fun leavingTheBarVerticallyPreservesPickupOffsetAndReleaseTarget() {
        send(MotionEvent.ACTION_DOWN, 120f)
        send(MotionEvent.ACTION_MOVE, 120f, -150f)
        send(MotionEvent.ACTION_MOVE, 190f, -150f)
        assertEquals(145f, requireNotNull(glass.selectionCenterX), 0.1f)
        assertEquals(Color.RED, tabs[0].currentTextColor)
        send(MotionEvent.ACTION_MOVE, 225f, -210f)
        assertEquals(Color.RED, tabs[2].currentTextColor)
        send(MotionEvent.ACTION_UP, 225f, -210f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(2), clicks)
    }

    @Test
    fun cancellationAndMultitouchRestorePreviewWithoutNavigation() {
        for (cancel in listOf(MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN)) {
            send(MotionEvent.ACTION_DOWN, 75f)
            send(MotionEvent.ACTION_MOVE, 225f, -180f)
            send(cancel, 225f, -180f)
            send(MotionEvent.ACTION_UP, 225f, -180f)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(tabs[0].id, bar.checkedRadioButtonId)
            assertEquals(emptyList<Int>(), clicks)
            assertSame(colors, tabs[0].textColors)
            assertSame(colors, tabs[2].textColors)
            assertEquals(Color.RED, iconColor(tabs[0]))
            assertEquals(Color.GRAY, iconColor(tabs[2]))
        }
    }

    @Test
    fun verticalOnlyAndOutsideGesturesDoNotSwitchTabs() {
        send(MotionEvent.ACTION_DOWN, 75f, -180f)
        send(MotionEvent.ACTION_MOVE, 225f)
        send(MotionEvent.ACTION_UP, 225f)
        send(MotionEvent.ACTION_DOWN, 75f)
        send(MotionEvent.ACTION_MOVE, 75f, -180f)
        send(MotionEvent.ACTION_UP, 75f, -180f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<Int>(), clicks)
        assertEquals(tabs[0].id, bar.checkedRadioButtonId)
    }

    @Test
    fun reorderedAndDisabledTabsUseNativeIdsAndVisiblePositions() {
        bar.removeView(tabs[2])
        bar.addView(tabs[2], 0)
        layout()
        selection.update()
        send(MotionEvent.ACTION_DOWN, 225f)
        send(MotionEvent.ACTION_MOVE, 75f)
        send(MotionEvent.ACTION_UP, 75f)
        assertEquals(tabs[2].id, bar.checkedRadioButtonId)
        assertEquals(listOf(2), clicks)
        tabs[0].isEnabled = false
        send(MotionEvent.ACTION_DOWN, 75f)
        send(MotionEvent.ACTION_MOVE, 225f)
        send(MotionEvent.ACTION_UP, 225f)
        assertEquals(tabs[2].id, bar.checkedRadioButtonId)
        assertEquals(listOf(2), clicks)
    }

    @Test
    fun fastReversalReleasesOnTheRenderedCapsuleAndAllAnimationsSettle() {
        configure(press = true)
        send(MotionEvent.ACTION_DOWN, 75f)
        send(MotionEvent.ACTION_MOVE, 225f, -180f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertTrue(requireNotNull(glass.selectionCenterX) > 200f)
        assertEquals(1.2f, tabs[2].scaleX, 0.02f)
        send(MotionEvent.ACTION_MOVE, 75f, -180f)
        send(MotionEvent.ACTION_UP, 75f, -180f)
        assertEquals(tabs[2].id, bar.checkedRadioButtonId)
        assertEquals(listOf(2), clicks)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(1f, tabs[2].scaleX, 0f)
        assertEquals(1f, tabs[2].scaleY, 0f)
        assertEquals(0f, bar.translationX, 0f)
    }

    @Test
    fun disablingDuringADragRestoresPropertiesWithoutClicking() {
        configure(press = true)
        bar.translationX = 3f
        tabs[0].scaleX = 0.9f
        send(MotionEvent.ACTION_DOWN, 75f)
        send(MotionEvent.ACTION_MOVE, 225f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        selection.close()
        glass.setActive(false)
        send(MotionEvent.ACTION_UP, 225f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<Int>(), clicks)
        assertSame(colors, tabs[0].textColors)
        assertSame(colors, tabs[2].textColors)
        assertEquals(0.9f, tabs[0].scaleX, 0f)
        assertEquals(1f, tabs[2].scaleX, 0f)
        assertEquals(3f, bar.translationX, 0f)
    }
}
