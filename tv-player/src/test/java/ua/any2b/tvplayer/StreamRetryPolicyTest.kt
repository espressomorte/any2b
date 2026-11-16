package ua.any2b.tvplayer

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRetryPolicyTest {
    @Test
    fun retryDelayBoundsGrowExponentiallyWithJitter() {
        assertEquals(500L..1_500L, StreamRetryPolicy.delayBoundsMs(0))
        assertEquals(1_000L..3_000L, StreamRetryPolicy.delayBoundsMs(1))
        assertEquals(2_000L..6_000L, StreamRetryPolicy.delayBoundsMs(2))
        assertEquals(4_000L..12_000L, StreamRetryPolicy.delayBoundsMs(3))
    }

    @Test
    fun retryLimitRejectsFurtherAttempts() {
        assertNull(StreamRetryPolicy.delayBoundsMs(-1))
        assertNull(StreamRetryPolicy.delayBoundsMs(StreamRetryPolicy.MAX_RETRIES))
        assertNull(StreamRetryPolicy.nextDelayMs(StreamRetryPolicy.MAX_RETRIES))
    }

    @Test
    fun generatedDelayAlwaysFallsWithinJitterBounds() {
        repeat(100) { seed ->
            val random = Random(seed)
            for (retryCount in 0 until StreamRetryPolicy.MAX_RETRIES) {
                val bounds = StreamRetryPolicy.delayBoundsMs(retryCount)!!
                val delay = StreamRetryPolicy.nextDelayMs(retryCount, random)!!
                assertTrue("$delay should be in $bounds", delay in bounds)
            }
        }
    }
}
