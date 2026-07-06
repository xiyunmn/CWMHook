package com.xiyunmn.cwmhook.feature.chapterbackup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

internal class ChapterBackupDestination(
    private val context: Context,
    private val exportTreeUri: String?,
    private val logTag: String,
) {
    private val resolver = context.contentResolver
    private val directoryCache = HashMap<String, Uri>()

    fun writeBookBackup(
        batchName: String?,
        book: ChapterBackupBook,
        chapters: List<ChapterBackupChapter>,
        naming: ChapterBackupNaming? = null,
    ): String {
        val effectiveNaming = naming ?: book.defaultNaming()
        val fileName = ensureTxtExtension(effectiveNaming.fileName)
        val directories = listOfNotNull(
            batchName?.takeIf { it.isNotBlank() },
            effectiveNaming.directoryName,
        )
        val text = buildString {
            append(book.title).append('\n')
            if (!book.author.isNullOrBlank()) {
                append("作者：").append(book.author).append('\n')
            }
            append("book_id：").append(book.bookId).append('\n')
            append("章节数：").append(chapters.size).append('\n')
            append('\n')
            chapters.sortedBy { it.index }.forEach { chapter ->
                append("## ")
                if (chapter.index >= 0) {
                    append(chapter.index).append(' ')
                }
                append(chapter.title).append('\n')
                append("chapter_id：").append(chapter.chapterId).append('\n')
                append('\n')
                append(chapter.content.trimEnd())
                append("\n\n")
            }
        }
        return writeText(
            directories = directories,
            fileName = fileName,
            text = text,
        )
    }

    fun rootLabel(batchName: String): String {
        return if (exportTreeUri.isNullOrBlank()) {
            File(privateRoot(), batchName).absolutePath
        } else {
            "${exportDirectoryLabel()}/$batchName"
        }
    }

    fun pathLabel(directories: List<String>): String {
        return if (exportTreeUri.isNullOrBlank()) {
            directories.fold(privateRoot()) { parent, name -> File(parent, safeName(name)) }.absolutePath
        } else {
            "${exportDirectoryLabel()}/${directories.joinToString("/")}"
        }
    }

    fun exportDirectoryLabel(): String {
        return if (exportTreeUri.isNullOrBlank()) {
            "未选择，使用宿主私有路径"
        } else {
            describeTreeUri(exportTreeUri)
        }
    }

    private fun writeText(
        directories: List<String>,
        fileName: String,
        text: String,
    ): String {
        val tree = exportTreeUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return if (tree == null) {
            writePrivate(directories, fileName, text)
        } else {
            writeSaf(tree, directories, fileName, text)
        }
    }

    private fun writePrivate(
        directories: List<String>,
        fileName: String,
        text: String,
    ): String {
        val dir = directories.fold(privateRoot()) { parent, name -> File(parent, safeName(name)) }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, safeFileName(fileName))
        file.writeText(text, StandardCharsets.UTF_8)
        return file.absolutePath
    }

    private fun writeSaf(
        treeUri: Uri,
        directories: List<String>,
        fileName: String,
        text: String,
    ): String {
        val safeFileName = safeFileName(fileName)
        val parent = directories.fold(rootDocumentUri(treeUri)) { currentParent, name ->
            findOrCreateDirectory(treeUri, currentParent, safeName(name))
        }
        val file = findOrCreateTextFile(treeUri, parent, safeFileName)
        resolver.openOutputStream(file, "wt").use { output ->
            if (output == null) {
                error("Cannot open SAF output stream")
            }
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                writer.write(text)
            }
        }
        return "${describeTreeUri(treeUri.toString())}/${directories.joinToString("/")}/$safeFileName"
    }

    private fun describeTreeUri(uriText: String): String {
        return runCatching {
            val documentId = DocumentsContract.getTreeDocumentId(Uri.parse(uriText))
            when {
                documentId == "primary:" -> "内部存储"
                documentId.startsWith("primary:") -> {
                    val path = documentId.removePrefix("primary:").trim('/')
                    if (path.isBlank()) "内部存储" else "内部存储/$path"
                }
                else -> documentId.replace(':', '/').trim('/').ifBlank { "已选择系统路径" }
            }
        }.getOrDefault("已选择系统路径")
    }

    private fun privateRoot(): File {
        return File(context.filesDir, "cwmhook/exports/chapter_export")
    }

    private fun rootDocumentUri(treeUri: Uri): Uri {
        return DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
    }

    private fun findOrCreateDirectory(treeUri: Uri, parentUri: Uri, displayName: String): Uri {
        val key = "${DocumentsContract.getDocumentId(parentUri)}/$displayName"
        directoryCache[key]?.let { return it }
        val existing = findChild(treeUri, parentUri, displayName, DocumentsContract.Document.MIME_TYPE_DIR)
        if (existing != null) {
            directoryCache[key] = existing
            return existing
        }
        val created = DocumentsContract.createDocument(
            resolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            displayName,
        ) ?: error("Cannot create SAF directory: $displayName")
        directoryCache[key] = created
        return created
    }

    private fun findOrCreateTextFile(treeUri: Uri, parentUri: Uri, displayName: String): Uri {
        val existing = findChild(treeUri, parentUri, displayName, "text/plain")
        if (existing != null) {
            return existing
        }
        return DocumentsContract.createDocument(
            resolver,
            parentUri,
            "text/plain",
            displayName,
        ) ?: error("Cannot create SAF file: $displayName")
    }

    private fun findChild(treeUri: Uri, parentUri: Uri, displayName: String, mimeType: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(parentUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val childMime = cursor.getString(mimeIndex)
                    if (name == displayName && childMime == mimeType) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                    }
                }
                null
            }
        }.getOrElse { throwable ->
            ModuleFileLogger.w(logTag, "Failed to query SAF children", throwable)
            null
        }
    }

    private fun ChapterBackupBook.defaultNaming(): ChapterBackupNaming {
        val baseName = defaultExportBaseName()
        return ChapterBackupNaming(
            directoryName = baseName,
            fileName = baseName,
        )
    }

    private fun ensureTxtExtension(raw: String): String {
        val trimmed = raw.trim().ifBlank { "未命名" }
        return if (trimmed.endsWith(".txt", ignoreCase = true)) trimmed else "$trimmed.txt"
    }

    private fun safeFileName(raw: String): String {
        val base = ensureTxtExtension(raw)
            .replace(Regex("(?i)\\.txt$"), "")
            .ifBlank { "未命名" }
        return "${safeName(base).take(92)}.txt"
    }

    private fun safeName(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
            .ifBlank { "未命名" }
        return cleaned.take(96)
    }
}
