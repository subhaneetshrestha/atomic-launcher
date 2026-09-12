// Pure Kotlin/JVM: settings + theme schema, validation, migrations, built-in themes.
// Must never depend on android.* so it stays unit-testable on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // The rules an image address must pass are the fetcher's, and are applied when a theme is read.
    implementation(project(":core:collections"))

    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
