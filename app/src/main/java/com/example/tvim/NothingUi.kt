package com.example.tvim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.tvim.ui.theme.LocalNothingDesign
import com.example.tvim.ui.theme.NothingDisplayFont
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Uses one measured cell per character so square zero and colon dots stay centered. */
@Composable
fun TvimDigitalTime(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    color: Color = LocalContentColor.current,
    fontFamily: FontFamily = NothingDisplayFont,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    nothingDesign: Boolean = LocalNothingDesign.current
) {
    if (!nothingDesign || ':' !in text) {
        Text(text, modifier, color = color, fontSize = fontSize, fontFamily = fontFamily,
            fontWeight = fontWeight, letterSpacing = letterSpacing)
        return
    }

    val layoutSize = fontSize.value.dp
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digitCellWidth = with(density) {
        textMeasurer.measure(
            text = "0",
            style = TextStyle(fontSize = fontSize, fontFamily = fontFamily,
                fontWeight = fontWeight ?: FontWeight.Normal, letterSpacing = 0.sp),
            constraints = Constraints()
        ).size.width.toDp()
    }
    val cellSpacing = with(density) { if (letterSpacing != TextUnit.Unspecified) letterSpacing.toDp() else 0.dp }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(cellSpacing)
    ) {
        text.forEach { character ->
            Box(
                modifier = Modifier.width(digitCellWidth).height(layoutSize * 1.2f),
                contentAlignment = Alignment.Center
            ) {
                when (character) {
                    '0' -> SquareDotZero(Modifier.fillMaxWidth().height(layoutSize * 0.92f), color)
                    ':' -> Column(
                        verticalArrangement = Arrangement.spacedBy(layoutSize * 0.1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val dotSize = layoutSize * 0.12f
                        Box(Modifier.size(dotSize).background(color, androidx.compose.foundation.shape.RoundedCornerShape(1.dp)))
                        Box(Modifier.size(dotSize).background(color, androidx.compose.foundation.shape.RoundedCornerShape(1.dp)))
                    }
                    else -> Text(character.toString(), color = color, fontSize = fontSize,
                        fontFamily = fontFamily, fontWeight = fontWeight, letterSpacing = 0.sp)
                }
            }
        }
    }
}

@Composable
private fun SquareDotZero(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val columns = 5
        val rows = 7
        val stepX = size.width / (columns + 1f)
        val stepY = size.height / (rows + 1f)
        val radius = min(stepX, stepY) * 0.36f
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if (row == 0 || row == rows - 1 || column == 0 || column == columns - 1) {
                    drawCircle(color, radius, Offset(stepX * (column + 1), stepY * (row + 1)))
                }
            }
        }
    }
}

fun DrawScope.drawNothingDots(color: Color, startAngle: Float, sweepAngle: Float, strokeWidth: Dp) {
    val radius = min(size.width, size.height) / 2f - strokeWidth.toPx() / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val count = (kotlin.math.abs(sweepAngle) * PI.toFloat() / 180f * radius / (strokeWidth.toPx() * 1.25f)).toInt().coerceAtLeast(1)
    for (index in 0..count) {
        val angle = (startAngle + sweepAngle * index / count) * PI.toFloat() / 180f
        drawCircle(color, strokeWidth.toPx() * 0.42f,
            Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius))
    }
}

/** Uses the familiar Material symbol normally and a matching dot glyph in Nothing Design. */
@Composable
fun TvimIcon(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current
) {
    if (LocalNothingDesign.current) {
        DotGlyph(image.name.substringAfterLast('.'), contentDescription, modifier, tint)
    } else {
        Icon(imageVector = image, contentDescription = contentDescription, modifier = modifier, tint = tint)
    }
}

@Composable
private fun DotGlyph(name: String, description: String?, modifier: Modifier, tint: Color) {
    Canvas(modifier.semantics { if (description != null) contentDescription = description }) {
        val side = min(size.width, size.height)
        val unit = side / 100f
        val radius = (side / 24f).coerceAtLeast(1f)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        fun dot(x: Float, y: Float) = drawCircle(tint, radius, Offset(left + x * unit, top + y * unit))
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            val count = ceil(max(kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1)) / 10f).toInt().coerceAtLeast(1)
            for (index in 0..count) {
                val fraction = index.toFloat() / count
                dot(x1 + (x2 - x1) * fraction, y1 + (y2 - y1) * fraction)
            }
        }
        fun poly(vararg points: Pair<Float, Float>, close: Boolean = false) {
            points.toList().zipWithNext().forEach { (a, b) -> line(a.first, a.second, b.first, b.second) }
            if (close && points.size > 2) line(points.last().first, points.last().second, points.first().first, points.first().second)
        }
        fun circle(cx: Float, cy: Float, r: Float, start: Float = 0f, end: Float = 360f) {
            val count = ((end - start) * PI.toFloat() / 180f * r / 10f).toInt().coerceAtLeast(8)
            for (i in 0..count) {
                val angle = (start + (end - start) * i / count) * PI.toFloat() / 180f
                dot(cx + cos(angle) * r, cy + sin(angle) * r)
            }
        }
        fun clock(stopwatch: Boolean) {
            circle(50f, 55f, 29f)
            line(50f, 55f, 50f, 37f); line(50f, 55f, 65f, 55f)
            if (stopwatch) {
                line(43f, 18f, 57f, 18f); line(50f, 18f, 50f, 26f)
                line(70f, 32f, 76f, 27f)
            } else {
                poly(25f to 29f, 18f to 20f, 24f to 14f, 34f to 25f)
                poly(75f to 29f, 82f to 20f, 76f to 14f, 66f to 25f)
                line(30f, 81f, 24f, 88f); line(70f, 81f, 76f, 88f)
            }
        }
        when (name.lowercase().replace("_", "")) {
            "alarm", "notifications", "notificationsactive" -> {
                if (name.lowercase().contains("alarm") && name.lowercase() == "alarm") clock(false)
                else {
                    poly(25f to 68f, 29f to 62f, 31f to 42f, 37f to 31f, 50f to 25f, 63f to 31f, 69f to 42f, 71f to 62f, 75f to 68f, close = true)
                    line(20f, 68f, 80f, 68f); circle(50f, 76f, 5f, 0f, 180f)
                }
            }
            "timer" -> clock(true)
            "hourglassempty" -> poly(29f to 17f, 71f to 17f, 67f to 34f, 50f to 50f, 67f to 66f, 71f to 83f, 29f to 83f, 33f to 66f, 50f to 50f, 33f to 34f, close = true)
            "settings" -> { circle(50f, 50f, 25f); circle(50f, 50f, 9f); for (a in 0..7) { val angle = a * PI.toFloat() / 4f; line(50f + cos(angle) * 28f, 50f + sin(angle) * 28f, 50f + cos(angle) * 39f, 50f + sin(angle) * 39f) } }
            "add" -> { line(50f, 20f, 50f, 80f); line(20f, 50f, 80f, 50f) }
            "vibration" -> { poly(34f to 20f, 66f to 20f, 66f to 80f, 34f to 80f, close = true); line(24f, 30f, 24f, 70f); line(16f, 37f, 16f, 63f); line(76f, 30f, 76f, 70f); line(84f, 37f, 84f, 63f) }
            "musicnote" -> { poly(58f to 22f, 77f to 18f, 77f to 65f); circle(48f, 70f, 10f); circle(68f, 66f, 10f) }
            "volumeup" -> { poly(18f to 40f, 34f to 40f, 55f to 24f, 55f to 76f, 34f to 60f, 18f to 60f, close = true); circle(57f, 50f, 20f, -55f, 55f); circle(57f, 50f, 32f, -50f, 50f) }
            "label" -> { poly(22f to 30f, 61f to 20f, 80f to 39f, 43f to 77f, 22f to 56f, close = true); circle(36f, 43f, 3f) }
            "extension" -> poly(25f to 25f, 43f to 25f, 43f to 18f, 55f to 18f, 55f to 25f, 75f to 25f, 75f to 43f, 82f to 43f, 82f to 55f, 75f to 55f, 75f to 75f, 55f to 75f, 55f to 82f, 43f to 82f, 43f to 75f, 25f to 75f, close = true)
            "calculate" -> { poly(25f to 15f, 75f to 15f, 75f to 85f, 25f to 85f, close = true); poly(35f to 27f, 65f to 27f, 65f to 42f, 35f to 42f, close = true); for (x in listOf(38f, 50f, 62f)) for (y in listOf(55f, 68f)) circle(x, y, 2.5f) }
            "keyboard" -> { poly(12f to 30f, 88f to 30f, 88f to 70f, 12f to 70f, close = true); for (y in listOf(42f, 54f)) for (x in listOf(25f, 38f, 50f, 62f, 75f)) circle(x, y, 2f); line(35f, 63f, 65f, 63f) }
            "chevronright" -> poly(40f to 25f, 65f to 50f, 40f to 75f)
            "arrowback" -> { poly(45f to 23f, 18f to 50f, 45f to 77f); line(20f, 50f, 80f, 50f) }
            "close" -> { line(25f, 25f, 75f, 75f); line(75f, 25f, 25f, 75f) }
            "backspace" -> { poly(38f to 22f, 82f to 22f, 82f to 78f, 38f to 78f, 18f to 50f, close = true); line(48f, 40f, 68f, 60f); line(68f, 40f, 48f, 60f) }
            "uploadfile" -> { poly(22f to 65f, 22f to 80f, 78f to 80f, 78f to 65f); line(50f, 60f, 50f, 22f); poly(33f to 39f, 50f to 22f, 67f to 39f) }
            "playarrow" -> poly(32f to 20f, 76f to 50f, 32f to 80f, close = true)
            "stop" -> poly(30f to 30f, 70f to 30f, 70f to 70f, 30f to 70f, close = true)
            else -> circle(50f, 50f, 30f)
        }
    }
}

/** Keeps Android's familiar accessible switch in both visual modes. */
@Composable
fun TvimSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.Black,
            checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color.DarkGray
        )
    )
}

@Composable
fun NothingDesignSetting(checked: Boolean, onCheckedChange: (Boolean) -> Unit, lang: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvimIcon(Icons.Default.Extension, "Nothing Design", Modifier.size(24.dp), androidx.compose.material3.MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text("Nothing Design", color = Color.White)
            androidx.compose.material3.Text(
                if (lang == "ru") "Точечные цифры и иконки, шрифт Nothing для текста" else "Dot-matrix digits and icons with Nothing-style text",
                color = Color(0xFFAAAAAA), style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
        TvimSwitch(checked, onCheckedChange)
    }
}
