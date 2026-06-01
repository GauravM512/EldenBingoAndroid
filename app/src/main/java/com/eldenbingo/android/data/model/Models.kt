package com.eldenbingo.android.data.model

import java.util.UUID

// ---- Enums ----

enum class MatchStatus {
    NotRunning, Starting, Preparation, Running, Finished
}

enum class MapInstance {
    MainMap, DLC
}

enum class EldenRingClasses {
    Vagabond, Warrior, Hero, Bandit, Astrologer, Prophet, Samurai, Prisoner, Confessor, Wretch
}

enum class PacketType {
    ServerRegisterAccepted,
    ServerRegisterDenied,
    ServerClientDropped,
    ServerShutdown,
    ClientRegister,
    ClientDisconnect,
    ObjectData,
    KeepAlive
}

// ---- Data Classes ----

data class UserInRoom(
    val nick: String,
    val guid: UUID,
    val isAdmin: Boolean,
    val team: Int
) {
    val isSpectator: Boolean get() = team == -1

    override fun toString(): String {
        val suffixes = mutableListOf<String>()
        if (isAdmin) suffixes.add("Admin")
        if (isSpectator) suffixes.add("Spectator")
        return nick + if (suffixes.isNotEmpty()) " [${suffixes.joinToString(", ")}]" else ""
    }
}

data class BingoBoardSquare(
    var text: String,
    var tooltip: String,
    var team: IntArray,
    var marked: Boolean,
    var counters: List<SquareCounter>
) {
    fun isChecked(teamIndex: Int): Boolean = team.contains(teamIndex)
}

data class SquareCounter(
    val team: Int,
    val counter: Int
)

data class BingoBoard(
    val size: Int,
    val lockout: Boolean,
    val squares: List<BingoBoardSquare>,
    val availableClasses: List<EldenRingClasses>
)

data class BingoGameSettings(
    val boardSize: Int = 5,
    val lockout: Boolean = false,
    val randomClasses: Boolean = true,
    val validClasses: Set<EldenRingClasses> = EldenRingClasses.entries.toSet(),
    val numberOfClasses: Int = 2,
    val categoryLimit: Int = 2,
    val randomSeed: Int = 0,
    val preparationTime: Int = 0,
    val pointsPerBingoLine: Int = 1
)

data class TeamScore(
    val team: Int,
    val name: String,
    val score: Int
)

data class BingoLine(
    val team: Int,
    val name: String,
    val type: Int,
    val bingoIndex: Int
)

data class MapCoordinates(
    val x: Float,
    val y: Float,
    val angle: Float,
    val isUnderground: Boolean,
    val mapInstance: MapInstance
)

data class RoomState(
    val name: String = "",
    val users: List<UserInRoom> = emptyList(),
    val matchStatus: MatchStatus = MatchStatus.NotRunning,
    val paused: Boolean = false,
    val timer: Int = 0
)

data class ChatMessage(
    val userGuid: UUID,
    val userName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val team: Int = -1
)

data class PlayerPosition(
    val userGuid: UUID,
    val nick: String,
    val x: Float,
    val y: Float,
    val angle: Float,
    val isUnderground: Boolean,
    val mapInstance: MapInstance,
    val team: Int
)

// ---- Team Colors ----

data class TeamColor(val color: Long, val name: String)

object BingoConstants {
    const val DEFAULT_PORT = 4501
    const val BOARD_SIZE_MIN = 3
    const val BOARD_SIZE_MAX = 11

    val TEAM_COLORS = listOf(
        TeamColor(0xFFBE1210, "Red"),
        TeamColor(0xFF095CA8, "Blue"),
        TeamColor(0xFF059510, "Green"),
        TeamColor(0xFFCD8004, "Orange"),
        TeamColor(0xFF8723D0, "Purple"),
        TeamColor(0xFF4ECCCC, "Cyan"),
        TeamColor(0xFFED73D8, "Pink"),
        TeamColor(0xFF835016, "Brown"),
        TeamColor(0xFFD7C300, "Yellow")
    )

    fun getTeamColor(team: Int): Long {
        return if (team in TEAM_COLORS.indices) TEAM_COLORS[team].color else 0xFFB0B0B0
    }

    fun getTeamName(team: Int): String {
        return if (team == -1) "Spectator"
        else if (team in TEAM_COLORS.indices) TEAM_COLORS[team].name + " Team"
        else ""
    }

    fun getTeamColorCompose(team: Int): androidx.compose.ui.graphics.Color {
        val colorLong = getTeamColor(team)
        return androidx.compose.ui.graphics.Color(colorLong)
    }
}
