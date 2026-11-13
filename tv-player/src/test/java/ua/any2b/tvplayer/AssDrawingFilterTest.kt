package ua.any2b.tvplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class AssDrawingFilterTest {
    @Test fun dropsVectorOnlyDialogueButKeepsSpeech() {
        val sample = """Dialogue: 0:00:00:00,0:00:02:00,0,0,Default,,0,0,0,,{\p1}m 1144 70 l 1222 41
Dialogue: 0:00:00:00,0:00:02:00,0,0,Default,,0,0,0,,Hello"""

        assertEquals(sample.lineSequence().last(), AssDrawingFilter.removeDrawings(sample))
    }

    @Test fun preservesTextAfterDrawingModeEnds() {
        val sample = """Dialogue: 0:00:00:00,0:00:02:00,0,0,Default,,0,0,0,,{\p1}m 0 0 l 10 10{\p0}Hello"""

        assertEquals(
            "Dialogue: 0:00:00:00,0:00:02:00,0,0,Default,,0,0,0,,Hello",
            AssDrawingFilter.removeDrawings(sample),
        )
    }
}
