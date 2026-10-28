plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("ua.any2b.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    environment("LC_ALL", "en_US.UTF-8")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.json)
}

kotlin {
    jvmToolchain(21)
}
