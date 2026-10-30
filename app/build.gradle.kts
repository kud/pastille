import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.pastille"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.beansontoast.pastille"
        minSdk = 28
        targetSdk = 35
        versionCode = 16
        versionName = "0.1.5"
        // A vX.Y.Z-dev.N pre-release names itself here and keeps the shipped versionCode (docs/RELEASING.md).
        System.getenv("PASTILLE_VERSION_NAME")?.let { versionName = it }
    }

    // CI writes the keystore from the PASTILLE_KEYSTORE_* secrets; without them the release build is left unsigned.
    val releaseKeystore = System.getenv("PASTILLE_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("PASTILLE_KEYSTORE_PASSWORD")
                keyAlias = "pastille"
                keyPassword = System.getenv("PASTILLE_KEYSTORE_PASSWORD")
            }
        }
    }

    // Two builds from one codebase (docs/adr/0001-in-app-updates.md). Same applicationId and key for
    // both, so a phone moves between them in place. `github` is what GitHub Releases carry: it adds
    // INTERNET and REQUEST_INSTALL_PACKAGES for the in-app updater, which talks only to GitHub.
    // `fdroid` is Pastille as it always was, with no network at all; checkNoNetwork* enforces it.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    buildTypes {
        // Installs beside the release build: a different signing key can never update it in place.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // Reproducible builds: F-Droid rebuilds from the tag, maybe from a checkout without .git, and
            // compares with the GitHub APK. The commit hash in META-INF would be one more thing to differ.
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    lint {
        // Lifecycle 2.8's detector crashes under Kotlin 2 UAST (IncompatibleClassChangeError); Pastille uses no LiveData.
        disable += "NullSafeMutableLiveData"
    }

    // F-Droid and reproducible builds reject the Google-encrypted dependency metadata block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
        // Settings › About reads the version from it.
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // CI shows only the log: print why a test failed, not just where.
        unitTests.all {
            // Design renders (app/src/test/.../redesign) run only with PASTILLE_RENDERS=<dir>, one JVM per class:
            // Robolectric's native canvas aborts the process (exit 134) when several renders share one.
            if (System.getenv("PASTILLE_RENDERS") != null) {
                it.forkEvery = 1
                it.maxHeapSize = "2g"
                it.outputs.upToDateWhen { false }
            }
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    sourceSets {
        // MigrationTestHelper reads the exported Room schemas as assets.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
}

// The fdroid build must never be able to reach the network, whatever a library's manifest adds:
// every variant's merged manifest is read after the build merges it, and an fdroid one asking for a
// network or install permission fails the build. The github variants must ask for INTERNET, which
// proves the check reads the right file (a check that cannot fail is not a check).
androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register<NoNetworkCheck>("checkNoNetwork$name") {
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            offline.set(variant.flavorName == "fdroid")
        }
        afterEvaluate {
            tasks.named("assemble$name") { dependsOn(check) }
            tasks.findByName("lint$name")?.dependsOn(check)
        }
    }
}

abstract class NoNetworkCheck : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val offline: Property<Boolean>

    @TaskAction
    fun check() {
        val text = manifest.get().asFile.readText()
        val asked = Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toSet()
        if (offline.get()) {
            val forbidden = asked.filter { it in NETWORK_PERMISSIONS }
            check(forbidden.isEmpty()) { "The fdroid build must not ask for $forbidden (${manifest.get().asFile})" }
        } else {
            check("android.permission.INTERNET" in asked) { "The github build lost INTERNET: the updater can't work (${manifest.get().asFile})" }
        }
    }

    private companion object {
        val NETWORK_PERMISSIONS = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.REQUEST_INSTALL_PACKAGES",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.exifinterface)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.savedstate)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.serialization.json)
    implementation(libs.reorderable)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
