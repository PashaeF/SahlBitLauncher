package ir.sahlbit.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import ir.sahlbit.launcher.R
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnalogClock(modifier: Modifier = Modifier, digitLanguage: String = "english", digitSizeSp: Int = 40, showSecondHand: Boolean = true) {
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now()
            delay(1000)
        }
    }
    val density = LocalDensity.current
    val digitSizePx = with(density) { digitSizeSp.sp.toPx() }
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension * 0.47f
        drawCircle(Color.White.copy(alpha = 0.16f), radius = radius + 5f)
        drawCircle(Color(0xFFF8FAFC), radius = radius)
        drawCircle(Color(0xFF2563EB), radius = radius, style = Stroke(width = 3f))

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(23, 32, 51)
            textSize = digitSizePx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        for (number in 1..12) {
            val angle = Math.toRadians((number * 30 - 90).toDouble())
            val x = cx + cos(angle).toFloat() * radius * 0.78f
            val y = cy + sin(angle).toFloat() * radius * 0.78f + textPaint.textSize * 0.34f
            drawContext.canvas.nativeCanvas.drawText(if (digitLanguage == "persian") number.toString().map { if (it in '0'..'9') ('۰'.code + it.code - '0'.code).toChar() else it }.joinToString("") else number.toString(), x, y, textPaint)
        }
        for (tick in 0 until 60) {
            val angle = Math.toRadians((tick * 6 - 90).toDouble())
            val outer = radius * 0.91f
            val inner = if (tick % 5 == 0) radius * 0.84f else radius * 0.88f
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner),
                end = Offset(cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer),
                strokeWidth = if (tick % 5 == 0) 2.5f else 1f
            )
        }
        val hourAngle = ((time.hour % 12) + time.minute / 60f) * 30f
        val minuteAngle = (time.minute + time.second / 60f) * 6f
        val secondAngle = time.second * 6f
        rotate(hourAngle, pivot = Offset(cx, cy)) {
            drawLine(Color(0xFF172033), Offset(cx, cy + radius * 0.08f), Offset(cx, cy - radius * 0.48f), strokeWidth = 6f)
        }
        rotate(minuteAngle, pivot = Offset(cx, cy)) {
            drawLine(Color(0xFF2563EB), Offset(cx, cy + radius * 0.08f), Offset(cx, cy - radius * 0.69f), strokeWidth = 4f)
        }
        if (showSecondHand) rotate(secondAngle, pivot = Offset(cx, cy)) {
            drawLine(Color(0xFF1D4ED8), Offset(cx, cy + radius * 0.18f), Offset(cx, cy - radius * 0.76f), strokeWidth = 1.8f)
        }
        drawCircle(Color(0xFFB35B45), radius = 5f, center = Offset(cx, cy))
    }
}
