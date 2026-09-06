import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.android.application)
}

// Release signing is attached only when a keystore is supplied through the environment
// (CI secrets or a maintainer's shell). Local and F-Droid builds produce unsigned APKs.
val releaseKeystore = providers.environmentVariable("ATOMIC_KEYSTORE")

android {
    namespace = "io.github.subhaneetshrestha.atomic"
    compileSdk = 37
    // AGP 9.4 defaults to build-tools 36.0.0; the AUR ships 37.0.0. Any version at or above the
    // default works, and pinning it avoids an SDK download during the build.
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.subhaneetshrestha.atomic"
        minSdk = 26
        targetSdk = 36
        // Literal on purpose: reproducible builds must not depend on git or the clock.
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore.isPresent) {
            create("release") {
                storeFile = file(releaseKeystore.get())
                storePassword = providers.environmentVariable("ATOMIC_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ATOMIC_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ATOMIC_KEY_PASSWORD").orNull
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug home coexist with the release one on the same device.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = false
        viewBinding = false
        resValues = false
    }

    androidResources {
        // Drops the ~80 locales of androidx strings we do not use. Extend when translations land.
        localeFilters += listOf("en")
    }

    dependenciesInfo {
        // The Google-encrypted dependency block is not reproducible and is rejected by F-Droid.
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":core:theme"))
    implementation(project(":core:search"))

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// ---------------------------------------------------------------------------------------------
// Verification gates (also run in CI): release APK size budget and a GMS-free classpath.
// ---------------------------------------------------------------------------------------------

/** Fails the build when the release APK exceeds the size budget; writes build/reports/apk-size.txt. */
abstract class CheckApkSizeTask : DefaultTask() {
    @get:InputFiles
    abstract val apkDirectory: DirectoryProperty

    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    @get:Input
    abstract val maxBytes: Property<Long>

    @get:Input
    abstract val warnBytes: Property<Long>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val artifacts =
            builtArtifactsLoader.get().load(apkDirectory.get())
                ?: throw GradleException("No built APKs found in ${apkDirectory.get().asFile}")
        val sizes = artifacts.elements.map { File(it.outputFile) }.associateWith { it.length() }
        val lines = sizes.map { (file, size) -> "%s\t%d bytes\t%.3f MiB".format(file.name, size, size / 1_048_576.0) }
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(lines.joinToString("\n") + "\n")
        }
        lines.forEach { logger.lifecycle("APK size: $it") }
        val largest = sizes.values.maxOrNull() ?: 0L
        if (largest > maxBytes.get()) {
            throw GradleException("Release APK is $largest bytes, over the ${maxBytes.get()}-byte budget (2.5 MiB)")
        }
        if (largest > warnBytes.get()) {
            logger.warn("Release APK is $largest bytes, within 200 KiB of the ${maxBytes.get()}-byte budget")
        }
    }
}

/** Fails the build if any Google Play services, Firebase or Play library reaches the release classpath. */
abstract class GmsGuardTask : DefaultTask() {
    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @get:Input
    abstract val forbiddenPrefixes: ListProperty<String>

    @TaskAction
    fun check() {
        val seen = LinkedHashSet<String>()

        fun walk(component: ResolvedComponentResult) {
            if (!seen.add(component.id.displayName)) return
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { walk(it.selected) }
        }
        walk(rootComponent.get())
        val offenders = seen.filter { id -> forbiddenPrefixes.get().any { id.startsWith(it) } }
        if (offenders.isNotEmpty()) {
            throw GradleException("GMS-free build violated by: ${offenders.joinToString()}")
        }
        logger.lifecycle(
            "gmsGuard: ${seen.size} components on the release runtime classpath, none from GMS/Firebase/Play",
        )
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        tasks.register<CheckApkSizeTask>("check${suffix}ApkSize") {
            group = "verification"
            description = "Fails if the $suffix APK exceeds the 2.5 MiB budget."
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
            maxBytes.set(2_621_440L)
            warnBytes.set(2_411_724L)
            report.set(layout.buildDirectory.file("reports/apk-size.txt"))
        }
        tasks.register<GmsGuardTask>("gmsGuard") {
            group = "verification"
            description = "Fails if Google Play services, Firebase or Play libraries are on the release classpath."
            rootComponent.set(variant.runtimeConfiguration.incoming.resolutionResult.rootComponent)
            forbiddenPrefixes.set(listOf("com.google.android.gms", "com.google.firebase", "com.google.android.play"))
        }
    }
}
