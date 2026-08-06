package com.eldenbingo.android.data.model

import java.util.UUID

// ---- Server -> Client Packets ----

data class ServerRoomNameSuggestion(val roomName: String)

data class ServerCreateRoomDenied(val reason: String)

data class ServerJoinRoomAccepted(
    val roomName: String,
    val users: List<UserInRoom>,
    val matchStatus: MatchStatus,
    val paused: Boolean,
    val timer: Int
)

data class ServerJoinRoomDenied(val reason: String)

data class ServerUserJoinedRoom(val user: UserInRoom)

data class ServerUserLeftRoom(val user: UserInRoom)

data class ServerUserCoordinates(
    val userGuid: UUID,
    val x: Float,
    val y: Float,
    val angle: Float,
    val isUnderground: Boolean,
    val mapInstance: MapInstance
)

data class ServerAdminStatusMessage(val message: String, val color: Int)

data class ServerUserChat(val userGuid: UUID, val message: String)

data class ServerMatchStatusUpdate(
    val matchStatus: MatchStatus,
    val paused: Boolean,
    val timer: Int
)

data class ServerEntireBingoBoardUpdate(
    val size: Int,
    val lockout: Boolean,
    val squares: List<BingoBoardSquare>,
    val availableClasses: List<EldenRingClasses>
)

data class ServerScoreboardUpdate(val scoreboard: List<TeamScore>)

data class ServerBingoAchievedUpdate(val bingo: BingoLine)

data class ServerSquareUpdate(val square: BingoBoardSquare, val index: Int)

data class ServerUserChecked(val index: Int, val team: Int, val teamsChecked: IntArray)

data class ServerCurrentGameSettings(val gameSettings: BingoGameSettings)

data class ServerTeamNameChanged(val userGuid: UUID, val team: Int, val teamColorName: String, val name: String)

data class ServerBroadcastMessage(val message: String)

data class ServerUserChangedTeam(val userGuid: UUID, val team: Int, val teamColorName: String, val users: List<UserInRoom>)

data class ServerUserBannedFromRoom(val user: UserInRoom, val banner: UserInRoom)

data class ServerPromoteToAdmin(val user: UserInRoom, val promoter: UserInRoom)

data class ServerMatchEvents(val events: List<MatchEvent>)

data class ServerEntireMatchLogReceived(val matchLog: String, val suggestedFilename: String, val json: Boolean)

// ---- Client -> Server Packets ----

data class ClientRequestRoomName(val dummy: Unit = Unit)

data class ClientRequestCreateRoom(
    val roomName: String,
    val adminPass: String,
    val nick: String,
    val team: Int,
    val settings: BingoGameSettings
)

data class ClientRequestJoinRoom(
    val roomName: String,
    val adminPass: String,
    val nick: String,
    val team: Int
)

data class ClientRequestLeaveRoom(val dummy: Unit = Unit)

data class ClientCoordinates(
    val x: Float,
    val y: Float,
    val angle: Float,
    val isUnderground: Boolean,
    val mapInstance: MapInstance
)

data class ClientChat(val message: String)

data class ClientBingoJson(val json: String)

data class ClientRandomizeBoard(val dummy: Unit = Unit)

data class ClientChangeMatchStatus(val matchStatus: MatchStatus)

data class ClientTogglePause(val dummy: Unit = Unit)

data class ClientTryCheck(val index: Int, val forUser: UUID)

data class ClientTryMark(val index: Int)

data class ClientTrySetCounter(val index: Int, val change: Int, val forUser: UUID)

data class ClientSetGameSettings(val gameSettings: BingoGameSettings)

data class ClientRequestCurrentGameSettings(val dummy: Unit = Unit)

data class ClientSetTeamName(val team: Int, val name: String)

data class ClientRequestTeamChange(val team: Int)

data class ClientBanUserFromRoom(val bannedUser: UUID)

data class ClientPromoteToAdmin(val promotedUser: UUID)

data class ClientRequestEntireMatchLog(val json: Boolean)

// ---- Neto Protocol Packets ----

data class ClientRegister(
    val registerString: String = "hello",
    val version: String,
    val identityToken: String
)

data class ServerRegisterAccepted(
    val message: String,
    val clientGuid: UUID
)

data class ServerRegisterDenied(val message: String)

data class ServerKicked(val reason: String)

data class KeepAlive(val dummy: Unit = Unit)
