// SPDX-License-Identifier: GPL-3.0-or-later

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    // Lets androidApp's lint (checkDependencies) check this module against its minSdk 26.
    alias(libs.plugins.android.lint)
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
    testImplementation(libs.kotlin.test.junit)
}

lint {
    // Only Android 4.3 and older seeded SecureRandom badly; the apps start at API 26.
    disable += "TrulyRandom"
}
