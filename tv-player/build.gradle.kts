plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ua.any2b.tvplayer"
    compileSdk = 35

    val githubTag = providers.environmentVariable("GITHUB_REF_TYPE").orNull
        .takeIf { it == "tag" }
        ?.let { providers.environmentVariable("GITHUB_REF_NAME").orNull }

    defaultConfig {
        applicationId = "ua.any2b.tvplayer"
        minSdk = 21          // Android 5 — покриває всі Android TV
        targetSdk = 35
        versionCode = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull()
            ?: githubTag?.let { providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() }
            ?: 1
        versionName = providers.gradleProperty("appVersionName").orNull
            ?: githubTag?.removePrefix("v")
            ?: "0.1.0-alpha.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // Чистий Kotlin Core (AniList, AniTube, Fenix, Matcher)
    implementation(project(":core"))

    // Каталог: Compose for TV
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling.debug)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Плеєр (ExoPlayer / Media3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    // AndroidX & утиліти
    implementation(libs.coroutines.android)
    implementation(libs.androidx.core)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.constraintlayout)

    // Зображення
    implementation(libs.coil.compose)
}
