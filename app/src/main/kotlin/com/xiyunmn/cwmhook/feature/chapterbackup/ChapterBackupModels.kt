package com.xiyunmn.cwmhook.feature.chapterbackup

internal data class ChapterBackupBook(
    val bookId: String,
    val title: String,
    val author: String?,
)

internal data class ChapterBackupChapter(
    val chapterId: String,
    val title: String,
    val index: Int,
    val content: String,
)

internal data class ChapterBackupCandidate(
    val chapterId: String,
    val title: String,
    val index: Int,
    val divisionId: String?,
    val divisionTitle: String,
    val authorized: Boolean,
    val cached: Boolean,
    val estimatedBytes: Long,
)

internal data class ChapterBackupResult(
    val bookCount: Int,
    val chapterCount: Int,
    val skippedCount: Int,
    val outputLabel: String,
)

internal data class ChapterBackupNaming(
    val directoryName: String,
    val fileName: String,
)

internal fun ChapterBackupBook.defaultExportBaseName(): String {
    val authorName = author?.trim().orEmpty()
    return if (authorName.isBlank()) {
        title.trim().ifBlank { bookId }
    } else {
        "${title.trim().ifBlank { bookId }} - $authorName"
    }
}
