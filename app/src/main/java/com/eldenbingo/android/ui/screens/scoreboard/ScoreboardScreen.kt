package com.eldenbingo.android.ui.screens.scoreboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldenbingo.android.data.model.TeamScore
import com.eldenbingo.android.ui.theme.EldenGold
import com.eldenbingo.android.ui.theme.TeamColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardScreen(
    scoreboard: List<TeamScore>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        TopAppBar(
            title = {
                Text(
                    "Scoreboard",
                    color = EldenGold,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = EldenGold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF2D2D2D)
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(scoreboard.sortedByDescending { it.score }) { score ->
                val teamColor = if (score.team in TeamColors.indices) TeamColors[score.team] else Color.White
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(teamColor, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = score.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${score.score}",
                            color = EldenGold,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
