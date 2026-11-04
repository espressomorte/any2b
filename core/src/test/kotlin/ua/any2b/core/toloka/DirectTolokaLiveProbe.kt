package ua.any2b.core.toloka

import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking

/** Manual probe only. Reads ignored local.properties and prints no credentials or release URLs. */
fun main() = runBlocking {
    val local = listOf(File("local.properties"), File("../local.properties"))
        .firstOrNull(File::exists) ?: error("local.properties not found")
    val properties = Properties().apply { local.inputStream().use(::load) }
    val username = properties.getProperty("toloka.username")?.takeIf(String::isNotBlank)
        ?: error("toloka.username missing")
    val password = properties.getProperty("toloka.password")?.takeIf(String::isNotBlank)
        ?: error("toloka.password missing")
    val client = DirectTolokaClient()
    client.check(username, password)
    println("Toloka login: confirmed")
    val result = client.search(username, password, "The Matrix")
    println("Toloka search: ${result.results.size} parsed releases; session reused: ${result.sessionReused}")
}
