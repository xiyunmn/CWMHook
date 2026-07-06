plugins {
    id("com.android.application") version "9.1.1" apply false
}

tasks.register("verifyArchitecture") {
    group = "verification"
    description = "Checks CWMHook architecture boundaries and banned APIs."

    doLast {
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
            title = "libxposed-service and remote preferences are not used by this module.",
            files = appAndBuildFiles,
            pattern = Regex(
                listOf(
                    pieces("io\\.github\\.libxposed", ":service"),
                    pieces("io\\.github\\.libxposed\\.ser", "vice"),
                    pieces("Xposed", "Service"),
                    pieces("getRemote", "Preferences"),
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
            val nativeInitEntries = nativeInit.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            val expectedNativeEntry = "libcwmhook_startup_probe.so"
            if (nativeInitEntries != listOf(expectedNativeEntry)) {
                throw org.gradle.api.GradleException(
                    "libxposed native_init.list must point to $expectedNativeEntry, found: $nativeInitEntries",
                )
            }
        }
    }
}

gradle.projectsEvaluated {
    tasks.findByPath(":app:preBuild")?.dependsOn(tasks.named("verifyArchitecture"))
}
