package com.blendervcam.controller.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/** Virtual stick. [onChange] receives x (right = +1) and y (up = +1), both in -1..1, and (0,0) on release. */
@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    onChange: (Float, Float) -> Unit,
) {
    var thumb by remember { mutableStateOf(Offset.Zero) }
    val callback by rememberUpdatedState(onChange)

    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
                val radius = this.size.width / 2f
                val center = Offset(radius, radius)

                fun update(pos: Offset) {
                    var d = (pos - center) / radius
                    val len = sqrt(d.x * d.x + d.y * d.y)
                    if (len > 1f) d = Offset(d.x / len, d.y / len)
                    thumb = d
                    callback(d.x, -d.y)
                }

                fun release() {
                    thumb = Offset.Zero
                    callback(0f, 0f)
                }

                detectDragGestures(
                    onDragStart = { update(it) },
                    onDragEnd = { release() },
                    onDragCancel = { release() },
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position)
                    },
                )
            },
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.width / 2f
            val c = Offset(r, r)
            drawCircle(Color(0x33FFFFFF), radius = r)
            drawCircle(Color(0x88FFFFFF), radius = r - 1.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
            drawLine(Color(0x22FFFFFF), Offset(c.x, 12.dp.toPx()), Offset(c.x, this.size.height - 12.dp.toPx()), 1.dp.toPx())
            drawLine(Color(0x22FFFFFF), Offset(12.dp.toPx(), c.y), Offset(this.size.width - 12.dp.toPx(), c.y), 1.dp.toPx())
            val knob = r * 0.36f
            drawCircle(BlenderOrange.copy(alpha = 0.9f), radius = knob, center = Offset(c.x + thumb.x * (r - knob), c.y + thumb.y * (r - knob)))
        }
    }
}
