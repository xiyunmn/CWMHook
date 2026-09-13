package com.xiyunmn.cwmhook.feature.glassbar

import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.RelativeLayout
import com.xiyunmn.cwmhook.config.glassbar.GlassBarsConfig
import com.xiyunmn.cwmhook.host.CiweiMaoIds

internal enum class GlassBarKind {
    MAIN, DETAIL, CATALOG;

    fun enabled(config: GlassBarsConfig): Boolean = when (this) {
        MAIN -> config.mainEnabled
        DETAIL -> config.detailEnabled
        CATALOG -> config.catalogEnabled
    }
}

internal data class GlassBarBinding(
    val kind: GlassBarKind,
    val parent: RelativeLayout,
    val bar: ViewGroup,
    val source: View,
    val nativeScroll: View? = null,
    val decorations: List<View> = emptyList(),
    val primaryAction: View? = null,
    val tabs: RadioGroup? = null,
)

/** Match only the three verified native layouts; unknown layouts keep the host appearance. */
internal object GlassBarBindingResolver {
    fun resolve(
        kind: GlassBarKind,
        root: View,
        find: (View, String) -> View? = ::findHostView,
    ): GlassBarBinding? {
        val barName = when (kind) {
            GlassBarKind.MAIN -> CiweiMaoIds.MAIN_TAB_GROUP
            GlassBarKind.DETAIL -> CiweiMaoIds.BOOK_DETAIL_BOTTOM_BAR
            GlassBarKind.CATALOG -> CiweiMaoIds.CATALOG_BOTTOM_BAR
        }
        val bar = if (kind == GlassBarKind.CATALOG) {
            find(root, CiweiMaoIds.CATALOG_DOWNLOAD_ACTION)?.parent as? ViewGroup
        } else find(root, barName) as? ViewGroup
        if (bar == null || find(bar, barName) !== bar) return null
        val parent = bar.parent as? RelativeLayout ?: return null
        if (bar.layoutParams !is RelativeLayout.LayoutParams) return null
        if (kind == GlassBarKind.MAIN && bar !is RadioGroup) return null
        if (kind == GlassBarKind.CATALOG && find(bar, CiweiMaoIds.CATALOG_DOWNLOAD_ACTION) == null) return null
        val sourceName = when (kind) {
            GlassBarKind.MAIN -> CiweiMaoIds.MAIN_TAB_CONTENT
            GlassBarKind.DETAIL -> CiweiMaoIds.BOOK_DETAIL_SCROLL
            GlassBarKind.CATALOG -> CiweiMaoIds.CATALOG_LIST
        }
        val source = find(parent, sourceName) ?: return null
        if (source.parent !== parent || source.layoutParams !is RelativeLayout.LayoutParams) return null
        val divider = if (kind == GlassBarKind.CATALOG) find(parent, CiweiMaoIds.CATALOG_BOTTOM_DIVIDER) else null
        val anchor = (source.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.ABOVE)
        if (anchor != 0 && anchor != bar.id && anchor != divider?.id) return null
        val hairlines = if (kind == GlassBarKind.DETAIL) {
            (0 until bar.childCount).map(bar::getChildAt).filter {
                it.javaClass == View::class.java && it.layoutParams.height in 1..bar.dp(2)
            }
        } else emptyList()
        return GlassBarBinding(
            kind = kind,
            parent = parent,
            bar = bar,
            source = source,
            nativeScroll = source.takeIf { kind != GlassBarKind.MAIN },
            decorations = hairlines + listOfNotNull(divider),
            primaryAction = if (kind == GlassBarKind.DETAIL) find(bar, CiweiMaoIds.BOOK_DETAIL_READ_ACTION) else null,
            tabs = bar as? RadioGroup,
        )
    }

    private fun findHostView(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", root.context.packageName)
        return if (id == 0) null else root.findViewById(id)
    }
}

internal fun View.isWithin(ancestor: View): Boolean {
    var current: View? = this
    while (current != null) {
        if (current === ancestor) return true
        current = current.parent as? View
    }
    return false
}

internal fun View.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
