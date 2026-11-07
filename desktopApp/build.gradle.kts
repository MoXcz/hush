// SPDX-License-Identifier: GPL-3.0-or-later

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":sync-lan"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    // Dispatchers.Main for viewModelScope on desktop.
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.datastore.preferences.core)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "app.hush.desktop.MainKt"

        nativeDistributions {
            packageName = "Hush"
            packageVersion = libs.versions.app.versionName.get()
            description = "Minimal focus timer"
            licenseFile.set(rootProject.file("LICENSE"))
            // An app image (launcher + bundled runtime) runs on any distribution, Arch included.
            targetFormats(TargetFormat.AppImage)
            // jdk.crypto.ec: EC keys and TLS for sync (`:desktopApp:run` has it, the jlink'ed image doesn't).
            modules("java.instrument", "jdk.unsupported", "jdk.crypto.ec")
            linux {
                iconFile.set(project.file("src/main/resources/hush.png"))
            }
        }

        buildTypes.release.proguard {
            isEnabled = false
        }
    }
}
