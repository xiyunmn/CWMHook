package com.xiyunmn.cwmhook.core.glass

import android.annotation.TargetApi
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ForbidAd4TieBa's page -> vibrancy -> blur -> lens -> surface stack, adapted for the host.
 * The raised droplet has separate clear/embedded materials, clipped AFTER refraction so the
 * pill's blur cannot bleed outside its outline. Host icons and badges are never captured.
 * The disjoint source and framework drawChild path preserve the host's scroll/display-list behavior.
 */
@TargetApi(33)
internal class GlassLensRenderer(private val failure: (Throwable) -> Unit) {
    private val page = RenderNode("CWMHook.Glass.Page")
    private val base = RenderNode("CWMHook.Glass.Base")
    private val embedded = RenderNode("CWMHook.Glass.Droplet")
    private val clear = RenderNode("CWMHook.Glass.ClearDroplet")
    private val shadow = RenderNode("CWMHook.Glass.Shadow")
    private val inner = RenderNode("CWMHook.Glass.InnerShadow")
    private val nodes = listOf(page, base, embedded, clear, shadow, inner)
    private val sourceLocation = IntArray(2)
    private val bounds = RectF()
    private val selection = RectF()
    private val outline = Path()
    private val droplet = Path()
    private val surface = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wash = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val erase = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.CLEAR }
    private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; blendMode = BlendMode.PLUS }
    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.PLUS }
    private var lens: RuntimeShader? = null
    private var dropletLens: RuntimeShader? = null
    private var bloom: RuntimeShader? = null
    private var lighting: GlassLighting? = null
    private var dropletLighting: GlassLighting? = null
    private var capture: GlassSourceCapture? = null
    private var blurEffect: RenderEffect? = null
    private var saturation: RenderEffect? = null
    private var lightAngle = GlassTilt.REST_ANGLE
    private var width = 0
    private var height = 0
    private var margin = 0
    private var density = 1f
    private var night = false
    private var spec = GlassEffectsSpec()
    private var pressure = 0f
    private var touchX = 0f
    private var selectionVisible = false
    private var lowX = 0.5f
    private var lowY = 0.5f
    private var highX = 0.5f
    private var highY = 0.5f
    private var dirty = true
    private var ready = false
    private var broken = false

    fun configure(width: Int, height: Int, density: Float, spec: GlassEffectsSpec, night: Boolean) {
        if (this.width == width && this.height == height && this.density == density && this.spec == spec && this.night == night) return
        if (this.width != width || this.height != height || this.density != density) ready = false
        this.width = width
        this.height = height
        this.density = density
        this.spec = spec
        this.night = night
        margin = ceil(64f * density).toInt()
        blurEffect = null
        lighting = null
        dropletLighting = null
        dirty = true
    }

    fun setPress(value: Float, x: Float, y: Float) {
        val next = if (spec.press) value.coerceIn(0f, 1f) else 0f
        if (pressure == next && touchX == x) return
        pressure = next
        touchX = x
        dirty = true
    }

    fun setLightAngle(angle: Float) {
        if (lightAngle == angle) return
        lightAngle = angle
        dirty = true
    }

    fun setSelection(rect: RectF, visible: Boolean) {
        if (selection == rect && selectionVisible == visible) return
        selection.set(rect)
        selectionVisible = visible
        dirty = true
    }

    /** Re-record only the bounded band, on an existing host frame. No bitmap readback or idle loop. */
    fun prepare(source: View, screenX: Int, screenY: Int): Boolean {
        if (broken || width <= 0 || height <= 0 || !source.isAttachedToWindow) return false
        return guarded {
            source.getLocationOnScreen(sourceLocation)
            val dx = (screenX - sourceLocation[0]).toFloat()
            val dy = (screenY - sourceLocation[1]).toFloat()
            val captureWidth = width + 2 * margin
            val captureHeight = height + 2 * margin
            val lx = max(0.5f, margin - dx + 0.5f).coerceAtMost(captureWidth - 0.5f)
            val ly = max(0.5f, margin - dy + 0.5f).coerceAtMost(captureHeight - 0.5f)
            val hx = min(captureWidth - 0.5f, margin - dx + source.width - 0.5f).coerceAtLeast(lx)
            val hy = min(captureHeight - 0.5f, margin - dy + source.height - 0.5f).coerceAtLeast(ly)
            if (lx != lowX || ly != lowY || hx != highX || hy != highY) dirty = true
            lowX = lx; lowY = ly; highX = hx; highY = hy
            record(page) { recording ->
                recording.drawColor(if (night) 0xFF111111.toInt() else 0xFFF7F7F7.toInt())
                recording.translate(margin - dx, margin - dy)
                recording.clipRect(0, 0, source.width, source.height)
                (capture ?: GlassSourceCapture(source.context).also { capture = it }).record(recording, source)
            }
            ready = true
        }
    }

    /** Material changes can reuse a frozen page when a non-dimming popup temporarily takes focus. */
    fun draw(canvas: Canvas): Boolean {
        if (!ready || broken || !canvas.isHardwareAccelerated) return false
        return guarded {
            if (dirty || !base.hasDisplayList()) updateMaterials()
            if (spec.shadow) drawNode(canvas, shadow)
            drawBase(canvas, includeLighting = true)
            if (selectionVisible) drawDroplet(canvas)
        }
    }

    private fun updateMaterials() {
        val growth = if (spec.press && width > 0) 16f * density / width * pressure else 0f
        bounds.set(-width * growth / 2f, -height * growth / 2f, width * (1f + growth / 2f), height * (1f + growth / 2f))
        path(outline, bounds)
        path(droplet, selection)
        surface.color = if (night) 0x662C2C2E else 0x66F2F2F7
        val vibrancy = saturation ?: RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) }),
        ).also { saturation = it }
        var effect = if (spec.blur) {
            blurEffect ?: RenderEffect.createBlurEffect(4f * density, 4f * density, vibrancy, Shader.TileMode.CLAMP)
                .also { blurEffect = it }
        } else vibrancy
        if (spec.refraction) {
            val shader = lens ?: RuntimeShader(GLASS_LENS_AGSL).also { lens = it }
            effect = RenderEffect.createChainEffect(optics(shader, bounds, 24f * density, -24f * density, false), effect)
        }
        base.setRenderEffect(effect)
        record(base) { it.drawRenderNode(page) }
        if (spec.shadow) {
            shade.color = if (night) 0x33000000 else 0x1A000000
            record(shadow) { it.translate(margin.toFloat(), margin.toFloat()); it.drawPath(outline, shade) }
            shadow.setRenderEffect(RenderEffect.createBlurEffect(10f * density, 10f * density, Shader.TileMode.DECAL))
        } else shadow.discardDisplayList()
        if (selectionVisible && pressure > 0.01f) {
            recordDroplet(embedded, includePill = true)
            recordDroplet(clear, includePill = false)
            val dropletEffect = if (spec.refraction) {
                val shader = dropletLens ?: RuntimeShader(GLASS_LENS_AGSL).also { dropletLens = it }
                optics(shader, selection, 10f * density * pressure, -14f * density * pressure, true)
            } else null
            embedded.setRenderEffect(dropletEffect)
            clear.setRenderEffect(dropletEffect)
            if (spec.shadow) {
                val blur = 8f * density * pressure
                shade.color = 0x26000000
                record(inner) {
                    it.translate(margin.toFloat(), margin.toFloat())
                    it.clipPath(droplet)
                    it.drawPath(droplet, shade)
                    it.translate(0f, blur)
                    it.drawPath(droplet, erase)
                }
                inner.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL))
                inner.alpha = pressure
            }
        } else {
            for (node in listOf(embedded, clear, inner)) {
                node.setRenderEffect(null)
                node.discardDisplayList()
            }
        }
        dirty = false
    }

    private fun optics(shader: RuntimeShader, rect: RectF, bevel: Float, amount: Float, depth: Boolean): RenderEffect {
        shader.setFloatUniform("size", max(1f, rect.width()), max(1f, rect.height()))
        shader.setFloatUniform("offset", -margin - rect.left, -margin - rect.top)
        shader.setFloatUniform("radius", min(rect.width(), rect.height()) / 2f)
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("refraction", amount)
        shader.setFloatUniform("depth", if (depth) 1f else 0f)
        shader.setFloatUniform("dispersion", if (depth) 0.5f else 0f)
        shader.setFloatUniform("sampleLo", lowX, lowY)
        shader.setFloatUniform("sampleHi", highX, highY)
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    private fun recordDroplet(node: RenderNode, includePill: Boolean) = record(node) {
        it.drawRenderNode(page)
        it.translate(margin.toFloat(), margin.toFloat())
        if (includePill) drawBase(it, includeLighting = false)
        drawWash(it)
    }

    private fun drawBase(canvas: Canvas, includeLighting: Boolean) {
        val save = canvas.save()
        canvas.clipPath(outline)
        drawNode(canvas, base)
        canvas.drawPath(outline, surface)
        if (spec.highlight && pressure > 0.001f) {
            plus.alpha = (15f * pressure).roundToInt()
            canvas.drawPath(outline, plus)
            val shader = bloom ?: RuntimeShader(GLASS_BLOOM_AGSL).also { bloom = it }
            shader.setFloatUniform("alpha", 0.12f * pressure)
            shader.setFloatUniform("radius", min(width, height) * 1.2f)
            shader.setFloatUniform("position", touchX.coerceIn(0f, width.toFloat()), height / 2f)
            bloomPaint.shader = shader
            canvas.drawPath(outline, bloomPaint)
        }
        canvas.restoreToCount(save)
        if (includeLighting && spec.highlight) {
            val light = lighting ?: GlassLighting(density, (-PI / 4).toFloat()).also { lighting = it }
            drawLighting(canvas, light, bounds, 0.75f)
        }
    }

    private fun drawWash(canvas: Canvas) {
        wash.color = if (night) Color.WHITE else Color.BLACK
        wash.alpha = (26f * (1f - pressure)).roundToInt()
        canvas.drawPath(droplet, wash)
        wash.color = Color.BLACK
        wash.alpha = (8f * pressure).roundToInt()
        canvas.drawPath(droplet, wash)
    }

    private fun drawDroplet(canvas: Canvas) {
        if (pressure <= 0.01f) {
            drawWash(canvas)
            return
        }
        val save = canvas.save()
        canvas.clipPath(droplet)
        val overflow = canvas.save()
        canvas.clipOutPath(outline)
        drawNode(canvas, clear)
        canvas.restoreToCount(overflow)
        val overlap = canvas.save()
        canvas.clipPath(outline)
        drawNode(canvas, embedded)
        canvas.restoreToCount(overlap)
        if (spec.shadow && inner.hasDisplayList()) drawNode(canvas, inner)
        canvas.restoreToCount(save)
        if (spec.highlight) {
            val light = dropletLighting ?: GlassLighting(density, (PI / 2).toFloat()).also { dropletLighting = it }
            drawLighting(canvas, light, selection, pressure)
        }
    }

    private fun drawLighting(canvas: Canvas, light: GlassLighting, rect: RectF, alpha: Float) {
        val save = canvas.save()
        canvas.translate(rect.left, rect.top)
        light.draw(canvas, rect.width().roundToInt(), rect.height().roundToInt(), lightAngle, alpha)
        canvas.restoreToCount(save)
    }

    private fun path(path: Path, rect: RectF) {
        path.reset()
        if (!rect.isEmpty) {
            val radius = min(rect.width(), rect.height()) / 2f
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
    }

    private fun record(node: RenderNode, draw: (Canvas) -> Unit) {
        val w = width + 2 * margin
        val h = height + 2 * margin
        node.setPosition(0, 0, w, h)
        val canvas = node.beginRecording(w, h)
        try { draw(canvas) } finally { node.endRecording() }
    }

    private fun drawNode(canvas: Canvas, node: RenderNode) {
        val save = canvas.save()
        canvas.translate(-margin.toFloat(), -margin.toFloat())
        canvas.drawRenderNode(node)
        canvas.restoreToCount(save)
    }

    private inline fun guarded(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (error: Exception) {
        broken = true
        release()
        failure(error)
        false
    }

    fun release() {
        ready = false
        nodes.forEach { it.setRenderEffect(null); it.discardDisplayList() }
        lens = null; dropletLens = null; bloom = null
        lighting = null; dropletLighting = null; capture = null
        blurEffect = null; saturation = null
        dirty = true
    }
}
