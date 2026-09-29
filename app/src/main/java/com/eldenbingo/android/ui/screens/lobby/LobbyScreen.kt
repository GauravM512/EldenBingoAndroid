package com.eldenbingo.android.ui.screens.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldenbingo.android.data.model.*
import com.eldenbingo.android.ui.components.UserListItem
import com.eldenbingo.android.ui.theme.EldenGold
import com.eldenbingo.android.ui.theme.TeamColors
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    roomState: RoomState,
    localUser: UserInRoom?,
    scoreboard: List<TeamScore>,
    bingoLines: List<BingoLine>,
    matchEvents: List<MatchEvent>,
    bingoBoard: BingoBoard?,
    matchTimerString: String,
    matchStatusString: String,
    onLeaveRoom: () -> Unit,
    onChangeTeam: (Int) -> Unit,
    onSetTeamName: (Int, String) -> Unit = { _, _ -> },
    onBanUser: (UUID) -> Unit = {},
    onPromoteToAdmin: (UUID) -> Unit = {},
    onTogglePause: () -> Unit,
    onStartMatch: () -> Unit,
    onStopMatch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBingo: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAdmin = localUser?.isAdmin == true
    var showTeamDialog by remember { mutableStateOf(false) }
    var showSetTeamNameDialog by remember { mutableStateOf(false) }
    var selectedUserForAdminAction by remember { mutableStateOf<UserInRoom?>(null) }
    val isMatchRunning = roomState.matchStatus == MatchStatus.Running ||
            roomState.matchStatus == MatchStatus.Starting ||
            roomState.matchStatus == MatchStatus.Preparation

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Top bar with room info and controls
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Lobby: ${roomState.name}",
                        color = EldenGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = matchStatusString,
                            color = when (roomState.matchStatus) {
                                MatchStatus.Running -> Color.Green
                                MatchStatus.Starting -> Color(0xFFFF9800)
                                MatchStatus.Preparation -> Color(0xFF7B1FA2)
                                MatchStatus.Finished -> Color(0xFF00BCD4)
                                else -> Color.Gray
                            },
                            fontSize = 14.sp
                        )
                        if (isMatchRunning) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = matchTimerString,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF2D2D2D)
            ),
            actions = {
                if (isAdmin) {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Lobby Settings", tint = EldenGold)
                    }
                }
                IconButton(onClick = onNavigateToBingo) {
                    Icon(Icons.Default.GridOn, "Bingo Card", tint = EldenGold)
                }
                IconButton(onClick = onNavigateToChat) {
                    Icon(Icons.AutoMirrored.Filled.Chat, "Chat", tint = EldenGold)
                }
                IconButton(onClick = onNavigateToMap) {
                    Icon(Icons.Default.Map, "Map", tint = EldenGold)
                }
                IconButton(onClick = onLeaveRoom) {
                    Icon(Icons.AutoMirrored.Filled.Logout, "Leave", tint = Color.Red)
                }
            }
        )

        // Quick action buttons for admins
        if (isAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isMatchRunning) {
                    Button(
                        onClick = onStartMatch,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start Match", fontSize = 14.sp)
                    }
                } else {
                    Button(
                        onClick = onTogglePause,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (roomState.paused) "Resume" else "Pause", fontSize = 14.sp)
                    }
                    Button(
                        onClick = onStopMatch,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Stop", fontSize = 14.sp)
                    }
                }
                OutlinedButton(
                    onClick = { showTeamDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Change Team", fontSize = 13.sp, color = EldenGold)
                }
                OutlinedButton(
                    onClick = { showSetTeamNameDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Team Name", fontSize = 13.sp, color = EldenGold)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { showTeamDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Change Team", fontSize = 13.sp, color = EldenGold)
                }
                OutlinedButton(
                    onClick = { showSetTeamNameDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Team Name", fontSize = 13.sp, color = EldenGold)
                }
            }
        }

        // Content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Users list
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Players (${roomState.users.size})",
                        color = EldenGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        roomState.users.forEach { user ->
                            UserListItem(
                                user = user,
                                onClick = if (isAdmin && user.guid != localUser.guid) {
                                    { selectedUserForAdminAction = user }
                                } else null
                            )
                        }
                    }
                }
            }

            // Scoreboard
            if (scoreboard.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Scoreboard",
                            color = EldenGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        scoreboard.sortedByDescending { it.score }.forEach { score ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val teamColor = if (score.team in TeamColors.indices) TeamColors[score.team] else Color.White
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(teamColor, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = score.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${score.score} pts",
                                    color = EldenGold,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Bingo lines achieved
            if (bingoLines.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Bingo Lines",
                            color = EldenGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        bingoLines.forEach { line ->
                            val teamColor = if (line.team in TeamColors.indices) TeamColors[line.team] else Color.White
                            val typeStr = when (line.type) {
                                0 -> "column ${line.bingoIndex + 1}"
                                1 -> "row ${line.bingoIndex + 1}"
                                2 -> "diagonal TL->BR"
                                3 -> "diagonal BL->TR"
                                else -> "unknown"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(teamColor, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "[${line.timerStr}] ${line.name} BINGO on $typeStr!",
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Match Log
            if (matchEvents.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Match Log",
                            color = EldenGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            matchEvents.asReversed().forEach { event ->
                                val teamColor = if (event.team in TeamColors.indices) TeamColors[event.team] else Color.White
                                val action = if (event.checked) "marked" else "unmarked"
                                
                                val totalSeconds = kotlin.math.abs(event.timestamp / 1000)
                                val hours = totalSeconds / 3600
                                val minutes = (totalSeconds % 3600) / 60
                                val seconds = totalSeconds % 60
                                val timestampStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                                val squareText = if (event.squareIndex >= 0 && bingoBoard != null && event.squareIndex < bingoBoard.squares.size) {
                                    bingoBoard.squares[event.squareIndex].text
                                } else if (event.eventType == MatchEventType.Bingo) {
                                    "BINGO!"
                                } else {
                                    "square ${event.squareIndex}"
                                }

                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(6.dp)
                                            .background(teamColor, RoundedCornerShape(1.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "[$timestampStr] ${event.player} $action $squareText",
                                        color = Color.LightGray,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Navigation cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    onClick = onNavigateToBingo,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2B00)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.GridOn, null, tint = EldenGold, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Bingo Card", color = EldenGold, fontSize = 14.sp)
                    }
                }
                Card(
                    onClick = onNavigateToChat,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2B00)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = EldenGold, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Chat", color = EldenGold, fontSize = 14.sp)
                    }
                }
                Card(
                    onClick = onNavigateToMap,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2B00)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Map, null, tint = EldenGold, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Map", color = EldenGold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (showTeamDialog) {
        ChangeTeamDialog(
            currentTeam = localUser?.team ?: -1,
            onDismiss = { showTeamDialog = false },
            onConfirm = { team ->
                showTeamDialog = false
                onChangeTeam(team)
            }
        )
    }

    if (showSetTeamNameDialog) {
        SetTeamNameDialog(
            currentTeam = localUser?.team ?: 0,
            onDismiss = { showSetTeamNameDialog = false },
            onConfirm = { team, name ->
                showSetTeamNameDialog = false
                onSetTeamName(team, name)
            }
        )
    }

    val targetUser = selectedUserForAdminAction
    if (targetUser != null) {
        UserActionDialog(
            user = targetUser,
            onDismiss = { selectedUserForAdminAction = null },
            onPromote = {
                selectedUserForAdminAction = null
                onPromoteToAdmin(targetUser.guid)
            },
            onBan = {
                selectedUserForAdminAction = null
                onBanUser(targetUser.guid)
            }
        )
    }
}

@Composable
private fun SetTeamNameDialog(
    currentTeam: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var teamNameText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Set Team Name", color = EldenGold, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Enter custom name for ${BingoConstants.getTeamName(currentTeam)}:",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = teamNameText,
                    onValueChange = { teamNameText = it },
                    label = { Text("Team Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentTeam, teamNameText) },
                enabled = teamNameText.isNotBlank()
            ) {
                Text("Save", color = EldenGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2D2D2D)
    )
}

@Composable
private fun UserActionDialog(
    user: UserInRoom,
    onDismiss: () -> Unit,
    onPromote: () -> Unit,
    onBan: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Admin Action: ${user.nick}", color = EldenGold, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!user.isAdmin) {
                    Button(
                        onClick = onPromote,
                        colors = ButtonDefaults.buttonColors(containerColor = EldenGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Promote to Admin", color = Color.Black)
                    }
                }
                Button(
                    onClick = onBan,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ban from Room", color = Color.White)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2D2D2D)
    )
}

@Composable
private fun ChangeTeamDialog(
    currentTeam: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedTeam by remember(currentTeam) { mutableIntStateOf(currentTeam) }
    val teams = remember { listOf(-1) + BingoConstants.TEAM_COLORS.indices.toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Change Team", color = EldenGold, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                teams.forEach { team ->
                    val teamColor = if (team in TeamColors.indices) TeamColors[team] else Color.Gray
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedTeam == team,
                                onClick = { selectedTeam = team }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTeam == team,
                            onClick = { selectedTeam = team },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = EldenGold,
                                unselectedColor = Color.Gray
                            )
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(teamColor, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = BingoConstants.getTeamName(team),
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTeam) }) {
                Text("OK", color = EldenGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2D2D2D)
    )
}
