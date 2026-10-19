import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing credentials live outside version control (keystore/ is
// gitignored); the release build only works on machines that have them.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.onurguner.ogauto"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.onurguner.ogauto"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.8.2-v2-mirror"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        } else if (System.getenv("OGAUTO_KEYSTORE_FILE") != null) {
            // CI: the workflow decodes the OGAUTO_KEYSTORE_B64 secret here.
            create("release") {
                storeFile = file(System.getenv("OGAUTO_KEYSTORE_FILE"))
                storePassword = System.getenv("OGAUTO_STORE_PASSWORD")
                keyAlias = System.getenv("OGAUTO_KEY_ALIAS")
                keyPassword = System.getenv("OGAUTO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".v2.mirror"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    testImplementation(libs.junit)
}
