package app.thumbpad

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Converts circular thumb travel to logical scroll pixels, without momentum or entry jumps. */
class ScrollWheelGesture {
    private var active = false
    private var previousAngle: Double? = null

    fun down(x: Float, y: Float, radius: Float) {
        active = true
        previousAngle = angle(x, y, radius)
    }

    fun move(x: Float, y: Float, radius: Float): Float {
        if (!active) return 0f
        val current = angle(x, y, radius)
        val previous = previousAngle
        previousAngle = current
        if (current == null || previous == null) return 0f

        var turn = current - previous
        if (turn > PI) turn -= 2 * PI
        if (turn < -PI) turn += 2 * PI
        // A large discontinuity is a new anchor, not a sudden page jump.
        if (abs(turn) > PI / 2) return 0f
        return (turn * radius * 0.72).toFloat()
    }

    fun end() {
        active = false
        previousAngle = null
    }

    private fun angle(x: Float, y: Float, radius: Float): Double? {
        if (!x.isFinite() || !y.isFinite() || !radius.isFinite() || radius <= 0f) return null
        val distance = hypot(x, y)
        // Crossing the centre or leaving the control breaks the angular anchor.
        if (distance < radius * 0.35f || distance > radius * 1.25f) return null
        return atan2(y.toDouble(), x.toDouble())
    }
}
