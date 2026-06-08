package com.eldenbingo.android.ui.screens.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import com.eldenbingo.android.data.model.BingoGameSettings
import com.eldenbingo.android.data.model.EldenRingClasses
import com.eldenbingo.android.data.model.MatchStatus
import com.eldenbingo.android.ui.theme.EldenBingoTheme
import com.eldenbingo.android.ui.theme.EldenGold

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(
    gameSettings: BingoGameSettings?,
    matchStatus: MatchStatus,
    isPaused: Boolean,
    onStartMatch: () -> Unit,
    onStopMatch: () -> Unit,
    onTogglePause: () -> Unit,
    onRandomizeBoard: () -> Unit,
    onUpdateSettings: (BingoGameSettings) -> Unit,
    onUploadBingoJson: (String) -> Unit,
    onBack: () -> Unit,
    onRequestSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Local state initialized once or when settings are explicitly refreshed
    var boardSize by rememberSaveable { mutableIntStateOf(gameSettings?.boardSize ?: 5) }
    var lockout by rememberSaveable { mutableStateOf(gameSettings?.lockout ?: true) }
    var randomClasses by rememberSaveable { mutableStateOf(gameSettings?.randomClasses ?: false) }
    var validClasses by rememberSaveable { mutableStateOf(gameSettings?.validClasses ?: EldenRingClasses.entries.toSet()) }
    var numClasses by rememberSaveable { mutableIntStateOf(gameSettings?.numberOfClasses ?: 5) }
    var categoryLimit by rememberSaveable { mutableIntStateOf(gameSettings?.categoryLimit ?: 2) }
    var preparationTime by rememberSaveable { mutableIntStateOf(gameSettings?.preparationTime ?: 0) }
    var pointsPerBingo by rememberSaveable { mutableIntStateOf(gameSettings?.pointsPerBingoLine ?: 1) }
    var randomSeed by rememberSaveable { mutableIntStateOf(gameSettings?.randomSeed ?: 0) }

    var selectedBingoJsonUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBingoJsonName by rememberSaveable { mutableStateOf<String?>(if (gameSettings == null) "bingo_default.json" else null) }
    var bingoJsonError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bingoJsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            selectedBingoJsonUri = uri.toString()
            selectedBingoJsonName = uri.path?.substringAfterLast('/') ?: uri.toString()
            bingoJsonError = null
        }
    }

    // Sync from server when gameSettings first becomes available
    var hasInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gameSettings) {
        if (gameSettings != null && !hasInitialized) {
            boardSize = gameSettings.boardSize
            lockout = gameSettings.lockout
            randomClasses = gameSettings.randomClasses
            validClasses = gameSettings.validClasses
            numClasses = gameSettings.numberOfClasses
            categoryLimit = gameSettings.categoryLimit
            preparationTime = gameSettings.preparationTime
            pointsPerBingo = gameSettings.pointsPerBingoLine
            randomSeed = gameSettings.randomSeed
            hasInitialized = true
        }
    }

    // Request settings when opening the screen
    LaunchedEffect(Unit) {
        onRequestSettings()
    }

    // Automatically update settings when any value changes
    LaunchedEffect(boardSize, lockout, randomClasses, validClasses, numClasses, categoryLimit, preparationTime, pointsPerBingo, randomSeed) {
        // Don't overwrite server settings with defaults until we've received the current settings
        if (!hasInitialized && gameSettings == null) return@LaunchedEffect

        val newSettings = BingoGameSettings(
            boardSize = boardSize,
            lockout = lockout,
            randomClasses = randomClasses,
            validClasses = validClasses,
            numberOfClasses = numClasses,
            categoryLimit = categoryLimit,
            randomSeed = randomSeed,
            preparationTime = preparationTime,
            pointsPerBingoLine = pointsPerBingo
        )

        // Only send if different from what we know the server has
        if (newSettings != gameSettings) {
            onUpdateSettings(newSettings)
        }
    }

    val isMatchRunning = matchStatus == MatchStatus.Running ||
            matchStatus == MatchStatus.Starting ||
            matchStatus == MatchStatus.Preparation

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = {
                Text("Admin Panel", color = EldenGold, fontWeight = FontWeight.Bold)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = EldenGold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2D2D2D))
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Match controls
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Match Controls", color = EldenGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isMatchRunning) {
                            Button(
                                onClick = onStartMatch,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Start Match")
                            }
                        } else {
                            Button(
                                onClick = onTogglePause,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isPaused) "Resume" else "Pause")
                            }
                            Button(
                                onClick = onStopMatch,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Stop Match")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isMatchRunning) {
                        OutlinedButton(
                            onClick = onRandomizeBoard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Randomize Board", color = EldenGold)
                        }
                    }
                }
            }

            // Game Settings
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Game Settings", color = EldenGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Board Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Board Size:", color = Color.White, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (boardSize > 3) boardSize-- }) {
                                Text("-", color = EldenGold)
                            }
                            Text("$boardSize", color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { if (boardSize < 11) boardSize++ }) {
                                Text("+", color = EldenGold)
                            }
                        }
                    }

                    // Lockout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lockout:", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(
                            checked = lockout,
                            onCheckedChange = { lockout = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = EldenGold)
                        )
                    }

                    // Random Seed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Random Seed:", color = Color.White, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { randomSeed-- }) {
                                Text("-", color = EldenGold)
                            }
                            Text("$randomSeed", color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { randomSeed++ }) {
                                Text("+", color = EldenGold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(onClick = { randomSeed = 0 }) {
                                Text("Reset", color = EldenGold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Preparation Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preparation time:", color = Color.White, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (preparationTime > 0) preparationTime -= 10 }) {
                                Text("-", color = EldenGold)
                            }
                            Text("${preparationTime}s", color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { preparationTime += 10 }) {
                                Text("+", color = EldenGold)
                            }
                        }
                    }

                    // Points Per Bingo Line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bonus points for bingo:", color = Color.White, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (pointsPerBingo > 1) pointsPerBingo-- }) {
                                Text("-", color = EldenGold)
                            }
                            Text("$pointsPerBingo", color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { if (pointsPerBingo < 10) pointsPerBingo++ }) {
                                Text("+", color = EldenGold)
                            }
                        }
                    }

                    // Random Classes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = randomClasses,
                                onCheckedChange = { randomClasses = it },
                                colors = CheckboxDefaults.colors(checkedColor = EldenGold)
                            )
                            Text("Limit starting classes:", color = Color.White)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (numClasses > 1) numClasses-- },
                                enabled = randomClasses
                            ) {
                                Text("-", color = if (randomClasses) EldenGold else Color.Gray)
                            }
                            Text(
                                "$numClasses",
                                color = if (randomClasses) Color.White else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { if (numClasses < 10) numClasses++ },
                                enabled = randomClasses
                            ) {
                                Text("+", color = if (randomClasses) EldenGold else Color.Gray)
                            }
                        }
                    }

                    // Class Selection List
                    AnimatedVisibility(
                        visible = randomClasses,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EldenRingClasses.entries.forEach { ringClass ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = validClasses.contains(ringClass),
                                                onCheckedChange = { checked ->
                                                    val newSet = validClasses.toMutableSet()
                                                    if (checked) newSet.add(ringClass) else newSet
                                                        .remove(ringClass)
                                                    validClasses = newSet
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = EldenGold),
                                                modifier = Modifier.scale(0.8f)
                                            )
                                            Text(
                                                ringClass.name,
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Max squares in same category
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Max squares in same category:", color = Color.White, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (categoryLimit > 1) categoryLimit-- }) {
                                Text("-", color = EldenGold)
                            }
                            Text("$categoryLimit", color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { if (categoryLimit < 10) categoryLimit++ }) {
                                Text("+", color = EldenGold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Bingo JSON", color = EldenGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                selectedBingoJsonName ?: "No file selected",
                                color = if (selectedBingoJsonName != null) Color.White else Color.Gray,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { bingoJsonPicker.launch(arrayOf("application/json", "text/json", "*/*")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Browse")
                                }
                                Button(
                                    onClick = {
                                        val uriString = selectedBingoJsonUri
                                        if (uriString == null) {
                                            bingoJsonError = "Select a JSON file first"
                                            return@Button
                                        }
                                        bingoJsonError = null
                                        val uri = Uri.parse(uriString)
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val json = context.contentResolver.openInputStream(uri)
                                                    ?.bufferedReader()
                                                    ?.use { it.readText() }
                                                if (json.isNullOrBlank()) {
                                                    bingoJsonError = "Unable to read JSON file"
                                                } else {
                                                    onUploadBingoJson(json)
                                                }
                                            } catch (ex: Exception) {
                                                bingoJsonError = ex.message ?: "Failed to read JSON"
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Upload")
                                }
                            }
                            if (!bingoJsonError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(bingoJsonError ?: "", color = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdminScreenPreview() {
    EldenBingoTheme {
        AdminScreen(
            gameSettings = BingoGameSettings(
                boardSize = 5,
                lockout = true,
                randomClasses = false,
                numberOfClasses = 2,
                categoryLimit = 3,
                preparationTime = 60,
                pointsPerBingoLine = 1,
                randomSeed = 1234
            ),
            matchStatus = MatchStatus.NotRunning,
            isPaused = false,
            onStartMatch = {},
            onStopMatch = {},
            onTogglePause = {},
            onRandomizeBoard = {},
            onUpdateSettings = {},
            onUploadBingoJson = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AdminScreenRunningPreview() {
    EldenBingoTheme {
        AdminScreen(
            gameSettings = BingoGameSettings(),
            matchStatus = MatchStatus.Running,
            isPaused = true,
            onStartMatch = {},
            onStopMatch = {},
            onTogglePause = {},
            onRandomizeBoard = {},
            onUpdateSettings = {},
            onUploadBingoJson = {},
            onBack = {}
        )
    }
}
