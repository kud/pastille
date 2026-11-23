// The plugins below run with libraries that have known vulnerabilities at the versions AGP asks
// for. These constraints raise them to a patched release; they never lower a version and add no
// dependency (gradle/libs.versions.toml, security-floors).
buildscript {
    dependencies {
        constraints {
            libs.bundles.security.floors.get().forEach { classpath(it) }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
