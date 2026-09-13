package com.xiyunmn.cwmhook.feature.glassbar

import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.xiyunmn.cwmhook.host.CiweiMaoViewClasses
import java.util.IdentityHashMap

/** Add trailing scroll space, rather than padding the entire page away from the glass source. */
internal class GlassContentInsets(private val binding: GlassBarBinding) : AutoCloseable {
    private class Target(view: View) : AutoCloseable {
        private val ownership = GlassViewOwnership()
        val padding = ownership.bottomPadding(view)
        val clipping = (view as? ViewGroup)?.let { group ->
            ownership.own({ group.clipToPadding }, { group.clipToPadding = it })
        }
        override fun close() = ownership.close()
    }

    private class Branch(val view: ViewGroup) {
        private val visibility = view.visibility
        private val children = (0 until view.childCount).map(view::getChildAt)
        fun changed(): Boolean = view.visibility != visibility || view.childCount != children.size ||
            children.indices.any { view.getChildAt(it) !== children[it] }
    }

    private val targets = IdentityHashMap<View, Target>()
    private val branches = mutableListOf<Branch>()
    private val location = IntArray(2)
    private var discovery: ArrayDeque<View>? = null
    private var found = linkedSetOf<View>()
    private var discovered = false
    private var closed = false
    private var barTop = 0
    private var viewportBottom = 0
    private var visible = false
    private val continueDiscovery = Runnable { discoverChunk() }

    fun update(barTop: Int, viewportBottom: Int, visible: Boolean) {
        if (closed) return
        this.barTop = barTop
        this.viewportBottom = viewportBottom
        this.visible = visible
        if (discovery == null && (!discovered || branches.any { it.changed() })) {
            found = linkedSetOf()
            branches.clear()
            discovery = ArrayDeque<View>().apply { add(binding.nativeScroll ?: binding.source) }
            discoverChunk()
        }
        applyInsets()
    }

    private fun discoverChunk() {
        val queue = discovery ?: return
        if (closed) return
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 96) {
            val view = queue.removeFirst()
            if (!view.isWithin(binding.source)) continue
            val group = view as? ViewGroup
            if (view.visibility == View.GONE) {
                group?.let { branches += Branch(it) }
                continue
            }
            if (view === binding.nativeScroll || isVerticalScroll(view)) {
                found += view
                continue
            }
            if (view is HorizontalScrollView || view.hasType(CiweiMaoViewClasses.RECYCLER_VIEW)) continue
            if (group != null) {
                branches += Branch(group)
                for (index in 0 until group.childCount) queue.add(group.getChildAt(index))
            }
        }
        if (queue.isNotEmpty()) {
            binding.source.post(continueDiscovery)
            return
        }
        targets.keys.toList().filter { it !in found }.forEach { targets.remove(it)?.close() }
        found.forEach { view -> if (view !in targets) targets[view] = Target(view) }
        discovery = null
        discovered = true
        applyInsets()
    }

    private fun applyInsets() {
        targets.entries.toList().forEach { (view, target) ->
            if (!view.isWithin(binding.source)) {
                targets.remove(view)?.close()
            } else {
                view.getLocationOnScreen(location)
                val extra = GlassGeometry.occlusion(location[1] + view.height, viewportBottom, barTop, visible)
                target.padding.update { it + extra }
                target.clipping?.set(false)
            }
        }
    }

    private fun isVerticalScroll(view: View): Boolean {
        if (view is ScrollView || view is AbsListView || view.hasType(CiweiMaoViewClasses.NESTED_SCROLL_VIEW)) return true
        if (!view.hasType(CiweiMaoViewClasses.RECYCLER_VIEW)) return false
        return runCatching {
            val manager = view.javaClass.getMethod("getLayoutManager").invoke(view) ?: return@runCatching false
            manager.javaClass.getMethod("canScrollVertically").invoke(manager) == true
        }.getOrDefault(false)
    }

    private fun View.hasType(name: String): Boolean {
        var type: Class<*>? = javaClass
        while (type != null) {
            if (type.name == name) return true
            type = type.superclass
        }
        return false
    }

    override fun close() {
        if (closed) return
        closed = true
        binding.source.removeCallbacks(continueDiscovery)
        discovery = null
        targets.values.forEach(Target::close)
        targets.clear()
        branches.clear()
        found.clear()
    }

}
