// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Align AGP's built-in Kotlin compiler with Firebase Auth's Kotlin metadata.
buildscript {
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") }
}
plugins {
    id("com.google.gms.google-services") version "4.5.0" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
