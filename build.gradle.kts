// Factory-owned file: agents must not edit it. Dependencies are added by the factory
// (java scripts/AddDependency.java), every plugin and library is pinned to an exact version, and
// gradle.lockfile locks every resolved configuration (strict: a configuration without a lock fails).
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    id("com.diffplug.spotless") version "8.11.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jetbrains.kotlinx.kover") version "0.9.11"
}

group = "io.github.trysac"
version = "0.1.0"
description = "Kanban board core with keyboard-only drag and drop and screen reader announcements, WIP limits, undo and redo, and crash-safe persistence in a versioned line format"

// Build output; the gates image moves it out of the mounted repository (FABRICA_BUILD_DIR).
providers.environmentVariable("FABRICA_BUILD_DIR").orNull?.takeIf { it.isNotEmpty() }?.let {
    layout.buildDirectory.set(file(it).resolve("build"))
}

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Lint: every compiler warning is an error.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("net.jqwik:jqwik:1.10.2")
}

// CI-only conformance tier: tests tagged @Tag("conformance") run only when FABRICA_CI=1 (the CI
// conformance job); the lint gate always compiles them.
val ciTier = providers.environmentVariable("FABRICA_CI").orNull == "1"

tasks.test {
    useJUnitPlatform {
        if (!ciTier) {
            excludeTags("conformance")
        }
    }
    // jqwik keeps failing samples in a database (kept with the build output) and reports only
    // failing properties.
    systemProperty(
        "jqwik.database",
        layout.buildDirectory
            .file("jqwik-database")
            .get()
            .asFile.path,
    )
    systemProperty("jqwik.reporting.onlyfailures", "true")
    // Each failing test with its assertion message, also under --quiet (the gates run Gradle quiet).
    testLogging {
        quiet {
            events(TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.FULL
        }
        events(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// Format: ktlint (Spotless) over the library, its tests and the build scripts; google-java-format
// over the factory's scripts/.
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.8.0")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
    }
    java {
        target("scripts/*.java")
        googleJavaFormat("1.24.0")
    }
}

// Lint and KDoc: detekt with the factory's rules (detekt.yml); any finding fails.
detekt {
    buildUponDefaultConfig = true
    config.setFrom("detekt.yml")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    // Findings go to the console; no report files (their default folder ignores FABRICA_BUILD_DIR).
    reports {
        html.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

// detekt 1.23 runs on the Kotlin compiler it was built with (2.0.21); keep its own classpath there
// while the library compiles with the Kotlin above.
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21")
        }
    }
}

// Coverage: Kover writes build/reports/kover/report.xml; scripts/Check.java enforces the threshold.
kover {
    reports {
        total {
            xml {
                onCheck.set(false)
            }
        }
    }
}
