package com.eldenbingo.android.data.network

import com.eldenbingo.android.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.Value
import net.jpountz.lz4.LZ4Factory
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.UUID

/**
 * TCP client that speaks the EldenBingo/Neto protocol.
 *
 * Wire format: [4-byte little-endian length][MessagePack payload]
 *
 * Packet MessagePack format (from PacketFormatter.cs):
 *   Array(2 + objectCount * 2):
 *     [0] byte: packetType (PacketTypes enum)
 *     [1] int32: objectCount
 *     For each object:
 *       [2i]   string: full type name (e.g. "Neto.Shared.ClientRegister" or "ServerUserChat")
 *       [2i+1] map:    contractless-serialized object
 */
class EldenBingoClient {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private val writeMutex = Mutex()

    var clientGuid: UUID? = null
        private set
    var isConnected: Boolean = false
        private set
    var localUser: UserInRoom? = null
        private set

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _roomState = MutableStateFlow(RoomState())
    val roomState: StateFlow<RoomState> = _roomState.asStateFlow()

    private val _bingoBoard = MutableStateFlow<BingoBoard?>(null)
    val bingoBoard: StateFlow<BingoBoard?> = _bingoBoard.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    fun addSystemChatMessage(message: String) {
        val chatMsg = ChatMessage(
            userGuid = UUID(0, 0),
            userName = "[SYSTEM]",
            message = message,
            team = -1
        )
        _chatMessages.value = _chatMessages.value + chatMsg
    }

    private val _playerPositions = MutableStateFlow<Map<UUID, PlayerPosition>>(emptyMap())
    val playerPositions: StateFlow<Map<UUID, PlayerPosition>> = _playerPositions.asStateFlow()

    private val _scoreboard = MutableStateFlow<List<TeamScore>>(emptyList())
    val scoreboard: StateFlow<List<TeamScore>> = _scoreboard.asStateFlow()

    private val _bingoLines = MutableStateFlow<List<BingoLine>>(emptyList())
    val bingoLines: StateFlow<List<BingoLine>> = _bingoLines.asStateFlow()

    private val _matchEvents = MutableStateFlow<List<MatchEvent>>(emptyList())
    val matchEvents: StateFlow<List<MatchEvent>> = _matchEvents.asStateFlow()

    private val _matchLog = MutableStateFlow<String?>(null)
    val matchLog: StateFlow<String?> = _matchLog.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _soundEvents = MutableSharedFlow<GameSound>(extraBufferCapacity = 8)
    val soundEvents: SharedFlow<GameSound> = _soundEvents.asSharedFlow()

    private val _squareClaimEvents = MutableSharedFlow<SquareClaimEvent>(extraBufferCapacity = 8)
    val squareClaimEvents: SharedFlow<SquareClaimEvent> = _squareClaimEvents.asSharedFlow()

    private val _gameSettings = MutableStateFlow<BingoGameSettings?>(null)
    val gameSettings: StateFlow<BingoGameSettings?> = _gameSettings.asStateFlow()

    private val _roomNameSuggestion = MutableStateFlow<String?>(null)
    val roomNameSuggestion: StateFlow<String?> = _roomNameSuggestion.asStateFlow()

    private var identityToken: String = ""
    private var cancelJob: Job? = null
    private var keepAliveJob: Job? = null
    private var timerJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val VERSION = "0.18.0"
        private const val SERVER_REGISTER_STRING = "neto server"
        private const val CLIENT_REGISTER_STRING = "hello"
        private const val KEEP_ALIVE_TIMEOUT_MS = 15000L
        private const val KEEP_ALIVE_INTERVAL_MS = 3000L
        private const val LZ4_BLOCK_ARRAY_TYPE = 98
        private const val LZ4_BLOCK_TYPE = 99
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    // ---- Connection ----

    fun connect(address: String, port: Int, token: String = "") {
        if (isConnected) {
            _statusMessage.value = "Already connected"
            return
        }
        identityToken = token
        _connectionState.value = ConnectionState.Connecting
        scope.launch {
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(address, port), 10000)
                socket = sock
                inputStream = sock.getInputStream()
                outputStream = sock.getOutputStream()
                isConnected = true
                _statusMessage.value = "Connected to $address:$port, registering..."

                // Send ClientRegister
                sendClientRegister()

                // Start reading packets
                readJob = launch { readPacketsLoop() }

                // Start keep-alive timer
                resetKeepAlive()

                // Start local timer ticking
                startTimerTicking()
            } catch (e: Exception) {
                isConnected = false
                _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
                cleanup()
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                sendPacket(PacketType.ClientDisconnect)
            } catch (_: Exception) {}
            isConnected = false
            if (_connectionState.value !is ConnectionState.Error) {
                _connectionState.value = ConnectionState.Disconnected
            }
            cleanup()
        }
    }

    private fun cleanup() {
        readJob?.cancel()
        keepAliveJob?.cancel()
        timerJob?.cancel()
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        _roomState.value = RoomState()
        _bingoBoard.value = null
        _bingoLines.value = emptyList()
        localUser = null
    }

    private var lastKeepAliveReceived = 0L

    private fun resetKeepAlive() {
        lastKeepAliveReceived = System.currentTimeMillis()
        if (keepAliveJob == null || !keepAliveJob!!.isActive) {
            keepAliveJob = scope.launch {
                while (isActive() && isConnected) {
                    delay(KEEP_ALIVE_INTERVAL_MS)
                    if (System.currentTimeMillis() - lastKeepAliveReceived > KEEP_ALIVE_TIMEOUT_MS) {
                        _statusMessage.value = "Keep-alive timeout"
                        disconnect()
                        break
                    }
                    // Send keep-alive to server
                    sendPacket(PacketType.KeepAlive)
                }
            }
        }
    }

    private fun startTimerTicking() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (isActive() && isConnected) {
                delay(1000)
                val current = _roomState.value
                if (current.matchStatus != MatchStatus.NotRunning &&
                    current.matchStatus != MatchStatus.Finished &&
                    !current.paused) {
                    _roomState.value = current.copy(timer = current.timer + 1000)
                }
            }
        }
    }

    // ---- Packet Reading ----

    private suspend fun readPacketsLoop() {
        val stream = inputStream ?: return
        try {
            while (isActive() && socket?.isConnected == true) {
                // Read 4-byte length prefix
                val lengthBytes = ByteArray(4)
                var totalRead = 0
                while (totalRead < 4) {
                    val read = withContext(Dispatchers.IO) {
                        stream.read(lengthBytes, totalRead, 4 - totalRead)
                    }
                    if (read <= 0) {
                        disconnect()
                        return
                    }
                    totalRead += read
                }
                val length = ByteBuffer.wrap(lengthBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                if (length <= 0 || length > 10_000_000) {
                    _statusMessage.value = "Invalid packet length: $length"
                    disconnect()
                    return
                }

                // Read payload
                val payload = ByteArray(length)
                totalRead = 0
                while (totalRead < length) {
                    val read = withContext(Dispatchers.IO) {
                        stream.read(payload, totalRead, length - totalRead)
                    }
                    if (read <= 0) {
                        disconnect()
                        return
                    }
                    totalRead += read
                }

                // Process packet
                processPacket(payload)
            }
        } catch (_: CancellationException) {
            // Normal disconnect
        } catch (e: Exception) {
            if (isActive()) {
                _statusMessage.value = "Read error: ${e.message}"
                disconnect()
            }
        }
    }

    private fun isActive(): Boolean = isConnected && socket?.isConnected == true

    private fun processPacket(payload: ByteArray) {
        try {
            val decompressed = decompressIfNeeded(payload)
            val unpacker = MessagePack.newDefaultUnpacker(decompressed)
            val arrayLen = unpacker.unpackArrayHeader()
            if (arrayLen < 2) return

            val packetTypeOrdinal = unpacker.unpackInt()
            val packetType = PacketType.entries.getOrNull(packetTypeOrdinal) ?: return
            val objectCount = unpacker.unpackInt()

            when (packetType) {
                PacketType.ServerRegisterAccepted -> {
                    handleRegisterAccepted(unpacker, objectCount)
                }
                PacketType.ServerRegisterDenied -> {
                    handleRegisterDenied(unpacker, objectCount)
                    disconnect()
                }
                PacketType.ServerClientDropped -> {
                    _statusMessage.value = "Kicked from server"
                    disconnect()
                }
                PacketType.ServerShutdown -> {
                    _statusMessage.value = "Server shutting down"
                    disconnect()
                }
                PacketType.KeepAlive -> {
                    resetKeepAlive()
                }
                PacketType.ObjectData -> {
                    handleObjectData(unpacker, objectCount)
                }
                else -> {}
            }
            unpacker.close()
        } catch (e: Exception) {
            _statusMessage.value = "Packet error: ${e.message}"
        }
    }

    private fun decompressIfNeeded(payload: ByteArray): ByteArray {
        if (payload.isEmpty()) return payload

        return try {
            when {
                isMessagePackExtension(payload) -> decompressLz4Block(payload) ?: payload
                isMessagePackArray(payload) -> decompressLz4BlockArray(payload) ?: payload
                else -> payload
            }
        } catch (e: Exception) {
            val hex = if (payload.size > 32) 
                payload.take(32).toByteArray().toHex() + "..." 
            else payload.toHex()
            _statusMessage.value = "Decompression failed: ${e.message} (Payload: $hex)"
            payload
        }
    }

    private fun decompressLz4Block(payload: ByteArray): ByteArray? {
        val ext = readExtension(payload, 0) ?: return null
        if (ext.type != LZ4_BLOCK_TYPE) return null

        val extData = payload.copyOfRange(ext.dataStart, ext.dataStart + ext.dataLength)
        val firstByte = extData[0].toInt() and 0xFF
        
        val uncompressedLength: Int
        val compressedStartInExtData: Int
        
        try {
            if (isMessagePackInteger(firstByte)) {
                val unpacker = MessagePack.newDefaultUnpacker(extData)
                uncompressedLength = unpacker.unpackInt()
                compressedStartInExtData = unpacker.totalReadBytes.toInt()
            } else {
                // Fallback for raw Little Endian length (e.g. when first byte is 0xC7)
                uncompressedLength = readInt32LE(extData, 0)
                compressedStartInExtData = 4
            }
        } catch (e: Exception) {
            throw Exception("Header parse failed at byte 0x%02x: ${e.message}".format(firstByte))
        }

        val compressed = extData.copyOfRange(compressedStartInExtData, extData.size)
        return decompressBlock(compressed, uncompressedLength)
    }

    private fun decompressLz4BlockArray(payload: ByteArray): ByteArray? {
        val arrayHeader = readArrayHeader(payload, 0) ?: return null
        if (arrayHeader.first < 2 || arrayHeader.first % 2 != 0) return null
        var offset = arrayHeader.second

        val output = ByteArrayOutputStream()
        repeat(arrayHeader.first / 2) {
            val ext = readExtension(payload, offset) ?: return null
            if (ext.type != LZ4_BLOCK_ARRAY_TYPE) return null
            offset = ext.dataStart + ext.dataLength

            val lengthPayload = payload.copyOfRange(ext.dataStart, ext.dataStart + ext.dataLength)
            val firstByte = lengthPayload[0].toInt() and 0xFF
            val uncompressedLength = try {
                if (isMessagePackInteger(firstByte)) {
                    MessagePack.newDefaultUnpacker(lengthPayload).use { it.unpackInt() }
                } else {
                    readInt32LE(lengthPayload, 0)
                }
            } catch (e: Exception) {
                throw Exception("Array segment header parse failed at byte 0x%02x: ${e.message}".format(firstByte))
            }

            val bin = readBin(payload, offset) ?: return null
            offset = bin.dataStart + bin.dataLength
            output.write(decompressBlock(payload, bin.dataStart, bin.dataLength, uncompressedLength))
        }
        return output.toByteArray()
    }

    private fun decompressBlock(compressed: ByteArray, uncompressedLength: Int): ByteArray {
        return decompressBlock(compressed, 0, compressed.size, uncompressedLength)
    }

    private fun decompressBlock(payload: ByteArray, offset: Int, length: Int, uncompressedLength: Int): ByteArray {
        val decompressor = LZ4Factory.fastestInstance().fastDecompressor()
        val result = ByteArray(uncompressedLength)
        decompressor.decompress(payload, offset, result, 0, uncompressedLength)
        return result
    }

    private fun isMessagePackExtension(payload: ByteArray): Boolean {
        return when (payload[0].toInt() and 0xFF) {
            0xC7, 0xC8, 0xC9, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8 -> true
            else -> false
        }
    }

    private fun isMessagePackArray(payload: ByteArray): Boolean {
        val code = payload[0].toInt() and 0xFF
        return code in 0x90..0x9F || code == 0xDC || code == 0xDD
    }

    private data class Segment(val dataStart: Int, val dataLength: Int)
    private data class ExtensionSegment(val type: Int, val dataStart: Int, val dataLength: Int)

    private fun readArrayHeader(payload: ByteArray, offset: Int): Pair<Int, Int>? {
        if (offset >= payload.size) return null
        return when (val code = payload[offset].toInt() and 0xFF) {
            in 0x90..0x9F -> (code and 0x0F) to offset + 1
            0xDC if offset + 2 < payload.size -> readUInt16(payload, offset + 1) to offset + 3
            0xDD if offset + 4 < payload.size -> readInt32(payload, offset + 1) to offset + 5
            else -> null
        }
    }

    private fun readExtension(payload: ByteArray, offset: Int): ExtensionSegment? {
        if (offset >= payload.size) return null
        val code = payload[offset].toInt() and 0xFF
        val (length, typeOffset) = when (code) {
            0xD4 -> 1 to offset + 1
            0xD5 -> 2 to offset + 1
            0xD6 -> 4 to offset + 1
            0xD7 -> 8 to offset + 1
            0xD8 -> 16 to offset + 1
            0xC7 -> {
                if (offset + 2 >= payload.size) return null
                (payload[offset + 1].toInt() and 0xFF) to offset + 2
            }
            0xC8 -> {
                if (offset + 3 >= payload.size) return null
                readUInt16(payload, offset + 1) to offset + 3
            }
            0xC9 -> {
                if (offset + 5 >= payload.size) return null
                readInt32(payload, offset + 1) to offset + 5
            }
            else -> return null
        }
        val dataStart = typeOffset + 1
        if (dataStart + length > payload.size) return null
        val type = payload[typeOffset].toInt() and 0xFF
        return ExtensionSegment(type, dataStart, length)
    }

    private fun readBin(payload: ByteArray, offset: Int): Segment? {
        if (offset >= payload.size) return null
        val code = payload[offset].toInt() and 0xFF
        val (length, dataStart) = when (code) {
            0xC4 -> {
                if (offset + 1 >= payload.size) return null
                (payload[offset + 1].toInt() and 0xFF) to offset + 2
            }
            0xC5 -> {
                if (offset + 2 >= payload.size) return null
                readUInt16(payload, offset + 1) to offset + 3
            }
            0xC6 -> {
                if (offset + 4 >= payload.size) return null
                readInt32(payload, offset + 1) to offset + 5
            }
            else -> return null
        }
        if (dataStart + length > payload.size) return null
        return Segment(dataStart, length)
    }

    private fun readUInt16(payload: ByteArray, offset: Int): Int {
        return ((payload[offset].toInt() and 0xFF) shl 8) or
                (payload[offset + 1].toInt() and 0xFF)
    }

    private fun readInt32(payload: ByteArray, offset: Int): Int {
        return ((payload[offset].toInt() and 0xFF) shl 24) or
                ((payload[offset + 1].toInt() and 0xFF) shl 16) or
                ((payload[offset + 2].toInt() and 0xFF) shl 8) or
                (payload[offset + 3].toInt() and 0xFF)
    }

    private fun readInt32LE(payload: ByteArray, offset: Int): Int {
        return (payload[offset].toInt() and 0xFF) or
                ((payload[offset + 1].toInt() and 0xFF) shl 8) or
                ((payload[offset + 2].toInt() and 0xFF) shl 16) or
                ((payload[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun isMessagePackInteger(code: Int): Boolean {
        return code in 0x00..0x7F || code in 0xE0..0xFF || // fixint
                code in 0xCC..0xCF || // uint 8, 16, 32, 64
                code in 0xD0..0xD3    // int 8, 16, 32, 64
    }

    private fun handleRegisterAccepted(unpacker: MessageUnpacker, objectCount: Int) {
        if (objectCount < 1) return
        unpacker.unpackString()
        val map = unpackObjectMap(unpacker)
        val message = map["Message"]?.asStringValue()?.asString()
        val guidValue = map["ClientGuid"]

        if (message == SERVER_REGISTER_STRING) {
            clientGuid = parseGuid(guidValue)
            _connectionState.value = ConnectionState.Connected
        } else {
            _connectionState.value = ConnectionState.Error("Invalid server response")
            disconnect()
        }
        resetKeepAlive()
    }

    private fun handleRegisterDenied(unpacker: MessageUnpacker, objectCount: Int) {
        val message = try {
            if (objectCount < 1) {
                null
            } else {
                unpacker.unpackString()
                unpackObjectMap(unpacker)["Message"]?.asStringValue()?.asString()
            }
        } catch (_: Exception) {
            null
        }
        _connectionState.value = ConnectionState.Error("Registration denied: ${message ?: "Unknown reason"}")
    }

    private fun handleObjectData(unpacker: MessageUnpacker, objectCount: Int) {
        for (i in 0 until objectCount) {
            try {
                val typeName = unpacker.unpackString()
                dispatchObject(typeName, unpackObjectMap(unpacker))
            } catch (_: Exception) {
                // Skip this object
            }
        }
    }

    private fun unpackObjectMap(unpacker: MessageUnpacker): Map<String, Value> {
        val value = unpacker.unpackValue()
        val packer = MessagePack.newDefaultBufferPacker()
        value.writeTo(packer)
        val bytes = packer.toByteArray()
        val decompressed = decompressIfNeeded(bytes)
        val objectValue = MessagePack.newDefaultUnpacker(decompressed).use { objectUnpacker ->
            objectUnpacker.unpackValue()
        }
        return objectValue.asMapValue().map().mapKeys { it.key.asStringValue().asString() }
    }

    // ---- Object Dispatch ----

    private fun dispatchObject(typeName: String, strMap: Map<String, Value>) {
        when {
            typeName.contains("ServerJoinRoomAccepted") -> handleJoinRoomAccepted(strMap)
            typeName.contains("ServerJoinRoomDenied") -> handleJoinRoomDenied(strMap)
            typeName.contains("ServerCreateRoomDenied") -> handleCreateRoomDenied(strMap)
            typeName.contains("ServerUserJoinedRoom") -> handleUserJoinedRoom(strMap)
            typeName.contains("ServerUserLeftRoom") -> handleUserLeftRoom(strMap)
            typeName.contains("ServerUserChat") -> handleUserChat(strMap)
            typeName.contains("ServerMatchStatusUpdate") -> handleMatchStatusUpdate(strMap)
            typeName.contains("ServerEntireBingoBoardUpdate") -> handleEntireBingoBoardUpdate(strMap)
            typeName.contains("ServerSquareUpdate") -> handleSquareUpdate(strMap)
            typeName.contains("ServerUserChecked") -> handleUserChecked(strMap)
            typeName.contains("ServerScoreboardUpdate") -> handleScoreboardUpdate(strMap)
            typeName.contains("ServerBingoAchievedUpdate") -> handleBingoAchieved(strMap)
            typeName.contains("ServerUserCoordinates") -> handleUserCoordinates(strMap)
            typeName.contains("ServerCurrentGameSettings") -> handleCurrentGameSettings(strMap)
            typeName.contains("ServerTeamNameChanged") -> {}
            typeName.contains("ServerBroadcastMessage") -> handleBroadcastMessage(strMap)
            typeName.contains("ServerUserChangedTeam") -> handleUserChangedTeam(strMap)
            typeName.contains("ServerUserBannedFromRoom") -> handleUserBanned(strMap)
            typeName.contains("ServerPromoteToAdmin") -> handlePromoteToAdmin(strMap)
            typeName.contains("ServerRoomNameSuggestion") -> handleRoomNameSuggestion(strMap)
            typeName.contains("ServerAdminStatusMessage") -> handleAdminStatusMessage(strMap)
            typeName.contains("ServerMatchEvents") -> handleMatchEvents(strMap)
            typeName.contains("ServerEntireMatchLogReceived") -> handleEntireMatchLogReceived(strMap)
        }
    }

    private fun handleJoinRoomAccepted(map: Map<String, Value>) {
        val roomName = map["RoomName"]?.asStringValue()?.asString() ?: return
        val matchStatusStr = map["MatchStatus"]?.asIntegerValue()?.asInt() ?: 0
        val paused = map["Paused"]?.asBooleanValue()?.boolean ?: false
        val timer = map["Timer"]?.asIntegerValue()?.asInt() ?: 0

        val users = parseUsersFromMap(map)

        val matchStatus = MatchStatus.entries.getOrNull(matchStatusStr) ?: MatchStatus.NotRunning
        _roomState.value = RoomState(roomName, users, matchStatus, paused, timer)

        localUser = users.find { it.guid == clientGuid }
        _statusMessage.value = "Joined lobby: $roomName"
    }

    private fun handleJoinRoomDenied(map: Map<String, Value>) {
        val reason = map["Reason"]?.asStringValue()?.asString() ?: "Unknown"
        _statusMessage.value = "Join denied: $reason"
    }

    private fun handleCreateRoomDenied(map: Map<String, Value>) {
        val reason = map["Reason"]?.asStringValue()?.asString() ?: "Unknown"
        _statusMessage.value = "Create denied: $reason"
    }

    private fun handleUserJoinedRoom(map: Map<String, Value>) {
        val userMap = map["User"]?.asMapValue()?.map() ?: return
        val user = parseUserFromMap(userMap.mapKeys { it.key.asStringValue().asString() }) ?: return
        val currentUsers = _roomState.value.users.toMutableList()
        currentUsers.add(user)
        _roomState.value = _roomState.value.copy(users = currentUsers)
    }

    private fun handleUserLeftRoom(map: Map<String, Value>) {
        val userMap = map["User"]?.asMapValue()?.map() ?: return
        val user = parseUserFromMap(userMap.mapKeys { it.key.asStringValue().asString() }) ?: return
        val currentUsers = _roomState.value.users.filter { it.guid != user.guid }
        _roomState.value = _roomState.value.copy(users = currentUsers)
    }

    private fun handleUserChat(map: Map<String, Value>) {
        val userGuidValue = map["UserGuid"]
        val message = map["Message"]?.asStringValue()?.asString() ?: return
        val userGuid = parseGuid(userGuidValue) ?: return

        val user = _roomState.value.users.find { it.guid == userGuid }
        val chatMsg = ChatMessage(
            userGuid = userGuid,
            userName = user?.nick ?: "Unknown",
            message = message,
            team = user?.team ?: -1
        )
        _chatMessages.value = _chatMessages.value + chatMsg
    }

    private fun handleMatchStatusUpdate(map: Map<String, Value>) {
        val previousStatus = _roomState.value.matchStatus
        val matchStatusStr = map["MatchStatus"]?.asIntegerValue()?.asInt() ?: 0
        val paused = map["Paused"]?.asBooleanValue()?.boolean ?: false
        val timer = map["Timer"]?.asIntegerValue()?.asInt() ?: 0
        val matchStatus = MatchStatus.entries.getOrNull(matchStatusStr) ?: MatchStatus.NotRunning

        // Clear lines when match starts (transition from NotRunning/Finished to Starting/Preparation)
        if ((matchStatus == MatchStatus.Starting || matchStatus == MatchStatus.Preparation) &&
            (previousStatus == MatchStatus.NotRunning || previousStatus == MatchStatus.Finished)) {
            _bingoLines.value = emptyList()
            _matchEvents.value = emptyList()
        }

        _roomState.value = _roomState.value.copy(matchStatus = matchStatus, paused = paused, timer = timer)
    }

    private fun handleEntireBingoBoardUpdate(map: Map<String, Value>) {
        val size = map["Size"]?.asIntegerValue()?.asInt() ?: return
        val lockout = map["Lockout"]?.asBooleanValue()?.boolean ?: false
        val squaresArr = map["Squares"]?.asArrayValue()?.list() ?: return
        val classesArr = map["AvailableClasses"]?.asArrayValue()?.list() ?: emptyList()

        val squares = squaresArr.mapNotNull { sqVal ->
            try {
                val sqMap = sqVal.asMapValue().map().mapKeys { it.key.asStringValue().asString() }
                val text = sqMap["Text"]?.asStringValue()?.asString() ?: ""
                val tooltip = sqMap["Tooltip"]?.asStringValue()?.asString() ?: ""
                val team = sqMap["Team"]?.asArrayValue()?.list()?.map { it.asIntegerValue().asInt() }?.toIntArray() ?: intArrayOf()
                val marked = sqMap["Marked"]?.asBooleanValue()?.boolean ?: false
                val counters = sqMap["Counters"]?.asArrayValue()?.list()?.mapNotNull { cVal ->
                    try {
                        val cMap = cVal.asMapValue().map().mapKeys { it.key.asStringValue().asString() }
                        SquareCounter(cMap["Team"]?.asIntegerValue()?.asInt() ?: 0, cMap["Counter"]?.asIntegerValue()?.asInt() ?: 0)
                    } catch (_: Exception) { null }
                } ?: emptyList()
                BingoBoardSquare(text, tooltip, team, marked, counters)
            } catch (_: Exception) { null }
        }

        val classes = classesArr.mapNotNull { cVal ->
            try { EldenRingClasses.entries.getOrNull(cVal.asIntegerValue().asInt()) }
            catch (_: Exception) { null }
        }

        if (squares.size == size * size) {
            _bingoBoard.value = BingoBoard(size, lockout, squares, classes)
        }
    }

    private fun handleSquareUpdate(map: Map<String, Value>) {
        val index = map["Index"]?.asIntegerValue()?.asInt() ?: return
        val sqMap = map["Square"]?.asMapValue()?.map()?.mapKeys { it.key.asStringValue().asString() } ?: return
        val text = sqMap["Text"]?.asStringValue()?.asString() ?: ""
        val tooltip = sqMap["Tooltip"]?.asStringValue()?.asString() ?: ""
        val team = sqMap["Team"]?.asArrayValue()?.list()?.map { it.asIntegerValue().asInt() }?.toIntArray() ?: intArrayOf()
        val marked = sqMap["Marked"]?.asBooleanValue()?.boolean ?: false
        val counters = sqMap["Counters"]?.asArrayValue()?.list()?.mapNotNull { cVal ->
            try {
                val cMap = cVal.asMapValue().map().mapKeys { it.key.asStringValue().asString() }
                SquareCounter(cMap["Team"]?.asIntegerValue()?.asInt() ?: 0, cMap["Counter"]?.asIntegerValue()?.asInt() ?: 0)
            } catch (_: Exception) { null }
        } ?: emptyList()

        val updatedSquare = BingoBoardSquare(text, tooltip, team, marked, counters)
        val board = _bingoBoard.value ?: return
        if (index in board.squares.indices) {
            val newSquares = board.squares.toMutableList()
            newSquares[index] = updatedSquare
            _bingoBoard.value = board.copy(squares = newSquares)
        }
    }

    private fun handleUserChecked(map: Map<String, Value>) {
        val index = map["Index"]?.asIntegerValue()?.asInt() ?: return
        val team = map["Team"]?.asIntegerValue()?.asInt() ?: return
        val teamsChecked = map["TeamsChecked"]?.asArrayValue()?.list()?.map { it.asIntegerValue().asInt() }?.toIntArray() ?: return

        val isClaimed = teamsChecked.contains(team)
        val isOwnTeam = team == localUser?.team
        _soundEvents.tryEmit(
            when {
                isClaimed && isOwnTeam -> GameSound.SquareClaimedOwn
                isClaimed -> GameSound.SquareClaimedOther
                isOwnTeam -> GameSound.SquareUnclaimedOwn
                else -> GameSound.SquareUnclaimedOther
            }
        )

        val board = _bingoBoard.value ?: return
        if (index in board.squares.indices) {
            val newSquares = board.squares.toMutableList()
            newSquares[index] = newSquares[index].copy(team = teamsChecked)
            _bingoBoard.value = board.copy(squares = newSquares)
        }
    }

    private fun handleScoreboardUpdate(map: Map<String, Value>) {
        val scoresArr = map["Scoreboard"]?.asArrayValue()?.list() ?: return
        val scores = scoresArr.mapNotNull { sVal ->
            try {
                val sMap = sVal.asMapValue().map().mapKeys { it.key.asStringValue().asString() }
                TeamScore(
                    team = sMap["Team"]?.asIntegerValue()?.asInt() ?: 0,
                    name = sMap["Name"]?.asStringValue()?.asString() ?: "",
                    score = sMap["Score"]?.asIntegerValue()?.asInt() ?: 0
                )
            } catch (_: Exception) { null }
        }
        _scoreboard.value = scores
    }

    private fun handleBingoAchieved(map: Map<String, Value>) {
        val bingoMap = map["Bingo"]?.asMapValue()?.map()?.mapKeys { it.key.asStringValue().asString() } ?: return
        val timerStr = formatTimerValue(_roomState.value.timer)
        val type = bingoMap["Type"]?.asIntegerValue()?.asInt() ?: 0
        val bingoIndex = bingoMap["BingoIndex"]?.asIntegerValue()?.asInt() ?: 0
        val teamName = bingoMap["Name"]?.asStringValue()?.asString() ?: ""

        val typeStr = when (type) {
            0 -> "column ${bingoIndex + 1}"
            1 -> "row ${bingoIndex + 1}"
            2 -> "diagonal TL->BR"
            3 -> "diagonal BL->TR"
            else -> "unknown"
        }

        val line = BingoLine(
            team = bingoMap["Team"]?.asIntegerValue()?.asInt() ?: 0,
            name = teamName,
            type = type,
            bingoIndex = bingoIndex,
            timerStr = timerStr
        )
        _bingoLines.value = _bingoLines.value + line

        addSystemChatMessage("[$timerStr] $teamName BINGO on $typeStr!")
        _soundEvents.tryEmit(GameSound.Bingo)
    }

    private fun formatTimerValue(timerMs: Int): String {
        val totalSeconds = kotlin.math.abs(timerMs / 1000)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun handleUserCoordinates(map: Map<String, Value>) {
        val guidValue = map["UserGuid"]
        val userGuid = parseGuid(guidValue) ?: return
        val x = map["X"]?.asFloatValue()?.toFloat() ?: return
        val y = map["Y"]?.asFloatValue()?.toFloat() ?: return
        val angle = map["Angle"]?.asFloatValue()?.toFloat() ?: 0f
        val underground = map["IsUnderground"]?.asBooleanValue()?.boolean ?: false
        val mapInst = map["MapInstance"]?.asIntegerValue()?.asInt() ?: 0

        val user = _roomState.value.users.find { it.guid == userGuid }
        val pos = PlayerPosition(
            userGuid = userGuid,
            nick = user?.nick ?: "Unknown",
            x = x, y = y, angle = angle,
            isUnderground = underground,
            mapInstance = if (mapInst == 1) MapInstance.DLC else MapInstance.MainMap,
            team = user?.team ?: -1
        )
        val positions = _playerPositions.value.toMutableMap()
        positions[userGuid] = pos
        _playerPositions.value = positions
    }

    private fun handleCurrentGameSettings(map: Map<String, Value>) {
        val gsMap = map["GameSettings"]?.asMapValue()?.map()?.mapKeys { it.key.asStringValue().asString() } ?: return
        _gameSettings.value = parseGameSettings(gsMap)
    }

    private fun handleBroadcastMessage(map: Map<String, Value>) {
        val message = map["Message"]?.asStringValue()?.asString() ?: return
        val chatMsg = ChatMessage(
            userGuid = UUID(0, 0),
            userName = "[SERVER]",
            message = message,
            team = -1
        )
        _chatMessages.value = _chatMessages.value + chatMsg
    }

    private fun handleUserChangedTeam(map: Map<String, Value>) {
        val previousUsers = _roomState.value.users.associateBy { it.guid }
        val usersArr = map["Users"]?.asArrayValue()?.list() ?: return
        val users = usersArr.mapNotNull { uVal ->
            try {
                val uMap = uVal.asMapValue().map().mapKeys { it.key.asStringValue().asString() }
                parseUserFromMap(uMap)
            } catch (_: Exception) { null }
        }
        _roomState.value = _roomState.value.copy(users = users)
        localUser = users.find { it.guid == clientGuid }
    }

    private fun handleUserBanned(map: Map<String, Value>) {
        val userMap = map["User"]?.asMapValue()?.map()?.mapKeys { it.key.asStringValue().asString() } ?: return
        val user = parseUserFromMap(userMap) ?: return
        if (user.guid == clientGuid) {
            _roomState.value = RoomState()
            _bingoBoard.value = null
            _statusMessage.value = "Banned from lobby"
        }
    }

    private fun handlePromoteToAdmin(map: Map<String, Value>) {
        val userMap = map["User"]?.asMapValue()?.map()?.mapKeys { it.key.asStringValue().asString() } ?: return
        val user = parseUserFromMap(userMap) ?: return
        if (user.guid == clientGuid) {
            localUser = localUser?.copy(isAdmin = true)
        }
        val users = _roomState.value.users.map {
            if (it.guid == user.guid) it.copy(isAdmin = true) else it
        }
        _roomState.value = _roomState.value.copy(users = users)
    }

    private fun handleRoomNameSuggestion(map: Map<String, Value>) {
        val name = map["RoomName"]?.asStringValue()?.asString() ?: return
        _roomNameSuggestion.value = name
    }

    private fun handleAdminStatusMessage(map: Map<String, Value>) {
        val message = map["Message"]?.asStringValue()?.asString() ?: return
        _statusMessage.value = message
    }

    private fun handleMatchEvents(map: Map<String, Value>) {
        val eventsArr = map["Events"]?.asArrayValue()?.list() ?: return
        val newEvents = eventsArr.mapNotNull { parseMatchEvent(it) }
        _matchEvents.value = _matchEvents.value + newEvents

        // Log to chat for convenience
        newEvents.forEach { event ->
            val action = if (event.checked) "marked" else "unmarked"
            val board = _bingoBoard.value
            val squareText = if (event.squareIndex >= 0 && board != null && event.squareIndex < board.squares.size) {
                "'${board.squares[event.squareIndex].text}'"
            } else if (event.eventType == MatchEventType.Bingo) {
                "BINGO!"
            } else {
                "square ${event.squareIndex}"
            }

            val message = if (event.eventType == MatchEventType.Bingo) {
                "${event.player} achieved BINGO!"
            } else {
                "${event.player} $action $squareText"
            }
            addSystemChatMessage(message)

            // Emit claim event for UI notifications
            if (event.checked && (event.eventType == MatchEventType.PlayerCheck || event.eventType == MatchEventType.RefereeCheck)) {
                val claimNick = if (event.eventType == MatchEventType.RefereeCheck) {
                    "[REF] ${event.player}"
                } else {
                    event.player
                }
                _squareClaimEvents.tryEmit(SquareClaimEvent(claimNick, squareText.removeSurrounding("'"), event.team))
            }
        }
    }

    private fun parseMatchEvent(value: Value): MatchEvent? {
        return try {
            val map = value.asMapValue().map().mapKeys { it.key.asStringValue().asString() }
            MatchEvent(
                timestamp = map["Timestamp"]?.asIntegerValue()?.asInt() ?: 0,
                squareIndex = map["SquareIndex"]?.asIntegerValue()?.asInt() ?: 0,
                team = map["Team"]?.asIntegerValue()?.asInt() ?: -1,
                player = map["Player"]?.asStringValue()?.asString() ?: "",
                checked = map["Checked"]?.asBooleanValue()?.boolean ?: false,
                eventType = MatchEventType.entries.getOrNull(map["EventType"]?.asIntegerValue()?.asInt() ?: 0) ?: MatchEventType.PlayerCheck
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun handleEntireMatchLogReceived(map: Map<String, Value>) {
        val matchLog = map["MatchLog"]?.asStringValue()?.asString() ?: return
        _matchLog.value = matchLog
    }

    // ---- Client Requests ----

    fun requestMatchLog(json: Boolean = false) {
        scope.launch {
            sendObjectPacket(ClientRequestEntireMatchLog(json), PacketType.ObjectData)
        }
    }

    // ---- Packet Sending ----

    private suspend fun sendClientRegister() {
        val registerObj = ClientRegister(CLIENT_REGISTER_STRING, VERSION, identityToken)
        sendObjectPacket(registerObj, PacketType.ClientRegister)
    }

    suspend fun requestRoomName() {
        sendObjectPacket(ClientRequestRoomName(), PacketType.ObjectData)
    }

    suspend fun createRoom(roomName: String, adminPass: String, nick: String, team: Int, settings: BingoGameSettings) {
        sendObjectPacket(ClientRequestCreateRoom(roomName, adminPass, nick, team, settings), PacketType.ObjectData)
    }

    suspend fun joinRoom(roomName: String, adminPass: String, nick: String, team: Int) {
        sendObjectPacket(ClientRequestJoinRoom(roomName, adminPass, nick, team), PacketType.ObjectData)
    }

    suspend fun leaveRoom() {
        _roomState.value = RoomState()
        _bingoBoard.value = null
        sendObjectPacket(ClientRequestLeaveRoom(), PacketType.ObjectData)
    }

    suspend fun sendChat(message: String) {
        sendObjectPacket(ClientChat(message), PacketType.ObjectData)
    }

    suspend fun tryCheck(index: Int, forUser: UUID?) {
        sendObjectPacket(ClientTryCheck(index, forUser ?: clientGuid ?: return), PacketType.ObjectData)
    }

    suspend fun tryMark(index: Int) {
        sendObjectPacket(ClientTryMark(index), PacketType.ObjectData)
    }

    suspend fun trySetCounter(index: Int, change: Int) {
        sendObjectPacket(ClientTrySetCounter(index, change, localUser?.guid ?: return), PacketType.ObjectData)
    }

    suspend fun changeMatchStatus(status: MatchStatus) {
        sendObjectPacket(ClientChangeMatchStatus(status), PacketType.ObjectData)
    }

    suspend fun togglePause() {
        sendObjectPacket(ClientTogglePause(), PacketType.ObjectData)
    }

    suspend fun requestCurrentGameSettings() {
        sendObjectPacket(ClientRequestCurrentGameSettings(), PacketType.ObjectData)
    }

    suspend fun setGameSettings(settings: BingoGameSettings) {
        sendObjectPacket(ClientSetGameSettings(settings), PacketType.ObjectData)
    }

    suspend fun requestTeamChange(team: Int) {
        sendObjectPacket(ClientRequestTeamChange(team), PacketType.ObjectData)
    }

    suspend fun randomizeBoard() {
        sendObjectPacket(ClientRandomizeBoard(), PacketType.ObjectData)
    }

    suspend fun sendBingoJson(json: String) {
        sendObjectPacket(ClientBingoJson(json), PacketType.ObjectData)
    }

    // ---- Low-level Packet I/O ----

    private suspend fun sendPacket(packetType: PacketType) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    val os = outputStream ?: return@withLock
                    val packer = MessagePack.newDefaultBufferPacker()
                    packer.packArrayHeader(2)
                    packer.packInt(packetType.ordinal)
                    packer.packInt(0)
                    val payload = packer.toByteArray()
                    val lengthBuf = ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array()
                    os.write(lengthBuf)
                    os.write(payload)
                    os.flush()
                } catch (e: Exception) {
                    _statusMessage.value = sendErrorMessage(e)
                }
            }
        }
    }

    private suspend fun sendObjectPacket(obj: Any, packetType: PacketType = PacketType.ObjectData) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    val os = outputStream ?: return@withLock
                    val packer = MessagePack.newDefaultBufferPacker()

                    val typeName = packetTypeName(obj)

                    packer.packArrayHeader(2 + 2) // packetType + objectCount + (typeName + object)
                    packer.packInt(packetType.ordinal)
                    packer.packInt(1) // one object

                    // Type name
                    packer.packString(typeName)

                    // Object as contractless map
                    packObjectAsMap(packer, obj)

                    val payload = packer.toByteArray()
                    val lengthBuf = ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array()
                    os.write(lengthBuf)
                    os.write(payload)
                    os.flush()
                } catch (e: Exception) {
                    _statusMessage.value = sendErrorMessage(e)
                }
            }
        }
    }

    private fun sendErrorMessage(e: Exception): String {
        return "Send error: ${e.message ?: e::class.simpleName ?: e::class.java.simpleName}"
    }

    private fun packObjectAsMap(packer: org.msgpack.core.MessagePacker, obj: Any) {
        when (obj) {
            is ClientRegister -> {
                packer.packMapHeader(3)
                packer.packString("Message"); packValue(packer, obj.registerString)
                packer.packString("Version"); packValue(packer, obj.version)
                packer.packString("IdentityToken"); packValue(packer, obj.identityToken)
            }
            is ClientRequestRoomName -> {
                packer.packMapHeader(0)
            }
            is ClientRequestCreateRoom -> {
                packer.packMapHeader(5)
                packer.packString("RoomName"); packValue(packer, obj.roomName)
                packer.packString("AdminPass"); packValue(packer, obj.adminPass)
                packer.packString("Nick"); packValue(packer, obj.nick)
                packer.packString("Team"); packValue(packer, obj.team)
                packer.packString("Settings"); packGameSettings(packer, obj.settings)
            }
            is ClientRequestJoinRoom -> {
                packer.packMapHeader(4)
                packer.packString("RoomName"); packValue(packer, obj.roomName)
                packer.packString("AdminPass"); packValue(packer, obj.adminPass)
                packer.packString("Nick"); packValue(packer, obj.nick)
                packer.packString("Team"); packValue(packer, obj.team)
            }
            is ClientRequestLeaveRoom -> {
                packer.packMapHeader(0)
            }
            is ClientChat -> {
                packer.packMapHeader(1)
                packer.packString("Message"); packValue(packer, obj.message)
            }
            is ClientTryCheck -> {
                packer.packMapHeader(2)
                packer.packString("Index"); packValue(packer, obj.index)
                packer.packString("ForUser"); packValue(packer, obj.forUser)
            }
            is ClientTryMark -> {
                packer.packMapHeader(1)
                packer.packString("Index"); packValue(packer, obj.index)
            }
            is ClientTrySetCounter -> {
                packer.packMapHeader(3)
                packer.packString("Index"); packValue(packer, obj.index)
                packer.packString("Change"); packValue(packer, obj.change)
                packer.packString("ForUser"); packValue(packer, obj.forUser)
            }
            is ClientChangeMatchStatus -> {
                packer.packMapHeader(1)
                packer.packString("MatchStatus"); packValue(packer, obj.matchStatus)
            }
            is ClientTogglePause -> {
                packer.packMapHeader(0)
            }
            is ClientRequestCurrentGameSettings -> {
                packer.packMapHeader(0)
            }
            is ClientSetGameSettings -> {
                packer.packMapHeader(1)
                packer.packString("GameSettings"); packGameSettings(packer, obj.gameSettings)
            }
            is ClientRequestTeamChange -> {
                packer.packMapHeader(1)
                packer.packString("Team"); packValue(packer, obj.team)
            }
            is ClientRandomizeBoard -> {
                packer.packMapHeader(0)
            }
            is ClientBingoJson -> {
                packer.packMapHeader(1)
                packer.packString("Json"); packValue(packer, obj.json)
            }
            is ClientSetTeamName -> {
                packer.packMapHeader(2)
                packer.packString("Team"); packValue(packer, obj.team)
                packer.packString("Name"); packValue(packer, obj.name)
            }
            is ClientBanUserFromRoom -> {
                packer.packMapHeader(1)
                packer.packString("BannedUser"); packValue(packer, obj.bannedUser)
            }
            is ClientPromoteToAdmin -> {
                packer.packMapHeader(1)
                packer.packString("PromotedUser"); packValue(packer, obj.promotedUser)
            }
            is ClientCoordinates -> {
                packer.packMapHeader(5)
                packer.packString("X"); packValue(packer, obj.x)
                packer.packString("Y"); packValue(packer, obj.y)
                packer.packString("Angle"); packValue(packer, obj.angle)
                packer.packString("IsUnderground"); packValue(packer, obj.isUnderground)
                packer.packString("MapInstance"); packValue(packer, obj.mapInstance)
            }
            else -> {
                // Fallback: use Kotlin reflection for unknown types
                val props = obj::class.members.filterIsInstance<kotlin.reflect.KProperty1<Any, *>>()
                packer.packMapHeader(props.size)
                for (prop in props) {
                    packer.packString(prop.name)
                    val value = try { prop.get(obj) } catch (_: Exception) { null }
                    packValue(packer, value)
                }
            }
        }
    }

    private fun packetTypeName(obj: Any): String {
        return when (obj) {
            is ClientRegister -> "Neto.Shared.ClientRegister"
            is KeepAlive -> "Neto.Shared.KeepAlive"
            else -> obj::class.simpleName ?: obj::class.java.simpleName
        }
    }

    private fun packGameSettings(packer: org.msgpack.core.MessagePacker, settings: BingoGameSettings) {
        packer.packMapHeader(9)
        packer.packString("BoardSize"); packValue(packer, settings.boardSize)
        packer.packString("Lockout"); packValue(packer, settings.lockout)
        packer.packString("RandomClasses"); packValue(packer, settings.randomClasses)
        packer.packString("ValidClasses"); run {
            val list = settings.validClasses.toList()
            packer.packArrayHeader(list.size)
            list.forEach { packValue(packer, it) }
        }
        packer.packString("NumberOfClasses"); packValue(packer, settings.numberOfClasses)
        packer.packString("CategoryLimit"); packValue(packer, settings.categoryLimit)
        packer.packString("RandomSeed"); packValue(packer, settings.randomSeed)
        packer.packString("PreparationTime"); packValue(packer, settings.preparationTime)
        packer.packString("PointsPerBingoLine"); packValue(packer, settings.pointsPerBingoLine)
    }

    private fun packValue(packer: org.msgpack.core.MessagePacker, value: Any?) {
        when (value) {
            null -> packer.packNil()
            is String -> packer.packString(value)
            is Int -> packer.packInt(value)
            is Long -> packer.packLong(value)
            is Float -> packer.packFloat(value)
            is Double -> packer.packDouble(value)
            is Boolean -> packer.packBoolean(value)
            is UUID -> packer.packString(value.toString())
            is Enum<*> -> packer.packInt(value.ordinal)
            is IntArray -> {
                packer.packArrayHeader(value.size)
                value.forEach { packer.packInt(it) }
            }
            is List<*> -> {
                packer.packArrayHeader(value.size)
                value.forEach { packValue(packer, it) }
            }
            is Set<*> -> {
                packer.packArrayHeader(value.size)
                value.forEach { packValue(packer, it) }
            }
            is Map<*, *> -> {
                packer.packMapHeader(value.size)
                value.forEach { (k, v) ->
                    packValue(packer, k)
                    packValue(packer, v)
                }
            }
            else -> {
                // Nested object - serialize as map
                packObjectAsMap(packer, value)
            }
        }
    }

    // ---- Helpers ----

    private fun parseUsersFromMap(map: Map<String, Value>): List<UserInRoom> {
        val usersArr = map["Users"]?.asArrayValue()?.list() ?: return emptyList()
        return usersArr.mapNotNull { uVal ->
            try {
                val uMap = uVal.asMapValue().map().mapKeys { it.key.asStringValue().asString() }
                parseUserFromMap(uMap)
            } catch (_: Exception) { null }
        }
    }

    private fun parseUserFromMap(map: Map<String, Value>): UserInRoom? {
        val nick = map["Nick"]?.asStringValue()?.asString() ?: return null
        val guidValue = map["Guid"]
        val guid = parseGuid(guidValue) ?: return null
        val isAdmin = map["IsAdmin"]?.asBooleanValue()?.getBoolean() ?: false
        val team = map["Team"]?.asIntegerValue()?.asInt() ?: 0
        return UserInRoom(nick, guid, isAdmin, team)
    }

    private fun parseGameSettings(map: Map<String, Value>): BingoGameSettings {
        return BingoGameSettings(
            boardSize = map["BoardSize"]?.asIntegerValue()?.asInt() ?: 5,
            lockout = map["Lockout"]?.asBooleanValue()?.boolean ?: false,
            randomClasses = map["RandomClasses"]?.asBooleanValue()?.boolean ?: false,
            validClasses = map["ValidClasses"]?.asArrayValue()?.list()?.mapNotNull {
                try { EldenRingClasses.entries.getOrNull(it.asIntegerValue().asInt()) }
                catch (_: Exception) { null }
            }?.toSet() ?: EldenRingClasses.entries.toSet(),
            numberOfClasses = map["NumberOfClasses"]?.asIntegerValue()?.asInt() ?: 2,
            categoryLimit = map["CategoryLimit"]?.asIntegerValue()?.asInt() ?: 2,
            randomSeed = map["RandomSeed"]?.asIntegerValue()?.asInt() ?: 0,
            preparationTime = map["PreparationTime"]?.asIntegerValue()?.asInt() ?: 0,
            pointsPerBingoLine = map["PointsPerBingoLine"]?.asIntegerValue()?.asInt() ?: 1
        )
    }

    private fun parseGuid(value: Value?): UUID? {
        if (value == null) return null
        if (value.isBinaryValue) {
            val bytes = value.asBinaryValue().asByteArray()
            if (bytes.size == 16) {
                val bb = ByteBuffer.wrap(bytes)
                val high = bb.long
                val low = bb.long
                return UUID(high, low)
            }
        }
        val str = if (value.isStringValue) value.asStringValue().asString() else value.toString()
        return try {
            // Handle base64-encoded GUID
            if (str.length == 24) {
                try {
                    val bytes = android.util.Base64.decode(str, android.util.Base64.DEFAULT)
                    if (bytes.size == 16) {
                        val bb = ByteBuffer.wrap(bytes)
                        val high = bb.long
                        val low = bb.long
                        UUID(high, low)
                    } else strToUuid(str)
                } catch (_: Exception) { strToUuid(str) }
            } else {
                strToUuid(str)
            }
        } catch (_: Exception) { null }
    }

    private fun strToUuid(str: String): UUID? {
        return try { UUID.fromString(str) } catch (_: Exception) { null }
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
