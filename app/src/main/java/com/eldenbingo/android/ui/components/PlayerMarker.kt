package com.eldenbingo.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.eldenbingo.android.ui.theme.TeamColors

@Composable
fun PlayerMarker(
    playerName: String,
    team: Int,
    angle: Float,
    isUnderground: Boolean,
    modifier: Modifier = Modifier
) {
    val teamColor = if (team in TeamColors.indices) TeamColors[team] else Color.White

    Canvas(modifier = modifier.size(24.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val arrowSize = size.width * 0.4f

        rotate(angle) {
            // Arrow pointing up
            val path = Path().apply {
                moveTo(center.x, center.y - arrowSize)
                lineTo(center.x - arrowSize * 0.6f, center.y + arrowSize * 0.4f)
                lineTo(center.x + arrowSize * 0.6f, center.y + arrowSize * 0.4f)
                close()
            }
            drawPath(
                path = path,
                color = teamColor,
                style = Fill
            )

            // Outline
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 2f)
            )
        }

        // Underground indicator
        if (isUnderground) {
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = size.width * 0.15f,
                center = Offset(center.x, center.y + arrowSize * 0.7f)
            )
        }
    }
}
