package com.eldenbingo.android.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldenbingo.android.data.model.BingoBoardSquare
import com.eldenbingo.android.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BingoSquare(
    square: BingoBoardSquare,
    index: Int,
    boardSize: Int,
    isLockout: Boolean,
    myTeam: Int,
    onCheck: (Int) -> Unit,
    onMark: (Int) -> Unit,
    onCounterChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSpectator = myTeam == -1
    val checkedTeams = square.team.toList()
    val isMyTeamChecked = !isSpectator && square.team.contains(myTeam)

    val backgroundColor = when {
        checkedTeams.size > 1 -> {
            // Multi-team: use diagonal gradient
            val colors = checkedTeams.mapNotNull {
                if (it in TeamColors.indices) TeamColors[it] else null
            }
            if (colors.size >= 2) {
                Brush.linearGradient(
                    colors = colors,
                    start = Offset.Zero,
                    end = Offset(Float.MAX_VALUE, Float.MAX_VALUE)
                )
            } else if (colors.size == 1) Brush.linearGradient(colors + colors) else null
        }
        checkedTeams.size == 1 -> {
            val teamIdx = checkedTeams[0]
            if (teamIdx in TeamColors.indices) {
                val color = TeamColors[teamIdx].copy(alpha = 0.5f)
                Brush.linearGradient(listOf(color, color))
            } else null
        }
        else -> null
    }

    val borderColor = when {
        square.marked -> EldenGold
        isMyTeamChecked -> Color.White.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.15f)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(
                brush = backgroundColor ?: Brush.linearGradient(
                    listOf(Color(0xFF2A2A2A), Color(0xFF1F1F1F))
                )
            )
            .border(
                width = if (square.marked) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .combinedClickable(
                onClick = { onCheck(index) },
                onLongClick = { onMark(index) }
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (square.marked) {
            Text(
                text = "\u2605",
                color = EldenGold,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FittingSquareText(
                text = square.text,
                boardSize = boardSize,
                color = Color.White,
                modifier = Modifier.weight(1f, fill = false)
            )

            // Counters
            val visibleCounters = square.counters.filter { counter ->
                val isMyCounter = counter.team == myTeam
                val isNonZero = counter.counter > 0
                if (isSpectator) isNonZero else (isMyCounter && isNonZero)
            }

            if (visibleCounters.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    visibleCounters.forEach { counter ->
                        val teamColor = if (counter.team in TeamColors.indices) TeamColors[counter.team] else Color.White
                        Text(
                            text = "${counter.counter}",
                            color = teamColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FittingSquareText(
    text: String,
    boardSize: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val maxFontSize = when {
        boardSize <= 5 -> 10.sp
        boardSize <= 7 -> 8.sp
        else -> 6.sp
    }
    val minFontSize = when {
        boardSize <= 5 -> 5.sp
        boardSize <= 7 -> 4.5.sp
        else -> 4.sp
    }
    var fontSize by remember(text, boardSize) { mutableStateOf(maxFontSize) }
    var ready by remember(text, boardSize) { mutableStateOf(false) }

    Text(
        text = text,
        color = color.copy(alpha = if (ready) 1f else 0f),
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = Int.MAX_VALUE,
        softWrap = true,
        lineHeight = (fontSize.value * 1.08f).sp,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize.value > minFontSize.value) {
                fontSize = (fontSize.value - 0.5f).coerceAtLeast(minFontSize.value).sp
            } else {
                ready = true
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
