package com.xiyunmn.cwmhook.feature.statusbar

internal data class ColorBucket(
    var count: Int = 0,
    var r: Long = 0,
    var g: Long = 0,
    var b: Long = 0,
)

internal data class BackgroundCandidate(
    val color: Int,
    val score: Int,
    val description: String,
)
