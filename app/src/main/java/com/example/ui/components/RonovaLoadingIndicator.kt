package com.example.ui.components

import android.graphics.PathMeasure
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.RonovaCrimson
import com.example.ui.theme.RonovaDeepPurple
import com.example.ui.theme.RonovaGlow
import com.example.ui.theme.RonovaPurple
import com.example.ui.theme.RonovaRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom indeterminate spinner shaped like an infinity symbol (∞),
 * animated with a continuous traveling gradient sweep from dark purple through red to crimson.
 * Parameterized by size, fully reusable across connection tests, model loading, and streaming gaps.
 */
@Composable
fun RonovaLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    strokeWidth: Dp = (size.value * 0.1f).coerceIn(2.5f, 6f).dp,
    trackAlpha: Float = 0.22f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ronova_infinity_anim")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing)
        ),
        label = "infinity_progress"
    )

    // Aspect ratio of infinity symbol is roughly 2:1
    val width = size
    val height = size / 2

    Box(
        modifier = modifier
            .width(width)
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width, height)) {
            val strokePx = strokeWidth.toPx()
            val w = size.toPx()
            val h = size.toPx() / 2f
            val cx = w / 2f
            val cy = h / 2f

            // Parametric Bernoulli Lemniscate
            val a = (w / 2f) - strokePx - 2f
            val steps = 120
            val fullPath = Path()
            var startPos = Offset.Zero

            for (i in 0..steps) {
                val t = (i.toFloat() / steps) * (2f * PI.toFloat())
                val denom = 1f + sin(t) * sin(t)
                val x = cx + (a * cos(t)) / denom
                val y = cy + (a * sin(t) * cos(t)) / denom

                if (i == 0) {
                    fullPath.moveTo(x, y)
                    startPos = Offset(x, y)
                } else {
                    fullPath.lineTo(x, y)
                }
            }
            fullPath.close()

            // 1. Draw background subtle guide track
            drawPath(
                path = fullPath,
                color = RonovaDeepPurple.copy(alpha = trackAlpha),
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 2. Compute traveling active segment using PathMeasure
            val androidPath = fullPath.asAndroidPath()
            val pathMeasure = PathMeasure(androidPath, true)
            val totalLength = pathMeasure.length

            if (totalLength > 0f) {
                val segmentLength = totalLength * 0.42f
                val startDistance = (progress * totalLength) % totalLength
                val endDistance = startDistance + segmentLength

                val activePath = android.graphics.Path()

                if (endDistance <= totalLength) {
                    pathMeasure.getSegment(startDistance, endDistance, activePath, true)
                } else {
                    // Wraps around the closed loop
                    pathMeasure.getSegment(startDistance, totalLength, activePath, true)
                    pathMeasure.getSegment(0f, endDistance - totalLength, activePath, false)
                }

                // Gradient brush along the traveling segment
                val gradientBrush = Brush.linearGradient(
                    colors = listOf(
                        RonovaDeepPurple.copy(alpha = 0.2f),
                        RonovaPurple,
                        RonovaCrimson,
                        RonovaRed,
                        RonovaGlow
                    ),
                    start = Offset(0f, cy),
                    end = Offset(w, cy)
                )

                drawPath(
                    path = activePath.asComposePath(),
                    brush = gradientBrush,
                    style = Stroke(
                        width = strokePx * 1.15f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 3. Draw bright leading energy bead at head of the segment
                val headDistance = endDistance % totalLength
                val pos = FloatArray(2)
                val tan = FloatArray(2)
                if (pathMeasure.getPosTan(headDistance, pos, tan)) {
                    drawCircle(
                        color = Color.White,
                        radius = strokePx * 0.75f,
                        center = Offset(pos[0], pos[1])
                    )
                    drawCircle(
                        color = RonovaGlow.copy(alpha = 0.5f),
                        radius = strokePx * 1.3f,
                        center = Offset(pos[0], pos[1])
                    )
                }
            }
        }
    }
}
