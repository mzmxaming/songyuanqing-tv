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

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "SongyuanqingTV"
            packageVersion = "1.0.0"
            description = "松源情·TV 桌面版 - 多源聚合影视点播"
            vendor = "Chen Guangming"
            copyright = "APP 设计开发：陈光明"
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
        }
    }
}
