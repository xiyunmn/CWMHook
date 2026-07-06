package com.xiyunmn.cwmhook.feature.chapterbackup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        format: ChapterBackupFormat = ChapterBackupFormat.TXT,
    ): String {
        val effectiveNaming = naming ?: book.defaultNaming()
        val fileName = ensureExtension(effectiveNaming.fileName, format)
        val directories = listOfNotNull(
            batchName?.takeIf { it.isNotBlank() },
            effectiveNaming.directoryName,
        )
        val bytes = when (format) {
            ChapterBackupFormat.TXT -> buildStandardTxt(book, chapters).toByteArray(StandardCharsets.UTF_8)
            ChapterBackupFormat.EPUB -> buildEpub(book, chapters)
        }
        return writeBytes(
            directories = directories,
            fileName = fileName,
            bytes = bytes,
            mimeType = format.mimeType,
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

    private fun writeBytes(
        directories: List<String>,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): String {
        val tree = exportTreeUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return if (tree == null) {
            writePrivate(directories, fileName, bytes)
        } else {
            writeSaf(tree, directories, fileName, bytes, mimeType)
        }
    }

    private fun buildStandardTxt(
        book: ChapterBackupBook,
        chapters: List<ChapterBackupChapter>,
    ): String {
        val sortedChapters = sortedChapters(chapters)
        val divisionTitles = sortedChapters
            .map { it.divisionTitle.trim().ifBlank { DEFAULT_DIVISION_TITLE } }
            .distinct()
        val writeDivisionHeadings = divisionTitles.size > 1
        var lastDivision: String? = null

        return buildString {
            // Legado-MD3's default "去空白" TXT TOC rule requires whitespace before a heading.
            append('\n')
            append("第0章 作品信息\n\n")
            append(formatBookMetadata(book))
            append("\n\n")
            sortedChapters.forEachIndexed { index, chapter ->
                val divisionTitle = chapter.divisionTitle.trim().ifBlank { DEFAULT_DIVISION_TITLE }
                if (writeDivisionHeadings && divisionTitle != lastDivision) {
                    append(formatDivisionHeading(divisionTitles.indexOf(divisionTitle) + 1, divisionTitle))
                    append("\n\n")
                    lastDivision = divisionTitle
                }
                val chapterHeading = formatChapterHeading(chapter, index + 1)
                append(chapterHeading)
                append("\n\n")
                append(formatChapterContent(chapter, chapterHeading))
                append("\n\n")
            }
        }.trimEnd() + "\n"
    }

    private fun buildEpub(
        book: ChapterBackupBook,
        chapters: List<ChapterBackupChapter>,
    ): ByteArray {
        val sortedChapters = sortedChapters(chapters)
        val cover = loadCover(book.coverUrl)
        val chapterItems = sortedChapters.mapIndexed { index, chapter ->
            val heading = formatChapterHeading(chapter, index + 1)
            EpubChapter(
                id = "chapter_${index + 1}",
                href = "text/chapter_${(index + 1).toString().padStart(4, '0')}.xhtml",
                title = heading,
                divisionTitle = chapter.divisionTitle.trim().ifBlank { DEFAULT_DIVISION_TITLE },
                content = formatChapterContent(chapter, heading),
            )
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            addStoredEntry(zip, "mimetype", "application/epub+zip".toByteArray(StandardCharsets.US_ASCII))
            addTextEntry(zip, "META-INF/container.xml", epubContainerXml())
            addTextEntry(zip, "OEBPS/content.opf", epubPackageOpf(book, chapterItems, cover))
            addTextEntry(zip, "OEBPS/toc.ncx", epubTocNcx(book, chapterItems))
            addTextEntry(zip, "OEBPS/nav.xhtml", epubNavXhtml(book, chapterItems))
            addTextEntry(zip, "OEBPS/book-info.xhtml", epubBookInfoXhtml(book, cover))
            if (cover != null) {
                addTextEntry(zip, "OEBPS/cover.xhtml", epubCoverXhtml(book, cover))
                addBytesEntry(zip, "OEBPS/${cover.href}", cover.bytes)
            }
            addTextEntry(zip, "OEBPS/styles/style.css", epubCss())
            chapterItems.forEach { chapter ->
                addTextEntry(zip, "OEBPS/${chapter.href}", epubChapterXhtml(book, chapter))
            }
        }
        return output.toByteArray()
    }

    private fun sortedChapters(chapters: List<ChapterBackupChapter>): List<ChapterBackupChapter> {
        return chapters.sortedWith(
            compareBy<ChapterBackupChapter> { it.index < 0 }.thenBy { it.index },
        )
    }

    private fun formatBookMetadata(book: ChapterBackupBook): String {
        return buildString {
            appendMetadataLine("书名", book.title.ifBlank { book.bookId })
            appendMetadataLine("作者", book.author)
            appendMetadataLine("book_id", book.bookId)
            appendMetadataBlock("简介", book.description)
            val introduce = cleanMetadataText(book.introduce)
            if (introduce.isNotBlank() && introduce != cleanMetadataText(book.description)) {
                appendMetadataBlock("介绍", introduce)
            }
            appendMetadataLine("封面", book.coverUrl)
        }.trimEnd()
    }

    private fun StringBuilder.appendMetadataLine(label: String, value: String?) {
        val text = cleanMetadataText(value)
        if (text.isNotBlank()) {
            append(label).append('：').append(text.replace('\n', ' ')).append('\n')
        }
    }

    private fun StringBuilder.appendMetadataBlock(label: String, value: String?) {
        val text = cleanMetadataText(value)
        if (text.isNotBlank()) {
            append(label).append("：\n")
            append(text).append('\n')
        }
    }

    private fun metadataDescription(book: ChapterBackupBook): String {
        return cleanMetadataText(book.description).ifBlank { cleanMetadataText(book.introduce) }
    }

    private fun cleanMetadataText(value: String?): String {
        return value.orEmpty()
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    private fun formatDivisionHeading(number: Int, title: String): String {
        val cleanTitle = stripHeadingPrefix(title).ifBlank { DEFAULT_DIVISION_TITLE }
        val tail = cleanTitle.take(HEADING_TAIL_LIMIT)
        return if (tail.isBlank()) "第${number}卷" else "第${number}卷 $tail"
    }

    private fun formatChapterHeading(chapter: ChapterBackupChapter, fallbackNumber: Int): String {
        val cleanTitle = stripHeadingPrefix(chapter.title)
        val tail = cleanTitle.take(HEADING_TAIL_LIMIT)
        return if (tail.isBlank()) "第${fallbackNumber}章" else "第${fallbackNumber}章 $tail"
    }

    private fun formatChapterContent(chapter: ChapterBackupChapter, generatedHeading: String): String {
        val knownTitleLines = listOf(
            chapter.title,
            stripHeadingPrefix(chapter.title),
            generatedHeading,
        ).map { normalizeTitleLine(it) }
            .filter { it.isNotBlank() }
            .distinct()
        var text = chapter.content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trimStart()
        repeat(2) {
            val firstLineEnd = text.indexOf('\n').takeIf { it >= 0 } ?: text.length
            val firstLine = normalizeTitleLine(text.substring(0, firstLineEnd))
            if (firstLine.isNotBlank() && firstLine in knownTitleLines) {
                text = text.substring(firstLineEnd).trimStart('\n', ' ', '\t', '　')
            } else {
                return@repeat
            }
        }
        return text.trimEnd()
    }

    private fun stripHeadingPrefix(raw: String): String {
        return raw.trim()
            .replace(CHAPTER_PREFIX_PATTERN, "")
            .replace(NUMBER_PREFIX_PATTERN, "")
            .replace(ENGLISH_PREFIX_PATTERN, "")
            .trim(' ', '　', '\t', '-', '_', '—', ':', '：', ',', '，', '.', '、')
    }

    private fun normalizeTitleLine(raw: String): String {
        return raw.trim()
            .replace(Regex("""[ 　\t]+"""), " ")
    }

    private fun epubContainerXml(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""
    }

    private fun epubPackageOpf(book: ChapterBackupBook, chapters: List<EpubChapter>, cover: EpubCover?): String {
        val modified = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val author = book.author?.trim().orEmpty()
        val description = metadataDescription(book)
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">""").append('\n')
            append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
            append("    <dc:identifier id=\"bookid\">").append(escapeXml("urn:cwmhook:ciweimao:${book.bookId}")).append("</dc:identifier>\n")
            append("    <dc:title>").append(escapeXml(book.title.ifBlank { book.bookId })).append("</dc:title>\n")
            if (author.isNotBlank()) {
                append("    <dc:creator>").append(escapeXml(author)).append("</dc:creator>\n")
            }
            if (description.isNotBlank()) {
                append("    <dc:description>").append(escapeXml(description)).append("</dc:description>\n")
            }
            append("    <dc:language>zh-CN</dc:language>\n")
            append("    <meta property=\"dcterms:modified\">").append(modified).append("</meta>\n")
            if (cover != null) {
                append("    <meta name=\"cover\" content=\"cover-image\"/>\n")
            }
            append("  </metadata>\n")
            append("  <manifest>\n")
            append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            append("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
            append("    <item id=\"style\" href=\"styles/style.css\" media-type=\"text/css\"/>\n")
            append("    <item id=\"book-info\" href=\"book-info.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
            if (cover != null) {
                append("    <item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
                append("    <item id=\"cover-image\" href=\"").append(cover.href).append("\" media-type=\"").append(cover.mimeType).append("\" properties=\"cover-image\"/>\n")
            }
            chapters.forEach {
                append("    <item id=\"").append(it.id).append("\" href=\"").append(it.href).append("\" media-type=\"application/xhtml+xml\"/>\n")
            }
            append("  </manifest>\n")
            append("  <spine toc=\"ncx\">\n")
            if (cover != null) {
                append("    <itemref idref=\"cover\" linear=\"yes\"/>\n")
            }
            append("    <itemref idref=\"book-info\"/>\n")
            chapters.forEach {
                append("    <itemref idref=\"").append(it.id).append("\"/>\n")
            }
            append("  </spine>\n")
            append("</package>\n")
        }
    }

    private fun epubTocNcx(book: ChapterBackupBook, chapters: List<EpubChapter>): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">""").append('\n')
            append("  <head>\n")
            append("    <meta name=\"dtb:uid\" content=\"").append(escapeXml("urn:cwmhook:ciweimao:${book.bookId}")).append("\"/>\n")
            append("    <meta name=\"dtb:depth\" content=\"2\"/>\n")
            append("    <meta name=\"dtb:totalPageCount\" content=\"0\"/>\n")
            append("    <meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n")
            append("  </head>\n")
            append("  <docTitle><text>").append(escapeXml(book.title.ifBlank { book.bookId })).append("</text></docTitle>\n")
            append("  <navMap>\n")
            val groups = chapters.groupBy { it.divisionTitle }
            var playOrder = 1
            append("    <navPoint id=\"book_info_toc\" playOrder=\"").append(playOrder++).append("\">\n")
            append("      <navLabel><text>作品信息</text></navLabel>\n")
            append("      <content src=\"book-info.xhtml\"/>\n")
            append("    </navPoint>\n")
            if (groups.size > 1) {
                groups.entries.forEachIndexed { index, entry ->
                    val first = entry.value.first()
                    append("    <navPoint id=\"division_").append(index + 1).append("\" playOrder=\"").append(playOrder++).append("\">\n")
                    append("      <navLabel><text>").append(escapeXml("第${index + 1}卷 ${stripHeadingPrefix(entry.key).ifBlank { DEFAULT_DIVISION_TITLE }}")).append("</text></navLabel>\n")
                    append("      <content src=\"").append(first.href).append("\"/>\n")
                    entry.value.forEach { chapter ->
                        append("      <navPoint id=\"").append(chapter.id).append("_toc\" playOrder=\"").append(playOrder++).append("\">\n")
                        append("        <navLabel><text>").append(escapeXml(chapter.title)).append("</text></navLabel>\n")
                        append("        <content src=\"").append(chapter.href).append("\"/>\n")
                        append("      </navPoint>\n")
                    }
                    append("    </navPoint>\n")
                }
            } else {
                chapters.forEach { chapter ->
                    append("    <navPoint id=\"").append(chapter.id).append("_toc\" playOrder=\"").append(playOrder++).append("\">\n")
                    append("      <navLabel><text>").append(escapeXml(chapter.title)).append("</text></navLabel>\n")
                    append("      <content src=\"").append(chapter.href).append("\"/>\n")
                    append("    </navPoint>\n")
                }
            }
            append("  </navMap>\n")
            append("</ncx>\n")
        }
    }

    private fun epubNavXhtml(book: ChapterBackupBook, chapters: List<EpubChapter>): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<!DOCTYPE html>""").append('\n')
            append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN" xml:lang="zh-CN">""").append('\n')
            append("<head><title>目录</title><link rel=\"stylesheet\" type=\"text/css\" href=\"styles/style.css\"/></head>\n")
            append("<body><nav epub:type=\"toc\" id=\"toc\"><h1>").append(escapeXml(book.title.ifBlank { book.bookId })).append("</h1>\n")
            append("<ol>\n")
            append("<li><a href=\"book-info.xhtml\">作品信息</a></li>\n")
            val groups = chapters.groupBy { it.divisionTitle }
            if (groups.size > 1) {
                groups.entries.forEachIndexed { index, entry ->
                    append("<li><span>").append(escapeXml("第${index + 1}卷 ${stripHeadingPrefix(entry.key).ifBlank { DEFAULT_DIVISION_TITLE }}")).append("</span><ol>\n")
                    entry.value.forEach { chapter ->
                        append("<li><a href=\"").append(chapter.href).append("\">").append(escapeXml(chapter.title)).append("</a></li>\n")
                    }
                    append("</ol></li>\n")
                }
            } else {
                chapters.forEach { chapter ->
                    append("<li><a href=\"").append(chapter.href).append("\">").append(escapeXml(chapter.title)).append("</a></li>\n")
                }
            }
            append("</ol></nav></body></html>\n")
        }
    }

    private fun epubCoverXhtml(book: ChapterBackupBook, cover: EpubCover): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<!DOCTYPE html>""").append('\n')
            append("""<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN" xml:lang="zh-CN">""").append('\n')
            append("<head><title>").append(escapeXml(book.title.ifBlank { book.bookId })).append("</title><link rel=\"stylesheet\" type=\"text/css\" href=\"styles/style.css\"/></head>\n")
            append("<body class=\"cover-page\"><section class=\"cover\"><img src=\"")
            append(escapeXml(cover.href))
            append("\" alt=\"")
            append(escapeXml(book.title.ifBlank { "封面" }))
            append("\"/></section></body></html>\n")
        }
    }

    private fun epubBookInfoXhtml(book: ChapterBackupBook, cover: EpubCover?): String {
        val title = book.title.ifBlank { book.bookId }
        val author = cleanMetadataText(book.author)
        val description = cleanMetadataText(book.description)
        val introduce = cleanMetadataText(book.introduce)
        val coverUrl = cleanMetadataText(book.coverUrl)
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<!DOCTYPE html>""").append('\n')
            append("""<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN" xml:lang="zh-CN">""").append('\n')
            append("<head><title>作品信息</title><link rel=\"stylesheet\" type=\"text/css\" href=\"styles/style.css\"/></head>\n")
            append("<body><section class=\"book-info\">\n")
            append("<h1>").append(escapeXml(title)).append("</h1>\n")
            if (cover != null) {
                append("<div class=\"info-cover\"><img src=\"").append(escapeXml(cover.href)).append("\" alt=\"封面\"/></div>\n")
            }
            append("<dl>\n")
            appendInfoPair("书名", title)
            appendInfoPair("作者", author)
            appendInfoPair("book_id", book.bookId)
            appendInfoPair("封面", coverUrl)
            append("</dl>\n")
            appendInfoBlock("简介", description)
            if (introduce.isNotBlank() && introduce != description) {
                appendInfoBlock("介绍", introduce)
            }
            append("</section></body></html>\n")
        }
    }

    private fun StringBuilder.appendInfoPair(label: String, value: String) {
        if (value.isBlank()) {
            return
        }
        append("<dt>").append(escapeXml(label)).append("</dt><dd>").append(escapeXml(value)).append("</dd>\n")
    }

    private fun StringBuilder.appendInfoBlock(label: String, value: String) {
        if (value.isBlank()) {
            return
        }
        append("<h2>").append(escapeXml(label)).append("</h2>\n")
        value.split('\n').forEach { line ->
            val text = line.trim()
            if (text.isNotBlank()) {
                append("<p class=\"description\">").append(escapeXml(text)).append("</p>\n")
            }
        }
    }

    private fun epubChapterXhtml(book: ChapterBackupBook, chapter: EpubChapter): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<!DOCTYPE html>""").append('\n')
            append("""<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN" xml:lang="zh-CN">""").append('\n')
            append("<head><title>").append(escapeXml(chapter.title)).append("</title><link rel=\"stylesheet\" type=\"text/css\" href=\"../styles/style.css\"/></head>\n")
            append("<body>\n")
            append("<section class=\"chapter\">\n")
            append("<h1>").append(escapeXml(chapter.title)).append("</h1>\n")
            if (book.title.isNotBlank()) {
                append("<div class=\"book-title\">").append(escapeXml(book.title)).append("</div>\n")
            }
            chapter.content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
                .forEach { line ->
                    val text = line.trim()
                    if (text.isBlank()) {
                        append("<p class=\"empty\">&#160;</p>\n")
                    } else {
                        append("<p>").append(escapeXml(text)).append("</p>\n")
                    }
                }
            append("</section>\n")
            append("</body></html>\n")
        }
    }

    private fun epubCss(): String {
        return """
body {
  margin: 0;
  padding: 1em;
  line-height: 1.8;
  font-family: serif;
}
h1 {
  font-size: 1.35em;
  line-height: 1.4;
  margin: 0 0 1.2em;
  text-align: center;
}
p {
  margin: 0.35em 0;
  text-indent: 2em;
}
p.empty {
  min-height: 0.8em;
}
.book-title {
  color: #666;
  font-size: 0.85em;
  margin: -0.8em 0 1.2em;
  text-align: center;
}
nav ol {
  line-height: 1.8;
}
body.cover-page {
  padding: 0;
}
.cover {
  height: 100vh;
  margin: 0;
  text-align: center;
}
.cover img {
  max-height: 100%;
  max-width: 100%;
}
.book-info dl {
  margin: 0 0 1.2em;
}
.book-info dt {
  color: #666;
  float: left;
  font-weight: bold;
  width: 4.5em;
}
.book-info dd {
  margin: 0 0 0.5em 5em;
}
.book-info h2 {
  font-size: 1.05em;
  margin: 1.2em 0 0.4em;
}
.book-info p.description {
  text-indent: 2em;
}
.info-cover {
  margin: 0 auto 1.2em;
  text-align: center;
}
.info-cover img {
  max-height: 14em;
  max-width: 55%;
}
""".trimIndent() + "\n"
    }

    private fun escapeXml(raw: String): String {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun addStoredEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun addTextEntry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun addBytesEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun loadCover(rawUrl: String?): EpubCover? {
        val source = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val bytes = runCatching { readCoverBytes(source) }
            .onFailure { ModuleFileLogger.w(logTag, "Failed to read cover: $source", it) }
            .getOrNull()
            ?: return null
        return normalizeCover(bytes)
    }

    private fun readCoverBytes(source: String): ByteArray? {
        return when {
            source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true) -> {
                val connection = URL(source).openConnection() as HttpURLConnection
                connection.connectTimeout = COVER_CONNECT_TIMEOUT_MS
                connection.readTimeout = COVER_READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "CWMHook")
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        return null
                    }
                    connection.inputStream.use { it.readLimited(COVER_MAX_BYTES) }
                } finally {
                    connection.disconnect()
                }
            }
            source.startsWith("content://", ignoreCase = true) -> {
                resolver.openInputStream(Uri.parse(source))?.use { it.readLimited(COVER_MAX_BYTES) }
            }
            source.startsWith("file://", ignoreCase = true) -> {
                Uri.parse(source).path?.let(::File)?.takeIf { it.isFile }?.inputStream()?.use { it.readLimited(COVER_MAX_BYTES) }
            }
            else -> {
                File(source).takeIf { it.isFile }?.inputStream()?.use { it.readLimited(COVER_MAX_BYTES) }
            }
        }
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) {
                break
            }
            total += read
            if (total > limit) {
                error("Cover image is too large")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun normalizeCover(bytes: ByteArray): EpubCover? {
        val mime = detectImageMime(bytes)
        if (mime == "image/jpeg" || mime == "image/png" || mime == "image/gif") {
            return EpubCover("images/cover.${extensionForMime(mime)}", mime, bytes)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            EpubCover("images/cover.jpg", "image/jpeg", output.toByteArray())
        } finally {
            bitmap.recycle()
        }
    }

    private fun detectImageMime(bytes: ByteArray): String? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        ) {
            return "image/gif"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }
        return null
    }

    private fun extensionForMime(mime: String): String {
        return when (mime) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            else -> "jpg"
        }
    }

    private fun writePrivate(
        directories: List<String>,
        fileName: String,
        bytes: ByteArray,
    ): String {
        val dir = directories.fold(privateRoot()) { parent, name -> File(parent, safeName(name)) }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, safeFileName(fileName))
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun writeSaf(
        treeUri: Uri,
        directories: List<String>,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): String {
        val safeFileName = safeFileName(fileName)
        val parent = directories.fold(rootDocumentUri(treeUri)) { currentParent, name ->
            findOrCreateDirectory(treeUri, currentParent, safeName(name))
        }
        val file = findOrCreateFile(treeUri, parent, safeFileName, mimeType)
        resolver.openOutputStream(file, "wt").use { output ->
            if (output == null) {
                error("Cannot open SAF output stream")
            }
            output.write(bytes)
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

    private fun findOrCreateFile(treeUri: Uri, parentUri: Uri, displayName: String, mimeType: String): Uri {
        val existing = findChild(treeUri, parentUri, displayName, mimeType)
            ?: findChild(treeUri, parentUri, displayName, null)
        if (existing != null) {
            return existing
        }
        return DocumentsContract.createDocument(
            resolver,
            parentUri,
            mimeType,
            displayName,
        ) ?: error("Cannot create SAF file: $displayName")
    }

    private fun findChild(treeUri: Uri, parentUri: Uri, displayName: String, mimeType: String?): Uri? {
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
                    val mimeMatches = if (mimeType == null) {
                        childMime != DocumentsContract.Document.MIME_TYPE_DIR
                    } else {
                        childMime == mimeType
                    }
                    if (name == displayName && mimeMatches) {
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

    private fun ensureExtension(raw: String, format: ChapterBackupFormat): String {
        val trimmed = raw.trim().ifBlank { "未命名" }
        if (trimmed.endsWith(".${format.extension}", ignoreCase = true)) {
            return trimmed
        }
        val base = ChapterBackupFormat.values()
            .firstOrNull { trimmed.endsWith(".${it.extension}", ignoreCase = true) }
            ?.let { trimmed.dropLast(it.extension.length + 1) }
            ?: trimmed
        return "${base.ifBlank { "未命名" }}.${format.extension}"
    }

    private fun safeFileName(raw: String): String {
        val trimmed = raw.trim().ifBlank { "未命名" }
        val dotIndex = trimmed.lastIndexOf('.')
            .takeIf { it > 0 && it < trimmed.lastIndex && trimmed.length - it <= 12 }
        val base = dotIndex?.let { trimmed.substring(0, it) } ?: trimmed
        val extension = dotIndex?.let { trimmed.substring(it).replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_") }.orEmpty()
        val maxBaseLength = (96 - extension.length).coerceAtLeast(16)
        return "${safeName(base).take(maxBaseLength)}$extension"
    }

    private fun safeName(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
            .ifBlank { "未命名" }
        return cleaned.take(96)
    }

    private data class EpubChapter(
        val id: String,
        val href: String,
        val title: String,
        val divisionTitle: String,
        val content: String,
    )

    private data class EpubCover(
        val href: String,
        val mimeType: String,
        val bytes: ByteArray,
    )

    private companion object {
        const val DEFAULT_DIVISION_TITLE = "作品相关"
        const val HEADING_TAIL_LIMIT = 28
        const val COVER_MAX_BYTES = 8 * 1024 * 1024
        const val COVER_CONNECT_TIMEOUT_MS = 8_000
        const val COVER_READ_TIMEOUT_MS = 12_000
        val CHAPTER_PREFIX_PATTERN = Regex(
            """^\s*(?:第\s{0,4}[0-9０-９零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+\s{0,4}[章节卷集部篇回话]|序章|楔子|终章|尾声|番外)\s*""",
        )
        val NUMBER_PREFIX_PATTERN = Regex("""^\s*[0-9０-９]{1,5}\s*[:：,.，、_\-—]\s*""")
        val ENGLISH_PREFIX_PATTERN = Regex("""^\s*(?:chapter|section|part|episode|no[.、]?)\s*\d{1,4}\s*""", RegexOption.IGNORE_CASE)
    }
}
