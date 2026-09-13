package com.xiyunmn.cwmhook.feature.glassbar

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassGeometryTest {
    @Test
    fun navigationSpaceIsReservedOnceAcrossFittedEdgeToEdgeAndKeyboardLayouts() {
        assertEquals(36, GlassGeometry.bottomMargin(0, 24, GlassGeometry.consumedBottom(800, 800, 800), 12))
        assertEquals(12, GlassGeometry.bottomMargin(0, 24, GlassGeometry.consumedBottom(800, 776, 776), 12))
        assertEquals(20, GlassGeometry.bottomMargin(0, 24, GlassGeometry.consumedBottom(800, 800, 784), 12))
        assertEquals(12, GlassGeometry.bottomMargin(0, 24, GlassGeometry.consumedBottom(800, 480, 480), 12))
        assertEquals(32, GlassGeometry.bottomMargin(20, 24, 24, 12))
    }

    @Test
    fun narrowScreensPrioritizeActionWidthAndInvisibleBarsReserveNoSpace() {
        assertEquals(16, GlassGeometry.widthSideMargin(400, 288, 0, 368))
        assertEquals(6, GlassGeometry.widthSideMargin(300, 288, 0, 268))
        assertEquals(0, GlassGeometry.widthSideMargin(240, 288, 0, 208))
        assertEquals(76, GlassGeometry.occlusion(800, 800, 724, true))
        assertEquals(156, GlassGeometry.occlusion(880, 800, 724, true))
        assertEquals(0, GlassGeometry.occlusion(600, 800, 724, true))
        assertEquals(0, GlassGeometry.occlusion(800, 800, 724, false))
    }
}
