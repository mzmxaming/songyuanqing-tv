import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.io.IOException
import java.net.URI

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/** 随包内置的 libVLC 版本。 */
val vlcVersion = "3.0.21"

/** 当前构建宿主系统。 */
val hostOs = System.getProperty("os.name").lowercase()
val hostIsWindows = hostOs.contains("win")
val hostIsMac = hostOs.contains("mac")
val hostIsArm = System.getProperty("os.arch").lowercase().contains("aarch64")

/**
 * 应用资源根目录。
 *
 * 结构约定：<root>/common、<root>/windows、<root>/macos、<root>/linux，
 * Compose 打包时会把对应系统目录的内容复制到安装包的 app/resources 下，
 * 运行时可通过系统属性 compose.application.resources.dir 定位。
 */
val appResourcesRoot = layout.buildDirectory.dir("appResources")

/** 本次构建要落位的 libVLC 目录（Windows / macOS 由构建任务下载；Linux 由 CI 容器准备）。 */
fun stagedVlcDir(): File = appResourcesRoot.get().asFile.resolve(
    when {
        hostIsWindows -> "windows/vlc"
        hostIsMac -> "macos/vlc"
        else -> "linux/vlc"
    }
)

/**
 * 下载文件（存在且非空则跳过）。
 *
 * 功能：按顺序尝试多个地址（国内镜像优先，官方源兜底），整体重试 3 轮。
 *      CI 上曾出现镜像偶发断连导致打包失败，重试可避免整条流水线失败。
 * @param urls 候选下载地址。
 * @param target 目标文件。
 */
fun downloadIfMissing(urls: List<String>, target: File) {
    if (target.isFile && target.length() > 0) return
    target.parentFile.mkdirs()
    val temp = File(target.parentFile, target.name + ".part")
    var lastError: Exception? = null
    for (attempt in 1..3) {
        for (url in urls) {
            try {
                println("下载 $url（第 $attempt 次尝试）")
                URI.create(url).toURL().openConnection().apply {
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; SongyuanqingTV-Build)")
                }.getInputStream().use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (temp.renameTo(target)) return
                temp.copyTo(target, overwrite = true)
                temp.delete()
                return
            } catch (error: Exception) {
                lastError = error
                println("下载失败（${error.message}），尝试下一个地址")
                temp.delete()
            }
        }
        if (attempt < 3) Thread.sleep(3_000)
    }
    throw IOException("libVLC 下载失败，请检查网络或手动放置到 ${target.parentFile.absolutePath}", lastError)
}

/**
 * 下载并裁剪 libVLC，落位到应用资源目录，实现播放器随包内置。
 *
 * Windows 取官方 win64 zip；macOS 取官方 dmg 后只保留 lib 与 plugins；
 * Linux 由系统包管理器提供 libVLC，此任务自动跳过。
 */
val prepareBundledVlc = tasks.register("prepareBundledVlc") {
    group = "distribution"
    description = "下载并裁剪 libVLC，随安装包内置（Windows / macOS）"
    onlyIf { hostIsWindows || hostIsMac }

    val stageDir = stagedVlcDir()
    outputs.dir(stageDir).withPropertyName("bundledVlc")

    doLast {
        val marker = File(stageDir, if (hostIsWindows) "libvlc.dll" else "lib/libvlc.dylib")
        if (marker.isFile) {
            logger.lifecycle("libVLC 已就位：${stageDir.absolutePath}")
            return@doLast
        }
        stageDir.mkdirs()
        val cacheDir = layout.buildDirectory.dir("vlc-cache").get().asFile.apply { mkdirs() }

        if (hostIsWindows) {
            val zip = File(cacheDir, "vlc-$vlcVersion-win64.zip")
            val path = "vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
            downloadIfMissing(
                listOf(
                    "https://mirrors.tuna.tsinghua.edu.cn/videolan-ftp/$path",
                    "https://mirrors.aliyun.com/videolan/$path",
                    "https://mirrors.ustc.edu.cn/videolan-ftp/$path",
                    "https://download.videolan.org/$path"
                ),
                zip
            )
            val extractDir = File(cacheDir, "win64")
            if (!File(extractDir, "vlc-$vlcVersion").isDirectory) {
                project.copy {
                    from(project.zipTree(zip))
                    into(extractDir)
                }
            }
            project.copy {
                from(File(extractDir, "vlc-$vlcVersion"))
                into(stageDir)
                // 只保留播放所需的运行库与插件：去掉 Qt 图形界面、多语言、推流与浏览器插件等无关内容。
                // locale 一项就有 40MB，纯界面资源，必须剔除。
                exclude(
                    "vlc.exe",
                    "vlc-cache-gen.exe",
                    "axvlc.dll",
                    "npvlc.dll",
                    "sdk/**",
                    "skins/**",
                    "hrtfs/**",
                    "locale/**",
                    "msi/**",
                    "Qt5*.dll",
                    "libqt5*.dll",
                    "qt.conf",
                    "plugins/gui/**",
                    "plugins/visualization/**",
                    "plugins/control/**",
                    "plugins/access_output/**",
                    "plugins/stream_out/**",
                    "plugins/mux/**"
                )
            }
        } else {
            val arch = if (hostIsArm) "arm64" else "intel64"
            val dmg = File(cacheDir, "vlc-$vlcVersion-$arch.dmg")
            val path = "vlc/$vlcVersion/macosx/vlc-$vlcVersion-$arch.dmg"
            downloadIfMissing(
                listOf(
                    "https://mirrors.tuna.tsinghua.edu.cn/videolan-ftp/$path",
                    "https://mirrors.aliyun.com/videolan/$path",
                    "https://mirrors.ustc.edu.cn/videolan-ftp/$path",
                    "https://download.videolan.org/$path"
                ),
                dmg
            )
            val mountPoint = File(cacheDir, "dmg-$arch").apply { mkdirs() }
            project.exec { commandLine("hdiutil", "attach", dmg.absolutePath, "-nobrowse", "-readonly", "-mountpoint", mountPoint.absolutePath) }
            try {
                val macOsDir = File(mountPoint, "VLC.app/Contents/MacOS")
                // 用 cp -a 保留 dylib 之间的相对符号链接
                project.exec {
                    commandLine("cp", "-a", File(macOsDir, "lib").absolutePath, File(stageDir, "lib").absolutePath)
                }
                project.exec {
                    commandLine("cp", "-a", File(macOsDir, "plugins").absolutePath, File(stageDir, "plugins").absolutePath)
                }
            } finally {
                project.exec { commandLine("hdiutil", "detach", mountPoint.absolutePath, "-force") }
            }
        }
        logger.lifecycle("libVLC 已内置到 ${stageDir.absolutePath}")
    }
}

// 所有打包 / 运行任务都先准备内置 libVLC
tasks.configureEach {
    if (name == "prepareAppResources" || name == "createDistributable" ||
        name == "runDistributable" || name.startsWith("package")
    ) {
        dependsOn(prepareBundledVlc)
    }
}

// 开发期 run 也要能用到内置库（打包后由 compose.application.resources.dir 自动指向）
afterEvaluate {
    tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
        dependsOn(prepareBundledVlc)
        systemProperty("bundled.vlc.dir", stagedVlcDir().absolutePath)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.vlcj)
    runtimeOnly(libs.slf4j.simple)
}

compose.desktop {
    application {
        mainClass = "com.bocchi.tv.desktop.MainKt"

        // Compose 会按「当前宿主系统」校验目标格式（例如 macOS 上不允许声明 AppImage），
        // 因此这里按平台声明，CI 的各系统 runner 各取所需。
        val formats = when {
            hostIsWindows -> listOf(TargetFormat.Msi, TargetFormat.Exe)
            hostIsMac -> listOf(TargetFormat.Dmg)
            // AppImage 在当前 Compose 版本下是空转任务（不产出文件），Linux 用 deb / rpm 即可覆盖
            // 银河麒麟 V10（Debian 系）与中标麒麟（RHEL 系），再附一个便携 tar.gz。
            else -> listOf(TargetFormat.Deb, TargetFormat.Rpm)
        }

        nativeDistributions {
            targetFormats(*formats.toTypedArray())
            packageName = "SongyuanqingTV"
            packageVersion = "1.2.7"
            // 随包内置 libVLC，用户无需另行安装 VLC
            appResourcesRootDir.set(appResourcesRoot)
            // WiX 的 light.exe 在非中文代码页（如 CI 的 en-US）下无法处理中文元数据，会以退出码 311 失败，
            // 因此安装包元信息保持 ASCII；界面内的中文不受影响。
            description = "Songyuanqing TV Desktop - multi-source movie streaming"
            vendor = "Chen Guangming"
            copyright = "(c) Chen Guangming"
            // jdk.unsupported 提供 sun.misc.Unsafe，VLCJ 回调渲染需要；缺少它会导致画面无法输出。
            modules("java.sql", "java.naming", "jdk.unsupported")

            windows {
                menuGroup = "SongyuanqingTV"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "6f171e53-7c4e-4a2b-9d31-8c2ea9b462b0"
            }
            macOS {
                bundleID = "vip.songyuanqing.tv"
                dockName = "松源情·TV"
            }
            linux {
                // deb 包名必须小写；银河麒麟 V10（Debian 系）与中标麒麟（RHEL 系）分别用 deb / rpm
                packageName = "songyuanqing-tv"
                debMaintainer = "Chen Guangming <noreply@example.com>"
                appCategory = "AudioVideo"
                menuGroup = "AudioVideo"
            }
        }
    }
}
