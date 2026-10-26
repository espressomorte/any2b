plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)
    implementation(libs.json)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}
