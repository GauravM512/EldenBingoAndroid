package com.eldenbingo.android.ui.screens.bingo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldenbingo.android.data.model.BingoBoard
import com.eldenbingo.android.data.model.BingoBoardSquare
import com.eldenbingo.android.data.model.MatchStatus
import com.eldenbingo.android.ui.components.BingoSquare
import com.eldenbingo.android.ui.theme.EldenGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BingoCardScreen(
    board: BingoBoard?,
    matchStatus: MatchStatus,
    localTeam: Int,
    onCheck: (Int) -> Unit,
    onMark: (Int) -> Unit,
    onCounterChange: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onRandomize: () -> Unit,
    isAdmin: Boolean,
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
                    "Bingo Card",
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
            ),
            actions = {
                if (isAdmin) {
                    TextButton(onClick = onRandomize) {
                        Text("Randomize", color = EldenGold, fontSize = 12.sp)
                    }
                }
            }
        )

        if (board == null) {
            // No board
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (matchStatus == MatchStatus.NotRunning)
                            "No board yet. Wait for the match to start."
                        else
                            "Loading board...",
                        color = Color.Gray,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Board info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${board.size}x${board.size} Board",
                    color = Color.White,
                    fontSize = 12.sp
                )
                if (board.lockout) {
                    Text(
                        text = "LOCKOUT",
                        color = Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (board.availableClasses.isNotEmpty()) {
                    Text(
                        text = "Classes: ${board.availableClasses.joinToString(", ") { it.name }}",
                        color = Color(0xFF9E9E9E),
                        fontSize = 10.sp
                    )
                }
            }

            // Bingo grid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0 until board.size) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (col in 0 until board.size) {
                            val index = row * board.size + col
                            val square = board.squares.getOrElse(index) {
                                BingoBoardSquare("", "", intArrayOf(), false, emptyList())
                            }
                            BingoSquare(
                                square = square,
                                index = index,
                                boardSize = board.size,
                                isLockout = board.lockout,
                                myTeam = localTeam,
                                onCheck = onCheck,
                                onMark = onMark,
                                onCounterChange = onCounterChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
