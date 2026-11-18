package ua.any2b.core.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApiRequestGateTest {
    @Test
    fun concurrentCallersArePacedAcrossTheSameGate() = runBlocking {
        val gate = ApiRequestGate(maxConcurrent = 3, intervalMs = 30L)
        val starts = (1..3).map { async {
            gate.run({ wait -> IllegalStateException(wait.toString()) }) {
                System.nanoTime() / 1_000_000L
            }
        } }.awaitAll().sorted()
        assertTrue(starts[1] - starts[0] >= 20L)
        assertTrue(starts[2] - starts[1] >= 20L)
    }

    @Test
    fun cooldownRejectsNewRequestsWithoutCallingTheApi() = runBlocking {
        val gate = ApiRequestGate(maxConcurrent = 1, intervalMs = 0L)
        gate.coolDown(2L)
        var called = false
        val failure = assertFailsWith<IllegalStateException> {
            gate.run({ wait -> IllegalStateException(wait.toString()) }) { called = true }
        }
        assertTrue(failure.message?.toLongOrNull() ?: 0L > 0L)
        assertEquals(false, called)
    }
}
