plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)
    implementation(libs.json)
    implementation(libs.jsoup)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.register<JavaExec>("tolokaDirectProbe") {
    group = "verification"
    description = "Opt-in live Toloka login and search using ignored local.properties"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ua.any2b.core.toloka.DirectTolokaLiveProbeKt")
    dependsOn(tasks.testClasses)
}
