package com.eldenbingo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldenbingo.android.data.model.ChatMessage
import com.eldenbingo.android.ui.theme.TeamColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(message.timestamp))

    val nameColor = when {
        message.team == -1 -> Color.LightGray
        message.team in TeamColors.indices -> TeamColors[message.team]
        else -> Color.White
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "[$timeStr] ",
            color = Color.Gray,
            fontSize = 11.sp,
        )
        Text(
            text = "${message.userName}: ",
            color = nameColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message.message,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}
