package app.thumbpad

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ScrollWheelGestureTest {
    private val radius = 72f
    private fun point(degrees: Double): Pair<Float, Float> {
        val angle = degrees * PI / 180
        return (cos(angle) * radius * 0.72).toFloat() to (sin(angle) * radius * 0.72).toFloat()
    }
    private fun start(g: ScrollWheelGesture, degrees: Double) {
        val (x,y) = point(degrees)
        g.down(x,y,radius)
    }
    private fun turn(g: ScrollWheelGesture, degrees: Double): Float {
        val (x,y) = point(degrees)
        return g.move(x,y,radius)
    }

    @Test fun clockwiseAndCounterclockwiseHaveOppositeSigns() {
        val g = ScrollWheelGesture()
        start(g,0.0)
        val clockwise = turn(g,30.0)
        start(g,0.0)
        assertEquals(-clockwise,turn(g,-30.0),0.001f)
        assertEquals((PI / 6 * radius * 0.72).toFloat(),clockwise,0.001f)
    }

    @Test fun angleSeamDoesNotJump() {
        val g = ScrollWheelGesture()
        start(g,170.0)
        val clockwise = turn(g,-170.0)
        assertEquals((PI / 9 * radius * 0.72).toFloat(),clockwise,0.001f)
        assertEquals(-clockwise,turn(g,170.0),0.001f)
    }

    @Test fun travelIsIndependentOfSampleCount() {
        for (samples in listOf(6,9,12,24)) {
            val g = ScrollWheelGesture()
            start(g,0.0)
            var sum = 0f
            for (sample in 1..samples) sum += turn(g,sample * 90.0 / samples)
            assertEquals((PI / 2 * radius * 0.72).toFloat(),sum,0.001f)
        }
    }

    @Test fun centreCrossingAndReentryReanchorWithoutScroll() {
        val g = ScrollWheelGesture()
        start(g,0.0)
        assertEquals(0f,g.move(0f,0f,radius))
        assertEquals(0f,turn(g,90.0))
        assertTrue(turn(g,100.0) > 0f)
        assertEquals(0f,g.move(radius * 2,0f,radius))
        assertEquals(0f,turn(g,-90.0))
        assertTrue(turn(g,-100.0) < 0f)
    }

    @Test fun largeDiscontinuityIsAnAnchorRatherThanAPageJump() {
        val g = ScrollWheelGesture()
        start(g,0.0)
        assertEquals(0f,turn(g,180.0))
        assertTrue(turn(g,190.0) > 0f)
    }

    @Test fun cancellationStopsScrollingAndNextTouchDoesNotJump() {
        val g = ScrollWheelGesture()
        start(g,0.0)
        g.end()
        assertEquals(0f,turn(g,45.0))
        start(g,-90.0)
        assertEquals(0f,turn(g,-90.0))
        assertTrue(turn(g,-80.0) > 0f)
    }

    @Test fun invalidCoordinatesAreRejectedAndExistingDirectionSettingApplies() {
        val g = ScrollWheelGesture()
        start(g,0.0)
        assertEquals(0f,g.move(Float.NaN,0f,radius))
        assertEquals(0f,g.move(1f,1f,Float.POSITIVE_INFINITY))
        assertEquals(0f,g.move(1f,1f,0f))
        start(g,0.0)
        val delta = turn(g,30.0)
        assertEquals(-scrollDelta(delta,2f,true),scrollDelta(delta,2f,false),0.001f)
    }
}
