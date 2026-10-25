plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ua.any2b.tvplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "ua.any2b.tvplayer"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (System.getenv("ANDROID_KEYSTORE_PATH") != null &&
                !System.getenv("ANDROID_KEYSTORE_PASSWORD").isNullOrBlank() &&
                !System.getenv("ANDROID_KEY_ALIAS").isNullOrBlank() &&
                !System.getenv("ANDROID_KEY_PASSWORD").isNullOrBlank()) {
                storeFile = file(System.getenv("ANDROID_KEYSTORE_PATH"))
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("ANDROID_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling.debug)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.coroutines.android)
    implementation(libs.androidx.core)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.coil.compose)
}
