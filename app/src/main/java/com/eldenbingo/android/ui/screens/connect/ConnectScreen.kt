package com.eldenbingo.android.ui.screens.connect

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldenbingo.android.data.model.BingoConstants
import com.eldenbingo.android.data.network.EldenBingoClient
import androidx.core.content.edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    connectionState: EldenBingoClient.ConnectionState,
    onConnect: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onCreateRoom: (String, String, String, Int) -> Unit,
    onJoinRoom: (String, String, String, Int) -> Unit,
    onStatusClear: () -> Unit,
    statusMessage: String?,
    modifier: Modifier = Modifier
) {
    val prefs = LocalContext.current.getSharedPreferences("connect_inputs", Context.MODE_PRIVATE)
    fun saveString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }
    fun saveInt(key: String, value: Int) {
        prefs.edit {putInt(key, value)}
    }

    var serverAddress by rememberSaveable { mutableStateOf(prefs.getString("server_address", "bingocard.bingobrawlers.com") ?: "bingocard.bingobrawlers.com") }
    var serverPort by rememberSaveable { mutableStateOf(prefs.getString("server_port", BingoConstants.DEFAULT_PORT.toString()) ?: BingoConstants.DEFAULT_PORT.toString()) }
    var nickname by rememberSaveable { mutableStateOf(prefs.getString("nickname", "") ?: "") }
    var roomName by rememberSaveable { mutableStateOf(prefs.getString("room_name", "") ?: "") }
    var adminPass by rememberSaveable { mutableStateOf(prefs.getString("admin_pass", "") ?: "") }
    var selectedTeam by rememberSaveable { mutableIntStateOf(prefs.getInt("selected_team", 0)) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinDialog by rememberSaveable { mutableStateOf(false) }
    var expandedTeam by remember { mutableStateOf(false) }

    val teamNames = listOf("Red", "Blue", "Green", "Orange", "Purple", "Cyan", "Pink", "Brown", "Yellow", "Spectator")

    val eldenGold = Color(0xFFD4A843)
    val actionGold = Color(0xFFC99A34)
    val actionBronze = Color(0xFF6E5730)
    val actionRed = Color(0xFF7A2E2A)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1A),
                        Color(0xFF2D1B00)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = "ELDEN BINGO",
            style = MaterialTheme.typography.titleLarge,
            color = eldenGold,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp
        )
        Text(
            text = "Bingo Brawlers Client",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Server connection card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D2D)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Server Connection",
                    color = eldenGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = serverAddress,
                    onValueChange = {
                        serverAddress = it
                        saveString("server_address", it)
                    },
                    label = { Text("Server Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = eldenGold,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = serverPort,
                    onValueChange = {
                        serverPort = it
                        saveString("server_port", it)
                    },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = eldenGold,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Connection status
                when (connectionState) {
                    is EldenBingoClient.ConnectionState.Connected -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CastConnected,
                                contentDescription = null,
                                tint = Color.Green,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Connected", color = Color.Green)
                        }
                    }
                    is EldenBingoClient.ConnectionState.Connecting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...", color = eldenGold)
                    }
                    is EldenBingoClient.ConnectionState.Error -> {
                        Text("Error: ${connectionState.message}", color = Color.Red, fontSize = 12.sp)
                    }
                    EldenBingoClient.ConnectionState.Disconnected -> {
                        Text("Not connected", color = Color.Gray, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (connectionState is EldenBingoClient.ConnectionState.Connected) {
                        if (maxWidth < 360.dp) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ServerActionButton(
                                        text = "Create",
                                        containerColor = actionGold,
                                        contentColor = Color.Black,
                                        onClick = { showCreateDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ServerActionButton(
                                        text = "Join",
                                        containerColor = actionBronze,
                                        onClick = { showJoinDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                ServerActionButton(
                                    text = "Disconnect",
                                    containerColor = actionRed,
                                    onClick = { onDisconnect() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ServerActionButton(
                                    text = "Disconnect",
                                    containerColor = actionRed,
                                    onClick = { onDisconnect() },
                                    modifier = Modifier.weight(1.15f)
                                )
                                ServerActionButton(
                                    text = "Create",
                                    containerColor = actionGold,
                                    contentColor = Color.Black,
                                    onClick = { showCreateDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                                ServerActionButton(
                                    text = "Join",
                                    containerColor = actionBronze,
                                    onClick = { showJoinDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                val port = serverPort.toIntOrNull() ?: BingoConstants.DEFAULT_PORT
                                saveString("server_address", serverAddress)
                                saveString("server_port", serverPort)
                                onConnect(serverAddress, port)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = eldenGold
                            ),
                            enabled = serverAddress.isNotBlank() && connectionState !is EldenBingoClient.ConnectionState.Connecting
                        ) {
                            Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Status message
        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3D2B00)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = eldenGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusMessage,
                        color = eldenGold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onStatusClear) {
                        Text("Dismiss", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    // Create Room Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Room") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nickname,
                    onValueChange = {
                        nickname = it
                        saveString("nickname", it)
                    },
                        label = { Text("Nickname") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedTeam,
                        onExpandedChange = { expandedTeam = !expandedTeam }
                    ) {
                        OutlinedTextField(
                            value = if (selectedTeam == -1) "Spectator" else teamNames.getOrElse(selectedTeam) { "Team $selectedTeam" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Team") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTeam) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedTeam,
                            onDismissRequest = { expandedTeam = false }
                        ) {
                            teamNames.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedTeam = if (index == 9) -1 else index
                                        saveInt("selected_team", selectedTeam)
                                        expandedTeam = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = roomName,
                    onValueChange = {
                        roomName = it
                        saveString("room_name", it)
                    },
                        label = { Text("Room Name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adminPass,
                    onValueChange = {
                        adminPass = it
                        saveString("admin_pass", it)
                    },
                        label = { Text("Admin Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveString("nickname", nickname)
                        saveString("room_name", roomName)
                        saveString("admin_pass", adminPass)
                        saveInt("selected_team", selectedTeam)
                        onCreateRoom(roomName, adminPass, nickname, selectedTeam)
                        showCreateDialog = false
                    },
                    enabled = nickname.isNotBlank() && roomName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Join Room Dialog
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join Room") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nickname,
                    onValueChange = {
                        nickname = it
                        saveString("nickname", it)
                    },
                        label = { Text("Nickname") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedTeam,
                        onExpandedChange = { expandedTeam = !expandedTeam }
                    ) {
                        OutlinedTextField(
                            value = if (selectedTeam == -1) "Spectator" else teamNames.getOrElse(selectedTeam) { "Team $selectedTeam" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Team") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTeam) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedTeam,
                            onDismissRequest = { expandedTeam = false }
                        ) {
                            teamNames.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedTeam = if (index == 9) -1 else index
                                        saveInt("selected_team", selectedTeam)
                                        expandedTeam = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = roomName,
                    onValueChange = {
                        roomName = it
                        saveString("room_name", it)
                    },
                        label = { Text("Room Name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adminPass,
                    onValueChange = {
                        adminPass = it
                        saveString("admin_pass", it)
                    },
                        label = { Text("Admin Password (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveString("nickname", nickname)
                        saveString("room_name", roomName)
                        saveString("admin_pass", adminPass)
                        saveInt("selected_team", selectedTeam)
                        onJoinRoom(roomName, adminPass, nickname, selectedTeam)
                        showJoinDialog = false
                    },
                    enabled = nickname.isNotBlank() && roomName.isNotBlank()
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ServerActionButton(
    text: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
