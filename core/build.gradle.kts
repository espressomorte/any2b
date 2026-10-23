plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)
    implementation(libs.json)
}

kotlin {
    jvmToolchain(21)
}
