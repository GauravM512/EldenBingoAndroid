package com.eldenbingo.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eldenbingo.android.EldenBingoApp
import com.eldenbingo.android.data.model.*
import com.eldenbingo.android.data.network.EldenBingoClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val client: EldenBingoClient
        get() = (getApplication<EldenBingoApp>()).client

    // ---- Connection State ----
    val connectionState: StateFlow<EldenBingoClient.ConnectionState>
        get() = client.connectionState

    val isConnected: Boolean
        get() = client.isConnected

    // ---- Room State ----
    val roomState: StateFlow<RoomState>
        get() = client.roomState

    val localUser: UserInRoom?
        get() = client.localUser

    // ---- Bingo Board ----
    val bingoBoard: StateFlow<BingoBoard?>
        get() = client.bingoBoard

    // ---- Chat ----
    val chatMessages: StateFlow<List<ChatMessage>>
        get() = client.chatMessages

    // ---- Map ----
    val playerPositions: StateFlow<Map<UUID, PlayerPosition>>
        get() = client.playerPositions

    // ---- Scoreboard ----
    val scoreboard: StateFlow<List<TeamScore>>
        get() = client.scoreboard

    // ---- Bingo Lines ----
    val bingoLines: StateFlow<List<BingoLine>>
        get() = client.bingoLines

    // ---- Status ----
    val statusMessage: StateFlow<String?>
        get() = client.statusMessage

    val soundEvents: SharedFlow<GameSound>
        get() = client.soundEvents

    val squareClaimEvents: SharedFlow<SquareClaimEvent>
        get() = client.squareClaimEvents

    // ---- Game Settings ----
    val gameSettings: StateFlow<BingoGameSettings?>
        get() = client.gameSettings

    // ---- Room Name Suggestion ----
    val roomNameSuggestion: StateFlow<String?>
        get() = client.roomNameSuggestion

    // ---- Timer display ----
    val matchTimerString: String
        get() {
            val state = roomState.value
            val totalSeconds = kotlin.math.abs(state.timer / 1000)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

    val matchStatusString: String
        get() {
            val state = roomState.value
            if (state.paused) return "Paused"
            return when (state.matchStatus) {
                MatchStatus.NotRunning -> "Not Running"
                MatchStatus.Starting -> "Starting..."
                MatchStatus.Preparation -> "Preparation"
                MatchStatus.Running -> "Running"
                MatchStatus.Finished -> "Match Finished"
            }
        }

    private var pendingDefaultBingoJsonUpload = false

    init {
        viewModelScope.launch {
            roomState.collect { state ->
                if (!pendingDefaultBingoJsonUpload) return@collect
                if (state.name.isNotBlank() && client.localUser?.isAdmin == true) {
                    pendingDefaultBingoJsonUpload = false
                    sendDefaultBingoJson()
                }
            }
        }
    }

    private suspend fun sendDefaultBingoJson() {
        try {
            val json = getApplication<EldenBingoApp>().assets.open("bingo_default.json").bufferedReader().use { it.readText() }
            client.sendBingoJson(json)
        } catch (ex: Exception) {
            // If the default JSON fails to load/send, leave the room creation flow intact.
        }
    }

    // ---- Actions ----

    fun connect(address: String, port: Int) {
        viewModelScope.launch {
            client.connect(address, port)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            if (roomState.value.name.isNotBlank()) {
                try {
                    client.leaveRoom()
                } catch (_: Exception) {
                }
            }
            client.disconnect()
        }
    }

    fun requestRoomName() {
        viewModelScope.launch { client.requestRoomName() }
    }

    fun createRoom(roomName: String, adminPass: String, nick: String, team: Int, settings: BingoGameSettings = BingoGameSettings(), uploadDefaultBingoJson: Boolean = false) {
        pendingDefaultBingoJsonUpload = uploadDefaultBingoJson
        viewModelScope.launch {
            client.createRoom(roomName, adminPass, nick, team, settings)
        }
    }

    fun joinRoom(roomName: String, adminPass: String, nick: String, team: Int) {
        viewModelScope.launch {
            client.joinRoom(roomName, adminPass, nick, team)
        }
    }

    fun leaveRoom() {
        pendingDefaultBingoJsonUpload = false
        viewModelScope.launch {
            client.leaveRoom()
        }
    }

    fun sendChat(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch { client.sendChat(message) }
    }

    fun tryCheck(index: Int) {
        viewModelScope.launch { client.tryCheck(index, localUser?.guid) }
    }

    fun tryMark(index: Int) {
        viewModelScope.launch { client.tryMark(index) }
    }

    fun trySetCounter(index: Int, change: Int) {
        viewModelScope.launch { client.trySetCounter(index, change) }
    }

    fun changeMatchStatus(status: MatchStatus) {
        viewModelScope.launch {
            client.changeMatchStatus(status)
        }
    }

    fun togglePause() {
        viewModelScope.launch {
            client.togglePause()
        }
    }

    fun requestCurrentGameSettings() {
        viewModelScope.launch {
            client.requestCurrentGameSettings()
        }
    }

    fun setGameSettings(settings: BingoGameSettings) {
        viewModelScope.launch {
            client.setGameSettings(settings)
        }
    }

    fun requestTeamChange(team: Int) {
        viewModelScope.launch {
            client.requestTeamChange(team)
        }
    }

    fun uploadBingoJson(json: String) {
        viewModelScope.launch {
            client.sendBingoJson(json)
        }
    }

    fun randomizeBoard() {
        viewModelScope.launch { client.randomizeBoard() }
    }

    fun clearStatus() {
        client.clearStatus()
    }
}
