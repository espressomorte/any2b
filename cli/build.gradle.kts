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

tasks.register<JavaExec>("fuzzyAnitube") {
    group = "application"
    description = "Fuzzy match every unresolved root mapping with local Ollama"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args(
        "fuzzy-anitube",
        rootProject.file("anilistTmbdbAnitube.json").absolutePath,
        rootProject.file("anilistTmbdbAnitube-fuzzy.json").absolutePath,
        "not_found,needs_review",
        "0",
    )
    environment("LC_ALL", "en_US.UTF-8")
    environment("FUZZY_LLM_PROVIDER", "ollama")
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
