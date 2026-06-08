package com.eldenbingo.android.ui.screens.bingo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.eldenbingo.android.data.model.SquareClaimEvent
import com.eldenbingo.android.ui.components.BingoSquare
import com.eldenbingo.android.ui.theme.EldenGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BingoCardScreen(
    board: BingoBoard?,
    matchStatus: MatchStatus,
    localTeam: Int,
    squareClaimEvents: SharedFlow<SquareClaimEvent>,
    onCheck: (Int) -> Unit,
    onMark: (Int) -> Unit,
    onCounterChange: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onRandomize: () -> Unit,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showHints by remember { mutableStateOf(false) }

    LaunchedEffect(squareClaimEvents) {
        squareClaimEvents.collect { event ->
            snackbarHostState.showSnackbar(
                message = "${event.nick} claimed '${event.squareText}'",
                duration = SnackbarDuration.Short
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                IconButton(onClick = { showHints = !showHints }) {
                    Icon(
                        if (showHints) Icons.Default.Help else Icons.Default.HelpOutline,
                        contentDescription = "Toggle Hints",
                        tint = EldenGold
                    )
                }
                if (isAdmin && matchStatus != MatchStatus.Running) {
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

            // Interaction Guide (UI Hint)
            AnimatedVisibility(
                visible = showHints,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InteractionHintItem(text = "Tap: Claim", modifier = Modifier.weight(1f))
                        InteractionHintItem(text = "2x Tap: Details", modifier = Modifier.weight(1f))
                        InteractionHintItem(text = "Hold: Star", modifier = Modifier.weight(1f))
                    }
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

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
}

@Composable
private fun InteractionHintItem(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = EldenGold.copy(alpha = 0.8f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
