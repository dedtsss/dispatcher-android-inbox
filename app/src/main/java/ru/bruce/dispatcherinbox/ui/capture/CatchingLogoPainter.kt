package ru.bruce.dispatcherinbox.ui.capture

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.Painter

// Маски относятся к утверждённому Logo.png 890×210; сам ресурс остаётся неизменным.
// Между котом (x≤237) и буквами (x≥261) — прозрачный зазор; точка i — отдельный акцент.
internal class CatchingLogoPainter(private val original: Painter, textColor: Color) : Painter() {
    private val letters = ColorFilter.tint(textColor)
    override val intrinsicSize: Size get() = original.intrinsicSize

    override fun DrawScope.onDraw() {
        val split = size.width * 250f / 890f
        val dotLeft = size.width * 680f / 890f
        val dotTop = size.height * 20f / 210f
        val dotRight = size.width * 720f / 890f
        val dotBottom = size.height * 62f / 210f
        clipRect(right = split) { with(original) { draw(size) } }
        clipRect(left = split) {
            clipRect(dotLeft, dotTop, dotRight, dotBottom, clipOp = ClipOp.Difference) {
                with(original) { draw(size, colorFilter = letters) }
            }
        }
        clipRect(dotLeft, dotTop, dotRight, dotBottom) { with(original) { draw(size) } }
    }
}

// Голова из того же оригинала: правая граница проходит по прозрачному зазору.
internal class CatchingCatPainter(private val original: Painter) : Painter() {
    override val intrinsicSize = Size(250f, 210f)
    override fun DrawScope.onDraw() {
        clipRect {
            with(original) { draw(Size(size.width * 890f / 250f, size.height)) }
        }
    }
}
