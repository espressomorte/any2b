import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val config = rootProject.file("local.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
val malClientId = (providers.environmentVariable("MAL_CLIENT_ID").orNull
    ?: localProperties.getProperty("mal.clientid").orEmpty())
    .takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) }.orEmpty()
val encryptedConfigUrl = (providers.environmentVariable("CURL_GIST_URL").orNull
    ?: localProperties.getProperty("curl.gist").orEmpty())
    .takeIf { value ->
        runCatching { URI(value) }.getOrNull()?.let { uri ->
            uri.scheme == "https" && uri.host == "gist.githubusercontent.com" &&
                uri.userInfo == null && uri.fragment == null && uri.query == null
        } == true
    }.orEmpty()

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
        buildConfigField("String", "MAL_CLIENT_ID", "\"$malClientId\"")
        buildConfigField("String", "ENCRYPTED_CONFIG_URL", "\"$encryptedConfigUrl\"")
    }

        val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
        ?.takeIf { it.isNotBlank() }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
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
