plugins {
    id("com.android.application") version "9.1.1" apply false
}

tasks.register("verifyArchitecture") {
    group = "verification"
    description = "Checks CWMHook architecture boundaries and banned APIs."

    doLast {
        fun verifyForbiddenTrackedFiles() {
            if (!file(".git").exists()) {
                return
            }

            val process = ProcessBuilder(
                "git",
                "-C",
                projectDir.absolutePath,
                "ls-files",
                "-z",
            ).redirectErrorStream(true).start()
            val output = process.inputStream.readBytes()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw org.gradle.api.GradleException(
                    "Cannot verify forbidden tracked files because git ls-files failed with exit code $exitCode.",
                )
            }

            val forbiddenPrefixes = listOf(
                ".claude/",
                ".gradle/",
                ".idea/",
                ".kotlin/",
                "app/.cxx/",
                "app/release/",
                "build/",
                "io/",
                "key/",
                "local_docs/",
                "log/",
                "target_app/",
                "tmp_jadx_async/",
                "tools/",
            )
            val forbiddenExtensions = setOf(
                "aab",
                "apk",
                "apks",
                "db",
                "dex",
                "har",
                "hprof",
                "jks",
                "key",
                "keystore",
                "log",
                "p12",
                "pcap",
                "pem",
                "pfx",
                "prof",
                "rar",
                "so",
                "sqlite",
                "zip",
                "7z",
            )
            val forbidden = output.toString(Charsets.UTF_8)
                .split('\u0000')
                .filter { it.isNotEmpty() }
                .filter { path ->
                    val normalized = path.replace('\\', '/').lowercase()
                    val fileName = normalized.substringAfterLast('/')
                    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
                    normalized == "local.properties" ||
                        normalized == "build.log" ||
                        (fileName.startsWith(".env") && fileName != ".env.example") ||
                        forbiddenPrefixes.any(normalized::startsWith) ||
                        "/build/" in normalized ||
                        extension in forbiddenExtensions
                }

            if (forbidden.isNotEmpty()) {
                throw org.gradle.api.GradleException(
                    "Local, generated, reverse-engineering, or sensitive files must never be tracked. " +
                        "Do not bypass .gitignore with git add -f. Remove them from the index with " +
                        "git rm --cached while keeping the local files:" +
                        System.lineSeparator() +
                        forbidden.joinToString(System.lineSeparator()),
                )
            }
        }

        fun sourceFilesUnder(root: java.io.File): Sequence<java.io.File> {
            if (!root.exists()) {
                return emptySequence()
            }
            return if (root.isDirectory) {
                root.walkTopDown().filter { file ->
                    file.isFile && (
                        file.extension in setOf("kt", "kts", "java", "xml") ||
                            file.name == "AndroidManifest.xml" ||
                            file.name == "xposed" + "_init"
                        )
                }
            } else {
                sequenceOf(root)
            }
        }

        fun relativePath(file: java.io.File): String {
            return file.relativeTo(projectDir).invariantSeparatorsPath
        }

        fun scan(
            title: String,
            files: Sequence<java.io.File>,
            pattern: Regex,
        ) {
            val matches = mutableListOf<String>()
            files.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (pattern.containsMatchIn(line)) {
                        matches += "${relativePath(file)}:${index + 1}: ${line.trim()}"
                    }
                }
            }
            if (matches.isNotEmpty()) {
                val shown = matches.take(80).joinToString(System.lineSeparator())
                val suffix = if (matches.size > 80) {
                    "${System.lineSeparator()}... and ${matches.size - 80} more"
                } else {
                    ""
                }
                throw org.gradle.api.GradleException("$title${System.lineSeparator()}$shown$suffix")
            }
        }

        fun pieces(vararg parts: String): String {
            return parts.joinToString("")
        }

        verifyForbiddenTrackedFiles()

        val appSourceFiles = sourceFilesUnder(file("app/src"))
        val buildConfigFiles = sequenceOf(file("app/build.gradle.kts"), file("settings.gradle.kts"))
            .filter { it.exists() }
        val appAndBuildFiles = appSourceFiles + buildConfigFiles

        scan(
            title = "Deprecated Xposed APIs are not allowed.",
            files = appAndBuildFiles,
            pattern = Regex(
                listOf(
                    pieces("de\\.ro", "bv\\.android\\.xposed"),
                    pieces("IXposed", "HookLoadPackage"),
                    pieces("Xposed", "Bridge"),
                    pieces("Xposed", "Helpers"),
                    pieces("XShared", "Preferences"),
                    pieces("assets/xposed", "_init"),
                    pieces("xposed", "_init"),
                ).joinToString("|"),
            ),
        )
        scan(
            title = "Compose, Material Components, and blur/render-effect dependencies are not allowed.",
            files = appAndBuildFiles,
            pattern = Regex(
                listOf(
                    pieces("androidx\\.com", "pose"),
                    pieces("com", "pose"),
                    pieces("material", "3"),
                    pieces("com\\.google\\.android\\.mat", "erial"),
                    pieces("Blur", "View"),
                    pieces("Render", "Effect"),
                ).joinToString("|"),
            ),
        )
        scan(
            title = "The standalone module console and libxposed service are not allowed.",
            files = appAndBuildFiles,
            pattern = Regex(
                listOf(
                    pieces("""io\.github\.libxposed""", ":service"),
                    pieces("""io\.github\.libxposed\.ser""", "vice"),
                    pieces("Xposed", "Service"),
                    pieces("ModuleControl", "Activity"),
                    pieces("CwmHook", "Application"),
                    pieces("""android\.intent\.category\.LAUN""", "CHER"),
                ).joinToString("|"),
            ),
        )
        scan(
            title = "UI layer must not install Xposed hooks directly.",
            files = sourceFilesUnder(file("app/src/main/kotlin/com/xiyunmn/cwmhook/ui")),
            pattern = Regex("io\\.github\\.libxposed\\.api|XposedModule|\\.hook\\("),
        )
        scan(
            title = "Config layer must not depend on Android View/widget classes.",
            files = sourceFilesUnder(file("app/src/main/kotlin/com/xiyunmn/cwmhook/config")),
            pattern = Regex("android\\.view\\.|android\\.widget\\."),
        )
        scan(
            title = "Feature layer must not import UI layer directly.",
            files = sourceFilesUnder(file("app/src/main/kotlin/com/xiyunmn/cwmhook/feature")),
            pattern = Regex("com\\.xiyunmn\\.cwmhook\\.ui\\."),
        )
        scan(
            title = "Host package/class strings should stay in host layer.",
            files = sourceFilesUnder(file("app/src/main/kotlin/com/xiyunmn/cwmhook"))
                .filter { file -> "/host/" !in relativePath(file) },
            pattern = Regex("com\\.kuangxiangciweimao"),
        )
        scan(
            title = "Raw Xposed hook installation must go through core/XposedCompat.",
            files = sourceFilesUnder(file("app/src/main/kotlin/com/xiyunmn/cwmhook"))
                .filter { file -> relativePath(file) != "app/src/main/kotlin/com/xiyunmn/cwmhook/core/XposedCompat.kt" },
            pattern = Regex(
                listOf(
                    pieces("\\.ho", "ok\\("),
                    pieces("setException", "Mode\\(XposedInterface\\.ExceptionMode\\.PROTECTIVE\\)"),
                ).joinToString("|"),
            ),
        )

        val rootLogger = file("app/src/main/kotlin/com/xiyunmn/cwmhook/ModuleFileLogger.kt")
        if (rootLogger.exists()) {
            throw org.gradle.api.GradleException(
                "ModuleFileLogger must stay in core/logging: ${relativePath(rootLogger)}",
            )
        }

        val rootPackageDir = file("app/src/main/kotlin/com/xiyunmn/cwmhook")
        val misplacedRootFiles = rootPackageDir.listFiles()
            ?.filter { it.isFile && it.extension == "kt" }
            ?.map { relativePath(it) }
            .orEmpty()
        if (misplacedRootFiles.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Root package must stay empty; put entry/facade/features in named layers." +
                    System.lineSeparator() +
                    misplacedRootFiles.joinToString(System.lineSeparator()),
            )
        }

        val javaInit = file("app/src/main/resources/META-INF/xposed/java_init.list")
        val javaInitEntries = javaInit.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        val expectedEntry = "com.xiyunmn.cwmhook.entry.CiweiMaoHookModule"
        if (javaInitEntries != listOf(expectedEntry)) {
            throw org.gradle.api.GradleException(
                "libxposed java_init.list must point to $expectedEntry, found: $javaInitEntries",
            )
        }

        val nativeInit = file("app/src/main/resources/META-INF/xposed/native_init.list")
        if (nativeInit.exists()) {
            throw org.gradle.api.GradleException(
                "API 102 hot reload requires this module to stay Java-only; native_init.list is not allowed.",
            )
        }

        val moduleProp = file("app/src/main/resources/META-INF/xposed/module.prop")
        val moduleProperties = java.util.Properties().apply {
            moduleProp.inputStream().use(::load)
        }
        val requiredProps = mapOf(
            "minApiVersion" to "102",
            "targetApiVersion" to "102",
            "autoHotReload" to "true",
        )
        requiredProps.forEach { (key, expected) ->
            val actual = moduleProperties.getProperty(key)
            if (actual != expected) {
                throw org.gradle.api.GradleException(
                    "module.prop requires $key=$expected for API 102 hot reload, found: $actual",
                )
            }
        }
    }
}

gradle.projectsEvaluated {
    tasks.findByPath(":app:preBuild")?.dependsOn(tasks.named("verifyArchitecture"))
}
