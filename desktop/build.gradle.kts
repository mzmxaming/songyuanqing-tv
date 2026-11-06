import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
        val hostOs = System.getProperty("os.name").lowercase()
        val formats = when {
            hostOs.contains("win") -> listOf(TargetFormat.Msi, TargetFormat.Exe)
            hostOs.contains("mac") -> listOf(TargetFormat.Dmg)
            // AppImage 在当前 Compose 版本下是空转任务（不产出文件），Linux 用 deb / rpm 即可覆盖
            // 银河麒麟 V10（Debian 系）与中标麒麟（RHEL 系），再附一个便携 tar.gz。
            else -> listOf(TargetFormat.Deb, TargetFormat.Rpm)
        }

        nativeDistributions {
            targetFormats(*formats.toTypedArray())
            packageName = "SongyuanqingTV"
            packageVersion = "1.2.0"
            // WiX 的 light.exe 在非中文代码页（如 CI 的 en-US）下无法处理中文元数据，会以退出码 311 失败，
            // 因此安装包元信息保持 ASCII；界面内的中文不受影响。
            description = "Songyuanqing TV Desktop - multi-source movie streaming"
            vendor = "Chen Guangming"
            copyright = "(c) Chen Guangming"
            modules("java.sql", "java.naming")

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
