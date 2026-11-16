// AGP 9 bundles KGP 2.2.10; raise it so the compiler can read Kotlin 2.4
// stdlib and kotlinx-serialization 1.13 metadata.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}