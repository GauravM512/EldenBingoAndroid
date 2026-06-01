package com.eldenbingo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldenbingo.android.data.model.UserInRoom
import com.eldenbingo.android.ui.theme.EldenGold
import com.eldenbingo.android.ui.theme.TeamColors

@Composable
fun UserListItem(
    user: UserInRoom,
    modifier: Modifier = Modifier
) {
    val teamColor = when {
        user.isSpectator && user.isAdmin -> Color.White
        user.isSpectator -> Color.LightGray
        user.team in TeamColors.indices -> TeamColors[user.team]
        else -> Color.Gray
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(teamColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Name
        Text(
            text = user.nick,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        // Role badges
        if (user.isAdmin) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "[Admin]",
                color = EldenGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (user.isSpectator) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "[Spectator]",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

