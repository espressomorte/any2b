plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("ua.any2b.cli.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
}

kotlin {
    jvmToolchain(21)
}
