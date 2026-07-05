package com.xiyunmn.cwmhook.feature.statusbar

internal data class StatusBarFeatureConfig(
    val logTag: String,
    val targetPackage: String,
    val targetDefaultPref: String,
    val cachePref: String,
    val cacheKeysName: String,
    val cacheVersion: String,
    val scrimTag: String,
    val mainFrameActivity: String,
    val readerActivity: String,
    val pagePagerIds: Set<String>,
    val sampleBitmapWidth: Int,
    val sampleBitmapHeight: Int,
    val sampleDelayMs: Long,
    val sampleMinIntervalMs: Long,
    val maxSceneCacheSize: Int,
    val maxTraversedViews: Int,
    val maxBackgroundScanViews: Int,
)
