package app.thumbpad

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScrollWheel(enabled: Boolean, onScroll: (Float) -> Unit, modifier: Modifier = Modifier) {
    val gesture = remember { ScrollWheelGesture() }
    val density = LocalDensity.current.density
    val scroll by rememberUpdatedState(onScroll)
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    var turning by remember { mutableStateOf(false) }
    var rotation by remember { mutableFloatStateOf(0f) }
    val colors = MaterialTheme.colorScheme
    val ink = colors.primary.copy(alpha = if (enabled) 1f else 0.35f)

    DisposableEffect(enabled) {
        gesture.end()
        turning = false
        onDispose { gesture.end() }
    }

    Box(
        modifier.onSizeChanged { dimensions = it }
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .border(1.dp, ink.copy(alpha = if (turning) 0.7f else 0.25f), CircleShape)
            .semantics(mergeDescendants = true) {
                contentDescription = "Scroll wheel"
                stateDescription = "Turn clockwise to scroll down, counterclockwise to scroll up. Respects natural scrolling."
                if (!enabled) disabled()
                else customActions = listOf(
                    CustomAccessibilityAction("Scroll up") { scroll(-48f); true },
                    CustomAccessibilityAction("Scroll down") { scroll(48f); true }
                )
            }
            .pointerInteropFilter { event ->
                if (!enabled) return@pointerInteropFilter true
                val radius = minOf(dimensions.width, dimensions.height) / (2f * density)
                val x = (event.x - dimensions.width / 2f) / density
                val y = (event.y - dimensions.height / 2f) / density
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        gesture.down(x, y, radius)
                        turning = true
                    }
                    MotionEvent.ACTION_MOVE -> if (turning && event.pointerCount == 1) {
                        val delta = gesture.move(x, y, radius)
                        if (delta != 0f) {
                            rotation = (rotation + delta / (radius * 0.72f) * 180f / PI.toFloat()) % 360f
                            scroll(delta)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        gesture.end()
                        turning = false
                    }
                }
                // This control only scrolls. A tap or centre crossing never moves/clicks the pointer.
                true
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            for (tick in 0 until 32) {
                val angle = (tick * 360f / 32 + rotation) * PI.toFloat() / 180f
                val direction = Offset(cos(angle), sin(angle))
                drawLine(
                    ink.copy(alpha = if (tick % 4 == 0) 0.65f else 0.28f),
                    center + direction * (radius * 0.69f),
                    center + direction * (radius * 0.82f),
                    2.dp.toPx(), StrokeCap.Round
                )
            }
            val inset = radius * 0.44f
            drawArc(
                ink.copy(alpha = if (turning) 0.8f else 0.35f), rotation - 110f, 40f, false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text("↶  ↷", color = ink, fontSize = 18.sp)
    }
}
