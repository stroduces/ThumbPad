package app.thumbpad

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.pow

data class Packet(val kind: Int, val sequence: Long, val micros: Long, val a: Float = 0f, val b: Float = 0f) {
    fun encode(): ByteArray = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        .put('T'.code.toByte()).put('P'.code.toByte()).put(1).put(kind.toByte())
        .putLong(sequence).putLong(micros).putFloat(a).putFloat(b).putInt(0).array()
}
/** Pure, sampling-rate-independent tap decision, in density-independent pixels. */
class GestureClassifier {
    private var start = 0L
    private var travel = 0f
    private var fingers = 1
    fun down(timeMs: Long) { start = timeMs; travel = 0f; fingers = 1 }
    fun move(dxDp: Float, dyDp: Float, count: Int = 1) { travel += hypot(dxDp, dyDp); fingers = maxOf(fingers, count) }
    fun tap(timeMs: Long): Int? = if (timeMs - start in 0..220 && travel <= 8f) { if (fingers > 1) 1 else 0 } else null
}
fun pointerDelta(dx: Float, dy: Float, dtMs: Float, sensitivity: Float, acceleration: Float, precise: Boolean): Pair<Float,Float> {
    val speed = hypot(dx,dy) / dtMs.coerceIn(1f,50f)
    val gain = if (precise) 0.35f else sensitivity * (1f + acceleration * speed.coerceAtMost(5f).pow(0.7f))
    return (dx*gain).coerceIn(-2048f,2048f) to (dy*gain).coerceIn(-2048f,2048f)
}
fun scrollDelta(delta: Float, speed: Float, natural: Boolean): Float = (delta * speed * if(natural) 1f else -1f).coerceIn(-2048f,2048f)

/** One replaceable motion slot + bounded reliable FIFO. Actions flush pending movement first. */
class EventQueue(private val capacity: Int = 64) {
    data class Event(val kind:Int,val a:Float,val b:Float,val created:Long)
    private val reliable = ArrayDeque<Event>()
    private var motion: Event? = null
    @Synchronized fun offer(e:Event):Boolean {
        if(e.kind==1) { motion = e; return true }
        if(reliable.size + (if(motion!=null) 1 else 0) >= capacity-1) return false
        motion?.let { reliable.addLast(it); motion=null }; reliable.addLast(e);return true
    }
    @Synchronized fun poll(now:Long):Event? {
        while(reliable.isNotEmpty()) { val e=reliable.removeFirst();if(e.kind!=1||now-e.created<=50_000) return e }
        val e=motion;motion=null;return e?.takeIf { now-it.created<=50_000 }
    }
    @Synchronized fun clear(){reliable.clear();motion=null}
}
