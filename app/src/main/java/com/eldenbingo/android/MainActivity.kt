package com.eldenbingo.android

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eldenbingo.android.audio.AndroidSoundPlayer
import com.eldenbingo.android.data.model.BingoConstants
import com.eldenbingo.android.data.model.MatchStatus
import com.eldenbingo.android.ui.navigation.Screen
import com.eldenbingo.android.ui.screens.admin.AdminScreen
import com.eldenbingo.android.ui.screens.bingo.BingoCardScreen
import com.eldenbingo.android.ui.screens.chat.ChatScreen
import com.eldenbingo.android.ui.screens.connect.ConnectScreen
import com.eldenbingo.android.ui.screens.lobby.LobbyScreen
import com.eldenbingo.android.ui.screens.map.MapPreviewScreen
import com.eldenbingo.android.ui.screens.scoreboard.ScoreboardScreen
import com.eldenbingo.android.ui.theme.EldenBingoTheme
import com.eldenbingo.android.viewmodel.GameViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setContent {
            EldenBingoTheme(darkTheme = true) {
                EldenBingoMain(viewModel)
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            viewModel.disconnect()
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EldenBingoMain(
    viewModel: GameViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current
    val soundPlayer = remember { AndroidSoundPlayer(context) }
    var showExitConfirm by remember { mutableStateOf(false) }

    val connectionState by viewModel.connectionState.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    val bingoBoard by viewModel.bingoBoard.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val playerPositions by viewModel.playerPositions.collectAsState()
    val scoreboard by viewModel.scoreboard.collectAsState()
    val bingoLines by viewModel.bingoLines.collectAsState()
    val matchEvents by viewModel.matchEvents.collectAsState()
    val matchLog by viewModel.matchLog.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val gameSettings by viewModel.gameSettings.collectAsState()

    val localUser = viewModel.localUser
    val isInRoom = roomState.name.isNotEmpty()
    var autoConnectAttempted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(connectionState, autoConnectAttempted) {
        if (!autoConnectAttempted && connectionState is com.eldenbingo.android.data.network.EldenBingoClient.ConnectionState.Disconnected) {
            val prefs = context.getSharedPreferences("connect_inputs", Context.MODE_PRIVATE)
            val address = prefs.getString("server_address", "bingocard.bingobrawlers.com") ?: "bingocard.bingobrawlers.com"
            val portString = prefs.getString("server_port", BingoConstants.DEFAULT_PORT.toString()) ?: BingoConstants.DEFAULT_PORT.toString()
            val port = portString.toIntOrNull() ?: BingoConstants.DEFAULT_PORT
            autoConnectAttempted = true
            viewModel.connect(address, port)
        }
    }

    DisposableEffect(isInRoom) {
        val activity = context as? Activity
        if (isInRoom) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(soundPlayer) {
        viewModel.soundEvents.collect { sound ->
            soundPlayer.play(sound)
        }
    }

    DisposableEffect(soundPlayer) {
        onDispose {
            soundPlayer.release()
        }
    }

    LaunchedEffect(statusMessage, connectionState) {
        if (statusMessage != null && connectionState !is com.eldenbingo.android.data.network.EldenBingoClient.ConnectionState.Error) {
            delay(4_000)
            viewModel.clearStatus()
        }
    }

    // Navigate to lobby when connected and in a room
    LaunchedEffect(isInRoom, navBackStackEntry?.destination?.route) {
        if (isInRoom && navBackStackEntry?.destination?.route == Screen.Connect.route) {
            navController.navigate(Screen.Lobby.route) {
                popUpTo(Screen.Connect.route) { inclusive = true }
            }
        }
        if (isInRoom) {
            viewModel.requestCurrentGameSettings()
        }
    }

    // Navigate back to connect when disconnected
    LaunchedEffect(connectionState) {
        if (connectionState is com.eldenbingo.android.data.network.EldenBingoClient.ConnectionState.Disconnected) {
            if (navBackStackEntry?.destination?.route != Screen.Connect.route) {
                navController.navigate(Screen.Connect.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Connect.route,
            modifier = Modifier.fillMaxSize()
        ) {
            // Connect Screen
            composable(Screen.Connect.route) {
                ConnectScreen(
                    connectionState = connectionState,
                    onConnect = { address, port ->
                        viewModel.connect(address, port)
                    },
                    onDisconnect = { viewModel.disconnect() },
                    onCreateRoom = { roomName, adminPass, nick, team ->
                        viewModel.createRoom(roomName, adminPass, nick, team, uploadDefaultBingoJson = true)
                    },
                    onJoinRoom = { roomName, adminPass, nick, team ->
                        viewModel.joinRoom(roomName, adminPass, nick, team)
                    },
                    onStatusClear = { viewModel.clearStatus() },
                    statusMessage = statusMessage
                )
            }

            // Lobby Screen
            composable(Screen.Lobby.route) {
                BackHandler(enabled = isInRoom) {
                    showExitConfirm = true
                }

                LobbyScreen(
                    roomState = roomState,
                    localUser = localUser,
                    scoreboard = scoreboard,
                    bingoLines = bingoLines,
                    matchEvents = matchEvents,
                    bingoBoard = bingoBoard,
                    matchTimerString = viewModel.matchTimerString,
                    matchStatusString = viewModel.matchStatusString,
                    onLeaveRoom = {
                        viewModel.leaveRoom()
                        navController.navigate(Screen.Connect.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onChangeTeam = { team -> viewModel.requestTeamChange(team) },
                    onTogglePause = { viewModel.togglePause() },
                    onStartMatch = { viewModel.changeMatchStatus(MatchStatus.Starting) },
                    onStopMatch = { viewModel.changeMatchStatus(MatchStatus.Finished) },
                    onNavigateToSettings = { navController.navigate(Screen.Admin.route) },
                    onNavigateToBingo = { navController.navigate(Screen.BingoCard.route) },
                    onNavigateToChat = { navController.navigate(Screen.Chat.route) },
                    onNavigateToMap = { navController.navigate(Screen.MapPreview.route) }
                )
            }

            // Bingo Card Screen
            composable(Screen.BingoCard.route) {
                BingoCardScreen(
                    board = bingoBoard,
                    matchStatus = roomState.matchStatus,
                    localTeam = localUser?.team ?: 0,
                    squareClaimEvents = viewModel.squareClaimEvents,
                    onCheck = { viewModel.tryCheck(it) },
                    onMark = { viewModel.tryMark(it) },
                    onCounterChange = { index, change -> viewModel.trySetCounter(index, change) },
                    onBack = { navController.popBackStack() },
                    onRandomize = { viewModel.randomizeBoard() },
                    isAdmin = localUser?.isAdmin == true
                )
            }

            // Chat Screen
            composable(Screen.Chat.route) {
                ChatScreen(
                    messages = chatMessages,
                    onSendMessage = { viewModel.sendChat(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            // Map Preview Screen
            composable(Screen.MapPreview.route) {
                MapPreviewScreen(
                    playerPositions = playerPositions,
                    onBack = { navController.popBackStack() }
                )
            }

            // Scoreboard Screen
            composable(Screen.Scoreboard.route) {
                ScoreboardScreen(
                    scoreboard = scoreboard,
                    onBack = { navController.popBackStack() }
                )
            }

            // Admin Screen
            composable(Screen.Admin.route) {
                AdminScreen(
                    gameSettings = gameSettings,
                    matchStatus = roomState.matchStatus,
                    isPaused = roomState.paused,
                    matchLog = matchLog,
                    onStartMatch = { viewModel.changeMatchStatus(MatchStatus.Starting) },
                    onStopMatch = { viewModel.changeMatchStatus(MatchStatus.Finished) },
                    onTogglePause = { viewModel.togglePause() },
                    onRandomizeBoard = { viewModel.randomizeBoard() },
                    onUpdateSettings = { viewModel.setGameSettings(it) },
                    onUploadBingoJson = { viewModel.uploadBingoJson(it) },
                    onBack = { navController.popBackStack() },
                    onRequestSettings = { viewModel.requestCurrentGameSettings() },
                    onRequestMatchLog = { viewModel.requestMatchLog(it) }
                )
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit Elden Bingo?") },
            text = { Text("You are currently in a lobby. Are you sure you want to exit?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        viewModel.disconnect()
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("No")
                }
            }
        )
    }
}
