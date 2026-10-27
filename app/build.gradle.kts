import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.google.gms.google-services")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Verify the checked-in index against its source assets without requiring Python at build time.
val verifyAtlasCatalog = tasks.register("verifyAtlasCatalog") {
    val directory = layout.projectDirectory.dir("src/main/assets/atlas")
    val names = listOf("countries.geojson", "cities.json", "country-labels.json", "country-flags.json", "catalog.sqlite", "manifest.json")
    val checksums = directory.file("catalog.sha256")
    inputs.files(names.map { directory.file(it) } + checksums)
    doLast {
        val rows = checksums.asFile.readLines().map { it.split("  ", limit = 2) }
        check(rows.map { it[1] } == names) { "Invalid atlas index fingerprints" }
        rows.forEach { (expected, name) ->
            val digest = MessageDigest.getInstance("SHA-256")
            directory.file(name).asFile.inputStream().buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expected) {
                "Stale atlas index ($name). Run python3 scripts/compile_atlas_catalog.py"
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyAtlasCatalog) }
val verifyTimeZoneCatalog = tasks.register("verifyTimeZoneCatalog") {
    val directory = layout.projectDirectory.dir("src/main/assets/timezones")
    val names = listOf("boundaries.sqlite", "manifest.json")
    inputs.files(names.map { directory.file(it) } + directory.file("catalog.sha256"))
    doLast {
        val rows = directory.file("catalog.sha256").asFile.readLines().map { it.split("  ", limit = 2) }
        check(rows.map { it[1] } == names) { "Invalid timezone index fingerprints" }
        rows.forEach { (expected, name) ->
            val digest = MessageDigest.getInstance("SHA-256")
            directory.file(name).asFile.inputStream().buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Stale timezone index ($name). Rebuild with scripts/prepare_timezone_catalog.py" }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyTimeZoneCatalog) }
tasks.withType<Test>().configureEach {
    inputs.file(layout.projectDirectory.file("src/main/assets/atlas/catalog.sqlite"))
}

// The default profile is private machine-local data, never a source/build artifact.
val releaseSigningProfile = providers.environmentVariable("FLYTLY_RELEASE_SIGNING_PROPERTIES")
    .map { file(it) }.getOrElse(file("${System.getProperty("user.home")}/.android/flytly-release/signing.properties"))
val releaseSigning = Properties().apply {
    if (releaseSigningProfile.isFile) releaseSigningProfile.inputStream().use(::load)
}
val releaseStore = releaseSigning.getProperty("storeFile")?.let(::file)
val releaseSigningReady = releaseStore?.isFile == true &&
    listOf("storePassword","keyAlias","keyPassword").all { !releaseSigning.getProperty(it).isNullOrBlank() } &&
    releaseSigning.getProperty("keyAlias") != "androiddebugkey"
val requireReleaseSigning = tasks.register("requireReleaseSigning") {
    val signingReady = releaseSigningReady
    doLast {
        check(signingReady) {
            "Release signing is missing. Configure the private Flytly upload-key profile; debug signing and unsigned release fallback are disabled."
        }
    }
}
tasks.configureEach {
    if (name in listOf("assembleRelease","bundleRelease","validateSigningRelease","packageRelease","packageReleaseBundle","signReleaseBundle")) dependsOn(requireReleaseSigning)
}

android {
    namespace = "com.mountzou.flytly"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mountzou.flytly"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("flytlyRelease") {
            if (releaseSigningReady) {
                storeFile = releaseStore
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
                storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("flytlyRelease")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += listOf("sqlite", "geojson")
    }
}

dependencies {
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.maplibre)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidsvg)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
