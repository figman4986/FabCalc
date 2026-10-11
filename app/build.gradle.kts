import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// --- Release signing ---------------------------------------------------------------------------
// Signing secrets are read from local.properties (gitignored) or the environment — never
// hard-coded or committed. Release tasks fail during configuration without them; debug builds and
// secret-less CI remain available.
val signingProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingSecret(key: String): String? =
    (signingProperties.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingSecret("RELEASE_KEYSTORE_PATH")
val releaseStorePassword = signingSecret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingSecret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingSecret("RELEASE_KEY_PASSWORD")
// A PKCS12 keystore reuses the store password for the key, so RELEASE_KEY_PASSWORD is optional.
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
).all { it != null }

if (!hasReleaseSigning && gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
    throw GradleException(
        "A release task requires signing configuration. Set RELEASE_KEYSTORE_PATH, " +
            "RELEASE_STORE_PASSWORD and RELEASE_KEY_ALIAS (and optionally RELEASE_KEY_PASSWORD) " +
            "in local.properties or the environment."
    )
}

android {
    namespace = "com.figapps.fractioncalculatorv1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.figapps.fractioncalculatorv1"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Created only when the secrets are present, so the config never carries placeholder keys.
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword ?: releaseStorePassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")

}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
