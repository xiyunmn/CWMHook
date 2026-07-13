package com.xiyunmn.cwmhook.feature.chapterbackup

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfig
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class ChapterBackupExporter(
    private val classLoader: ClassLoader,
    private val logTag: String,
) {
    interface Callback {
        fun onStarted()
        fun onSuccess(result: ChapterBackupResult)
        fun onFailure(message: String)
    }

    interface DownloadCallback : Callback {
        fun onDownloadStarted(chapterCount: Int)
        fun onDownloadProgress(progress: Int)
    }

    interface CandidateCallback {
        fun onStarted()
        fun onSuccess(book: ChapterBackupBook, candidates: List<ChapterBackupCandidate>)
        fun onFailure(message: String)
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CWMHookChapterExport").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeOperations = AtomicInteger()

    fun exportCachedBooks(context: Context, callback: Callback) {
        val appContext = context.applicationContext ?: context
        val tracked = tracked(callback)
        tracked.onStarted()
        executor.execute {
            val result = runCatching { exportCachedBooksOnWorker(appContext) }
            mainHandler.post {
                result
                    .onSuccess(tracked::onSuccess)
                    .onFailure { throwable ->
                        ModuleFileLogger.e(logTag, "Cached chapter export failed", throwable)
                        tracked.onFailure(throwable.message ?: "导出失败")
                    }
            }
        }
    }

    fun loadBookCandidates(context: Context, bookInfo: Any?, callback: CandidateCallback) {
        val book = bookInfo?.toChapterBackupBook() ?: run {
            callback.onFailure("未识别当前作品")
            return
        }
        loadBookCandidates(context, book, callback)
    }

    fun loadBookCandidates(context: Context, book: ChapterBackupBook, callback: CandidateCallback) {
        val appContext = context.applicationContext ?: context
        val tracked = tracked(callback)
        tracked.onStarted()
        executor.execute {
            val result = runCatching { loadBookCandidatesOnWorker(appContext, book) }
            mainHandler.post {
                result
                    .onSuccess { tracked.onSuccess(book, it) }
                    .onFailure { throwable ->
                        ModuleFileLogger.e(logTag, "Load chapter export candidates failed: ${book.bookId}", throwable)
                        tracked.onFailure(throwable.message ?: "读取目录失败")
                    }
            }
        }
    }

    fun exportSelectedChapters(
        context: Context,
        book: ChapterBackupBook,
        selected: List<ChapterBackupCandidate>,
        naming: ChapterBackupNaming? = null,
        format: ChapterBackupFormat = ChapterBackupFormat.TXT,
        callback: Callback,
    ) {
        val appContext = context.applicationContext ?: context
        if (selected.isEmpty()) {
            callback.onFailure("请先选择章节")
            return
        }
        val tracked = tracked(callback)
        tracked.onStarted()
        executor.execute {
            val result = runCatching { exportSelectedChaptersOnWorker(appContext, book, selected, naming, format) }
            mainHandler.post {
                result
                    .onSuccess(tracked::onSuccess)
                    .onFailure { throwable ->
                        ModuleFileLogger.e(logTag, "Selected chapter export failed: ${book.bookId}", throwable)
                        tracked.onFailure(throwable.message ?: "导出失败")
                    }
            }
        }
    }

    fun exportSelectedChaptersWithHostDownload(
        context: Context,
        book: ChapterBackupBook,
        selected: List<ChapterBackupCandidate>,
        downloadType: String?,
        naming: ChapterBackupNaming? = null,
        format: ChapterBackupFormat = ChapterBackupFormat.TXT,
        callback: Callback,
    ) {
        val appContext = context.applicationContext ?: context
        val distinct = selected.distinctBy { it.chapterId }
        if (distinct.isEmpty()) {
            callback.onFailure("请先选择章节")
            return
        }
        val blocked = currentBlockedCount(appContext, book, distinct)
        if (blocked > 0) {
            callback.onFailure("包含 $blocked 章不可导出的章节，请重新选择")
            return
        }
        val tracked = tracked(callback)
        tracked.onStarted()
        val pendingDownloadIds = currentPendingDownloadIds(appContext, book, distinct)
        if (pendingDownloadIds.isEmpty()) {
            exportSelectedChaptersAfterDownload(appContext, book, distinct, naming, format, tracked)
            return
        }
        runHostDownloadPreflight(
            context = appContext,
            onSuccess = {
                startHostDownload(
                    context = appContext,
                    book = book,
                    selected = distinct,
                    pendingDownloadIds = pendingDownloadIds,
                    downloadType = downloadType,
                    naming = naming,
                    format = format,
                    callback = tracked,
                )
            },
            onFailure = tracked::onFailure,
        )
    }

    fun shutdownIfIdle(): Boolean {
        if (activeOperations.get() != 0) {
            return false
        }
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        return true
    }

    fun isIdle(): Boolean = activeOperations.get() == 0

    private fun tracked(callback: Callback): Callback {
        activeOperations.incrementAndGet()
        val finished = AtomicBoolean(false)
        fun finish() {
            if (finished.compareAndSet(false, true)) {
                activeOperations.decrementAndGet()
            }
        }
        return object : DownloadCallback {
            override fun onStarted() = callback.onStarted()

            override fun onDownloadStarted(chapterCount: Int) {
                (callback as? DownloadCallback)?.onDownloadStarted(chapterCount)
            }

            override fun onDownloadProgress(progress: Int) {
                (callback as? DownloadCallback)?.onDownloadProgress(progress)
            }

            override fun onSuccess(result: ChapterBackupResult) {
                finish()
                callback.onSuccess(result)
            }

            override fun onFailure(message: String) {
                finish()
                callback.onFailure(message)
            }
        }
    }

    private fun tracked(callback: CandidateCallback): CandidateCallback {
        activeOperations.incrementAndGet()
        val finished = AtomicBoolean(false)
        fun finish() {
            if (finished.compareAndSet(false, true)) {
                activeOperations.decrementAndGet()
            }
        }
        return object : CandidateCallback {
            override fun onStarted() = callback.onStarted()

            override fun onSuccess(book: ChapterBackupBook, candidates: List<ChapterBackupCandidate>) {
                finish()
                callback.onSuccess(book, candidates)
            }

            override fun onFailure(message: String) {
                finish()
                callback.onFailure(message)
            }
        }
    }

    private fun currentBlockedCount(
        context: Context,
        book: ChapterBackupBook,
        selected: List<ChapterBackupCandidate>,
    ): Int {
        return runCatching {
            val readerId = currentReaderId()
            selected.count { candidate ->
                !cacheState(context, book.bookId, candidate.chapterId, readerId).cached && !candidate.authorized
            }
        }.getOrElse { throwable ->
            ModuleFileLogger.w(logTag, "Failed to recheck blocked chapters before export: book=${book.bookId}", throwable)
            selected.count { !it.cached && !it.authorized }
        }
    }

    private fun currentPendingDownloadIds(
        context: Context,
        book: ChapterBackupBook,
        selected: List<ChapterBackupCandidate>,
    ): List<String> {
        return runCatching {
            val readerId = currentReaderId()
            selected
                .filter { candidate ->
                    !cacheState(context, book.bookId, candidate.chapterId, readerId).cached && candidate.authorized
                }
                .map { it.chapterId }
        }.getOrElse { throwable ->
            ModuleFileLogger.w(logTag, "Failed to recheck chapter cache before export: book=${book.bookId}", throwable)
            selected
                .filter { !it.cached && it.authorized }
                .map { it.chapterId }
        }
    }

    private fun exportSelectedChaptersAfterDownload(
        context: Context,
        book: ChapterBackupBook,
        selected: List<ChapterBackupCandidate>,
        naming: ChapterBackupNaming?,
        format: ChapterBackupFormat,
        callback: Callback,
    ) {
        executor.execute {
            val result = runCatching { exportSelectedChaptersOnWorker(context, book, selected, naming, format) }
            mainHandler.post {
                result
                    .onSuccess(callback::onSuccess)
                    .onFailure { throwable ->
                        ModuleFileLogger.e(logTag, "Selected chapter export after download failed: ${book.bookId}", throwable)
                        callback.onFailure(throwable.message ?: "导出失败")
                    }
            }
        }
    }

    private fun runHostDownloadPreflight(
        context: Context,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        mainHandler.post {
            runCatching {
                val taskClass = Class.forName(CiweiMaoClasses.CHECK_DOWN_TASK, false, classLoader)
                val baseTaskClass = Class.forName(CiweiMaoClasses.BASE_TASK_NEW, false, classLoader)
                val successClass = Class.forName("${CiweiMaoClasses.BASE_TASK_NEW}\$AsyncTaskSuccessCallback", false, classLoader)
                val failClass = Class.forName("${CiweiMaoClasses.BASE_TASK_NEW}\$AsyncTaskFailCallback", false, classLoader)
                val task = taskClass.getConstructor(Context::class.java).newInstance(context)
                val successProxy = Proxy.newProxyInstance(
                    classLoader,
                    arrayOf(successClass),
                    InvocationHandler { _, method, _ ->
                        if (method.name == "successCallback") {
                            onSuccess()
                        }
                        null
                    },
                )
                val failProxy = Proxy.newProxyInstance(
                    classLoader,
                    arrayOf(failClass),
                    InvocationHandler { _, method, args ->
                        if (method.name == "failCallback") {
                            onFailure((args?.getOrNull(0)?.callNoArgMethod("getMessage") as? String) ?: "下载前检查失败")
                        }
                        null
                    },
                )
                baseTaskClass.getMethod("setAsyncTaskSuccessCallback", successClass).invoke(task, successProxy)
                baseTaskClass.getMethod("setAsyncTaskFailCallback", failClass).invoke(task, failProxy)
                baseTaskClass.getMethod("execute", Array<Any>::class.java)
                    .invoke(task, emptyArray<Any>())
            }.onFailure { throwable ->
                ModuleFileLogger.e(logTag, "Host download preflight failed", throwable)
                onFailure(throwable.message ?: "下载前检查失败")
            }
        }
    }

    private fun startHostDownload(
        context: Context,
        book: ChapterBackupBook,
        selected: List<ChapterBackupCandidate>,
        pendingDownloadIds: List<String>,
        downloadType: String?,
        naming: ChapterBackupNaming?,
        format: ChapterBackupFormat,
        callback: Callback,
    ) {
        mainHandler.post {
            var restoreHostListener: (() -> Unit)? = null
            runCatching {
                val downThreadClass = Class.forName(CiweiMaoClasses.BUY_DOWN_THREAD, false, classLoader)
                val instance = downThreadClass.getMethod("getIntance").invoke(null)
                val listenerClass = downThreadClass.declaredClasses.first { it.simpleName == "OnDownComplete" }
                val listenerField = downThreadClass.getDeclaredField("oncomplete").also { it.isAccessible = true }
                val originalListener = listenerField.get(instance)
                val completed = AtomicBoolean(false)

                fun restoreListener() {
                    runCatching { listenerField.set(instance, originalListener) }
                        .onFailure { ModuleFileLogger.w(logTag, "Failed to restore host download listener", it) }
                }
                restoreHostListener = ::restoreListener

                fun finishDownload(reason: String) {
                    if (!completed.compareAndSet(false, true)) {
                        return
                    }
                    restoreListener()
                    ModuleFileLogger.i(logTag, "Host chapter download complete: book=${book.bookId}, reason=$reason")
                    mainHandler.postDelayed(
                        { exportSelectedChaptersAfterDownload(context, book, selected, naming, format, callback) },
                        EXPORT_AFTER_DOWNLOAD_DELAY_MS,
                    )
                }

                val proxy = Proxy.newProxyInstance(
                    classLoader,
                    arrayOf(listenerClass),
                    InvocationHandler { _, method, args ->
                        invokeOriginalDownloadListener(originalListener, method, args)
                        if ((method.name == "onSuc" || method.name == "onFail") && args?.getOrNull(0) == book.bookId) {
                            val progress = args.getOrNull(2) as? Int ?: -1
                            (callback as? DownloadCallback)?.onDownloadProgress(progress)
                            if (progress >= 100) {
                                finishDownload(method.name)
                            }
                        }
                        null
                    },
                )
                downThreadClass.getMethod("setOnDownCompleteListener", listenerClass).invoke(instance, proxy)
                (callback as? DownloadCallback)?.onDownloadStarted(pendingDownloadIds.size)
                ModuleFileLogger.i(
                    logTag,
                    "Host chapter download starts: book=${book.bookId}, pending=${pendingDownloadIds.size}, selected=${selected.size}",
                )
                downThreadClass.getMethod("downList", String::class.java, List::class.java, String::class.java)
                    .invoke(instance, book.bookId, ArrayList(pendingDownloadIds), downloadType)
                mainHandler.postDelayed(
                    {
                        if (completed.compareAndSet(false, true)) {
                            restoreListener()
                            ModuleFileLogger.w(logTag, "Host chapter download timeout: book=${book.bookId}")
                            exportSelectedChaptersAfterDownload(context, book, selected, naming, format, callback)
                        }
                    },
                    DOWNLOAD_TIMEOUT_MS,
                )
            }.onFailure { throwable ->
                restoreHostListener?.invoke()
                ModuleFileLogger.e(logTag, "Host chapter download failed to start: book=${book.bookId}", throwable)
                callback.onFailure(throwable.message ?: "下载启动失败")
            }
        }
    }

    private fun invokeOriginalDownloadListener(originalListener: Any?, method: Method, args: Array<out Any?>?) {
        if (originalListener == null) {
            return
        }
        runCatching {
            method.invoke(originalListener, *(args ?: emptyArray()))
        }.onFailure { throwable ->
            val cause = (throwable as? InvocationTargetException)?.targetException ?: throwable
            ModuleFileLogger.w(logTag, "Original host download listener failed", cause)
        }
    }

    private fun exportCachedBooksOnWorker(context: Context): ChapterBackupResult {
        val config = ChapterBackupConfigStore.readLocal(context)
        requireEnabled(config)
        val destination = ChapterBackupDestination(context, config.exportTreeUri, logTag)
        val books = shelfBooks()
        val batchName = "刺猬猫章节导出_${timestamp()}"
        var exportedBooks = 0
        var exportedChapters = 0
        var skipped = 0

        books.forEach { book ->
            val chapters = cachedReadableChapters(context, book)
            if (chapters.isEmpty()) {
                skipped += 1
                return@forEach
            }
            destination.writeBookBackup(batchName, book, chapters)
            exportedBooks += 1
            exportedChapters += chapters.size
        }

        if (exportedChapters == 0) {
            error("没有找到可导出的已缓存章节")
        }
        val result = ChapterBackupResult(
            bookCount = exportedBooks,
            chapterCount = exportedChapters,
            skippedCount = skipped,
            outputLabel = destination.rootLabel(batchName),
        )
        ModuleFileLogger.i(
            logTag,
            "Cached chapter export finished: books=${result.bookCount}, chapters=${result.chapterCount}, skipped=${result.skippedCount}, output=${result.outputLabel}",
        )
        return result
    }

    private fun loadBookCandidatesOnWorker(
        context: Context,
        book: ChapterBackupBook,
    ): List<ChapterBackupCandidate> {
        val readerId = currentReaderId()
        val divisions = divisionNames(book.bookId)
        return catalogChapters(book.bookId).mapNotNull { chapterInfo ->
            val chapterId = chapterInfo.stringMethod("getChapter_id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val divisionId = chapterInfo.stringMethod("getDivision_id")
            val authorized = hasAuth(book.bookId, chapterId)
            val cacheState = cacheState(context, book.bookId, chapterId, readerId)
            ChapterBackupCandidate(
                chapterId = chapterId,
                title = chapterInfo.stringMethod("getChapter_title") ?: chapterId,
                index = chapterInfo.intMethod("getChapter_index"),
                divisionId = divisionId,
                divisionTitle = divisionId?.let { divisions[it] } ?: "作品相关",
                authorized = authorized,
                cached = cacheState.cached,
                estimatedBytes = cacheState.estimatedBytes,
            )
        }.sortedWith(compareBy<ChapterBackupCandidate> { it.index < 0 }.thenBy { it.index })
    }

    private fun exportSelectedChaptersOnWorker(
        context: Context,
        book: ChapterBackupBook,
        selected: List<ChapterBackupCandidate>,
        naming: ChapterBackupNaming?,
        format: ChapterBackupFormat,
    ): ChapterBackupResult {
        val config = ChapterBackupConfigStore.readLocal(context)
        requireEnabled(config)
        val readerId = currentReaderId()
        val destination = ChapterBackupDestination(context, config.exportTreeUri, logTag)
        val effectiveNaming = naming ?: ChapterBackupNaming(
            directoryName = book.defaultExportBaseName(),
            fileName = book.defaultExportBaseName(),
        )
        var skipped = 0
        val chapters = selected.mapNotNull { candidate ->
            val content = readCachedContent(context, book.bookId, candidate.chapterId, readerId)
            if (content == null) {
                skipped += 1
                return@mapNotNull null
            }
            ChapterBackupChapter(
                chapterId = candidate.chapterId,
                title = candidate.title,
                index = candidate.index,
                divisionTitle = candidate.divisionTitle,
                content = content,
            )
        }.sortedBy { it.index }
        if (chapters.isEmpty()) {
            error("没有可导出的已缓存章节")
        }
        destination.writeBookBackup(null, book, chapters, effectiveNaming, format)
        val result = ChapterBackupResult(
            bookCount = 1,
            chapterCount = chapters.size,
            skippedCount = skipped,
            outputLabel = destination.pathLabel(listOf(effectiveNaming.directoryName)),
        )
        ModuleFileLogger.i(
            logTag,
            "Selected chapter export finished: book=${book.bookId}, format=${format.displayName}, chapters=${result.chapterCount}, skipped=${result.skippedCount}, output=${result.outputLabel}",
        )
        return result
    }

    private fun cachedReadableChapters(context: Context, book: ChapterBackupBook): List<ChapterBackupChapter> {
        val readerId = currentReaderId()
        val divisions = divisionNames(book.bookId)
        val chapters = catalogChapters(book.bookId)
        return chapters.mapNotNull { chapterInfo ->
            val chapterId = chapterInfo.stringMethod("getChapter_id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val divisionId = chapterInfo.stringMethod("getDivision_id")
            val content = readCachedContent(context, book.bookId, chapterId, readerId)
                ?: return@mapNotNull null
            ChapterBackupChapter(
                chapterId = chapterId,
                title = chapterInfo.stringMethod("getChapter_title") ?: chapterId,
                index = chapterInfo.intMethod("getChapter_index"),
                divisionTitle = divisionId?.let { divisions[it] } ?: "作品相关",
                content = content,
            )
        }.sortedBy { it.index }
    }

    private fun readCachedContent(
        context: Context,
        bookId: String,
        chapterId: String,
        readerId: String,
    ): String? {
        val paths = chapterCachePaths(context, bookId, chapterId, readerId)
        val gzipFile = paths.gzipFile
        if (gzipFile.exists()) {
            return gzipFile.readText(StandardCharsets.UTF_8).takeIf { it.isNotBlank() }
        }

        val textFile = paths.textFile
        val keyFile = paths.keyFile
        if (!textFile.exists() || !keyFile.exists()) {
            return null
        }
        val cipherText = textFile.readText(StandardCharsets.UTF_8)
        val key = keyFile.readText(StandardCharsets.UTF_8)
        return runCatching { decrypt(cipherText, key).takeIf { it.isNotBlank() } }
            .getOrElse { throwable ->
                ModuleFileLogger.w(logTag, "Failed to decrypt cached chapter: book=$bookId chapter=$chapterId", throwable)
                null
            }
    }

    private fun cacheState(context: Context, bookId: String, chapterId: String, readerId: String): ChapterCacheState {
        val paths = chapterCachePaths(context, bookId, chapterId, readerId)
        val gzipFile = paths.gzipFile
        if (gzipFile.exists()) {
            return ChapterCacheState(cached = true, estimatedBytes = gzipFile.length().coerceAtLeast(0L))
        }
        val textFile = paths.textFile
        val keyFile = paths.keyFile
        return ChapterCacheState(
            cached = textFile.exists() && keyFile.exists(),
            estimatedBytes = if (textFile.exists()) textFile.length().coerceAtLeast(0L) else 0L,
        )
    }

    private fun shelfBooks(): List<ChapterBackupBook> {
        val daoFactory = Class.forName(CiweiMaoClasses.DAO_FACTORY, false, classLoader)
        val shelfDao = daoFactory.getMethod("getShelfBookInfoDao").invoke(null) ?: return emptyList()
        val list = shelfDao.javaClass.getMethod("queryAll").invoke(shelfDao) as? List<*> ?: return emptyList()
        return list.mapNotNull { shelf ->
            val bookInfo = shelf?.javaClass?.getMethod("getBook_info")?.invoke(shelf) ?: return@mapNotNull null
            bookInfo.toChapterBackupBook()
        }.distinctBy { it.bookId }
    }

    private fun catalogChapters(bookId: String): List<Any> {
        val daoFactory = Class.forName(CiweiMaoClasses.DAO_FACTORY, false, classLoader)
        val catalogDao = daoFactory.getMethod("getCatalogdao").invoke(null) ?: return emptyList()
        val list = catalogDao.javaClass.getMethod("getChapterInfo", String::class.java)
            .invoke(catalogDao, bookId) as? List<*> ?: return emptyList()
        return list.filterNotNull()
    }

    private fun divisionNames(bookId: String): Map<String, String> {
        return runCatching {
            val daoFactory = Class.forName(CiweiMaoClasses.DAO_FACTORY, false, classLoader)
            val divisionDao = daoFactory.getMethod("getDivisiondao").invoke(null) ?: return emptyMap()
            val list = divisionDao.javaClass.getMethod("getDivisionInfo", String::class.java)
                .invoke(divisionDao, bookId) as? List<*> ?: return emptyMap()
            list.mapNotNull { division ->
                val id = division?.stringMethod("getDivision_id") ?: return@mapNotNull null
                val name = division.stringMethod("getDivision_name") ?: id
                id to name
            }.toMap()
        }.getOrElse { throwable ->
            ModuleFileLogger.w(logTag, "Failed to read division names: book=$bookId", throwable)
            emptyMap()
        }
    }

    private fun hasAuth(bookId: String, chapterId: String): Boolean {
        return runCatching {
            val daoFactory = Class.forName(CiweiMaoClasses.DAO_FACTORY, false, classLoader)
            val catalogDao = daoFactory.getMethod("getCatalogdao").invoke(null) ?: return false
            val auth = catalogDao.javaClass.getMethod("hasAuth", String::class.java, String::class.java)
                .invoke(catalogDao, bookId, chapterId) as? Int
            auth == 1
        }.getOrElse { throwable ->
            ModuleFileLogger.w(logTag, "Failed to read chapter auth: book=$bookId chapter=$chapterId", throwable)
            false
        }
    }

    private fun decrypt(cipherText: String, key: String): String {
        val cryptoClass = Class.forName(CiweiMaoClasses.CHAPTER_CRYPTO, false, classLoader)
        return cryptoClass.getMethod("a", String::class.java, String::class.java)
            .invoke(null, cipherText, key) as String
    }

    private fun currentReaderId(): String {
        val userClass = Class.forName(CiweiMaoClasses.LOGINED_USER, false, classLoader)
        val user = userClass.getMethod("getLoginedUser").invoke(null)
        val readerInfo = user.javaClass.getMethod("getReaderInfo").invoke(user)
        return readerInfo.javaClass.getMethod("getReader_id").invoke(readerInfo) as String
    }

    private fun chapterCachePaths(
        context: Context,
        bookId: String,
        chapterId: String,
        readerId: String,
    ): ChapterCachePaths {
        val bookDir = File(context.filesDir, "novelCiwei/reader/booksnew/$bookId")
        val fallbackText = File(bookDir, "$chapterId.txt")
        val fallbackGzip = File(bookDir, "${chapterId}gz.txt")
        val fallbackKey = File(context.filesDir, "Yrhlcsy8/${readerChapterKey(chapterId, readerId)}")
        return ChapterCachePaths(
            textFile = hostFile("getDownloadingPath", bookId, chapterId) ?: fallbackText,
            gzipFile = hostFile("getDownloadingPathG", bookId, chapterId) ?: fallbackGzip,
            keyFile = hostKeyFile(chapterId, readerId) ?: fallbackKey,
        )
    }

    private fun hostFile(methodName: String, bookId: String, chapterId: String): File? {
        return runCatching {
            val downThreadClass = Class.forName(CiweiMaoClasses.BUY_DOWN_THREAD, false, classLoader)
            downThreadClass.getDeclaredMethod(methodName, String::class.java, String::class.java)
                .also { it.isAccessible = true }
                .invoke(null, bookId, chapterId) as? String
        }.getOrNull()?.let(::File)
    }

    private fun hostKeyFile(chapterId: String, readerId: String): File? {
        return runCatching {
            val downThreadClass = Class.forName(CiweiMaoClasses.BUY_DOWN_THREAD, false, classLoader)
            downThreadClass.getDeclaredMethod("g1", String::class.java)
                .also { it.isAccessible = true }
                .invoke(null, readerChapterKey(chapterId, readerId)) as? String
        }.getOrNull()?.let(::File)
    }

    private fun readerChapterKey(chapterId: String, readerId: String): String {
        return runCatching {
            val base64Class = Class.forName(CiweiMaoClasses.BASE64_HELPER, false, classLoader)
            base64Class.getMethod("b1", String::class.java)
                .invoke(null, "$chapterId$readerId") as? String
        }.getOrNull() ?: android.util.Base64.encodeToString(
            "$chapterId$readerId".toByteArray(StandardCharsets.UTF_8),
            android.util.Base64.DEFAULT,
        ).trim()
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun requireEnabled(config: ChapterBackupConfig) {
        if (!config.enabled) {
            error("个人章节导出未启用")
        }
    }

    private data class ChapterCacheState(
        val cached: Boolean,
        val estimatedBytes: Long,
    )

    private data class ChapterCachePaths(
        val textFile: File,
        val gzipFile: File,
        val keyFile: File,
    )

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L
        const val EXPORT_AFTER_DOWNLOAD_DELAY_MS = 350L
    }
}
