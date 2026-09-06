// Root build script: declares plugin versions once; each module applies what it needs.
// Keeping the Kotlin Gradle plugin on this classpath also fixes the Kotlin version that
// AGP's built-in Kotlin support uses for :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
}

// ktlint on every module: `./gradlew ktlintCheck` in CI and the pre-commit hook,
// `./gradlew ktlintFormat` locally. Style comes from .editorconfig (ktlint_official).
// The catalog accessor only exists on the root project, so read the version here.
val ktlintVersion = libs.versions.ktlint.get()

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintVersion)
        android.set(false)
        outputToConsole.set(true)
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }
}
