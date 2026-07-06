package com.xiyunmn.cwmhook.feature.chapterbackup

internal data class ChapterBackupBook(
    val bookId: String,
    val title: String,
    val author: String?,
    val description: String? = null,
    val introduce: String? = null,
    val coverUrl: String? = null,
)

internal data class ChapterBackupChapter(
    val chapterId: String,
    val title: String,
    val index: Int,
    val divisionTitle: String,
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

internal enum class ChapterBackupFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String,
) {
    TXT("TXT", "txt", "text/plain"),
    EPUB("EPUB", "epub", "application/epub+zip"),
}

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
