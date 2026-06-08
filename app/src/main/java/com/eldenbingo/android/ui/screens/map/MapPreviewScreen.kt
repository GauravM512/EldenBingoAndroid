package com.eldenbingo.android.ui.screens.map

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldenbingo.android.data.model.MapInstance
import com.eldenbingo.android.data.model.PlayerPosition
import com.eldenbingo.android.ui.theme.EldenGold
import com.eldenbingo.android.ui.theme.TeamColors
import java.util.LinkedHashMap
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private enum class MapChoice {
    Auto, Main, DLC
}

private data class MapSpec(
    val title: String,
    val instance: MapInstance,
    val width: Float,
    val height: Float,
    val offset: Offset,
    val assetPath: String
)

private data class MapTile(
    val col: Int,
    val row: Int,
    val assetPath: String,
    val pixelWidth: Int,
    val pixelHeight: Int
)

private data class MapTileSet(
    val tiles: List<MapTile>,
    val firstTileWidth: Int,
    val firstTileHeight: Int,
    val columns: Int,
    val rows: Int,
    val totalPixelWidth: Float,
    val totalPixelHeight: Float,
    val colWidths: IntArray,
    val rowHeights: IntArray
)

private data class CachedTile(
    val bitmap: android.graphics.Bitmap,
    val image: ImageBitmap
)

private class MapTileImageCache(
    private val assetManager: AssetManager,
    private val maxTiles: Int = 160
) {
    private val cache = object : LinkedHashMap<String, CachedTile>(maxTiles, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTile>?): Boolean {
            val shouldRemove = size > maxTiles
            if (shouldRemove) {
                eldest?.value?.bitmap?.recycle()
            }
            return shouldRemove
        }
    }

    fun get(tile: MapTile, targetWidth: Int, targetHeight: Int): ImageBitmap? {
        val sampleSize = calculateInSampleSize(tile.pixelWidth, tile.pixelHeight, targetWidth, targetHeight)
        val key = "${tile.assetPath}@$sampleSize"
        cache[key]?.let { return it.image }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = assetManager.open(tile.assetPath).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val cached = CachedTile(bitmap, bitmap.asImageBitmap())
        cache[key] = cached
        return cached.image
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        val requestedWidth = targetWidth.coerceAtLeast(96)
        val requestedHeight = targetHeight.coerceAtLeast(96)
        var sampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / sampleSize >= requestedWidth && halfHeight / sampleSize >= requestedHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }
}

private val MainMapSpec = MapSpec("Lands Between", MapInstance.MainMap, 9645f, 9119f, Offset.Zero, "textures/Map")
private val DlcMapSpec = MapSpec("Shadow Realm", MapInstance.DLC, 4879f, 5940f, Offset(3035f, 1864f), "textures/Map/DLC")
private val RoundTableWorldRect = Rect(2740f, 7510f, 2940f, 7710f)
private val RoundTableMapPos = Offset(1750f, 7369f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPreviewScreen(
    playerPositions: Map<UUID, PlayerPosition>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val roundTableImage = remember {
        context.assets.open("textures/RoundTable.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
    }
    val mainTiles = remember { loadMapTiles(context.assets, MainMapSpec.assetPath) }
    val dlcTiles = remember { loadMapTiles(context.assets, DlcMapSpec.assetPath) }
    val tileCache = remember { MapTileImageCache(context.assets) }

    var mapChoice by remember { mutableStateOf(MapChoice.Auto) }
    var followedPlayer by remember { mutableStateOf<UUID?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var cameraCenter by remember { mutableStateOf(Offset(MainMapSpec.width * 0.5f, MainMapSpec.height * 0.5f)) }
    var zoom by remember { mutableFloatStateOf(0.09f) }

    val automaticMap = remember(playerPositions) {
        val mainCount = playerPositions.values.count { it.mapInstance == MapInstance.MainMap && it.x > 0f && it.y > 0f }
        val dlcCount = playerPositions.values.count { it.mapInstance == MapInstance.DLC && it.x > 0f && it.y > 0f }
        if (dlcCount > mainCount) DlcMapSpec else MainMapSpec
    }
    val mapSpec = when (mapChoice) {
        MapChoice.Auto -> automaticMap
        MapChoice.Main -> MainMapSpec
        MapChoice.DLC -> DlcMapSpec
    }
    val visiblePlayers = playerPositions.values
        .filter { it.mapInstance == mapSpec.instance && it.x > 0f && it.y > 0f }
        .sortedBy { it.nick.lowercase() }

    fun fitAll() {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return
        if (visiblePlayers.isEmpty()) {
            cameraCenter = mapSpec.offset + Offset(mapSpec.width * 0.5f, mapSpec.height * 0.5f)
            zoom = min(viewportSize.width / mapSpec.width, viewportSize.height / mapSpec.height).coerceIn(0.01f, 5f)
            return
        }

        val minX = visiblePlayers.minOf { it.x }
        val maxX = visiblePlayers.maxOf { it.x }
        val minY = visiblePlayers.minOf { it.y }
        val maxY = visiblePlayers.maxOf { it.y }
        val paddedWidth = max(600f, (maxX - minX) * 1.3f)
        val paddedHeight = max(600f, (maxY - minY) * 1.3f)
        cameraCenter = Offset((minX + maxX) * 0.5f, (minY + maxY) * 0.5f)
        zoom = min(viewportSize.width / paddedWidth, viewportSize.height / paddedHeight).coerceIn(0.01f, 5f)
    }

    LaunchedEffect(mapSpec.instance, viewportSize, visiblePlayers.size) {
        if (followedPlayer == null) {
            fitAll()
        }
    }

    LaunchedEffect(followedPlayer, playerPositions, mapSpec.instance) {
        val player = followedPlayer?.let { playerPositions[it] }
        if (player != null && player.mapInstance == mapSpec.instance && player.x > 0f && player.y > 0f) {
            cameraCenter = Offset(player.x, player.y)
            zoom = zoom.coerceAtLeast(0.35f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .systemBarsPadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("Map", color = EldenGold, fontWeight = FontWeight.Bold)
                    Text(mapSpec.title, color = Color.LightGray, fontSize = 11.sp)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = EldenGold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2D2D2D)),
            actions = {
                IconButton(onClick = {
                    followedPlayer = null
                    fitAll()
                }) {
                    Icon(Icons.Default.FilterCenterFocus, "Fit all players", tint = EldenGold)
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF202020))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                MapChoice.entries.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = mapChoice == choice,
                        onClick = {
                            mapChoice = choice
                            followedPlayer = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, MapChoice.entries.size),
                        label = { Text(choice.name, fontSize = 11.sp) }
                    )
                }
            }
            FollowPlayerMenu(
                players = visiblePlayers,
                followedPlayer = followedPlayer,
                onFollow = { followedPlayer = it }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .pointerInput(mapSpec.instance) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        followedPlayer = null
                        val before = screenToWorld(centroid, viewportSize, cameraCenter, zoom)
                        val newZoom = (zoom * zoomChange).coerceIn(0.01f, 5f)
                        val after = screenToWorld(centroid, viewportSize, cameraCenter, newZoom)
                        cameraCenter += before - after - Offset(pan.x / newZoom, pan.y / newZoom)
                        zoom = newZoom
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tiles = if (mapSpec.instance == MapInstance.DLC) dlcTiles else mainTiles
                drawMapBackground(mapSpec, tiles, tileCache, cameraCenter, zoom)
                if (mapSpec.instance == MapInstance.MainMap) {
                    drawRoundTable(roundTableImage, cameraCenter, zoom)
                }
                visiblePlayers.forEach { player ->
                    drawPlayer(player, cameraCenter, zoom)
                }
            }

            MapStatusCard(
                mapTitle = mapSpec.title,
                playerCount = visiblePlayers.size,
                zoom = zoom,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )

            IconButton(
                onClick = {
                    followedPlayer = null
                    fitAll()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Fit all players",
                    tint = EldenGold,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun FollowPlayerMenu(
    players: List<PlayerPosition>,
    followedPlayer: UUID?,
    onFollow: (UUID?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = players.firstOrNull { it.userGuid == followedPlayer }?.nick ?: "Follow"

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedName, color = EldenGold, fontSize = 11.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF2D2D2D)
        ) {
            DropdownMenuItem(
                text = { Text("Fit all", color = Color.White) },
                onClick = {
                    expanded = false
                    onFollow(null)
                }
            )
            players.forEach { player ->
                DropdownMenuItem(
                    text = { Text(player.nick, color = Color.White) },
                    onClick = {
                        expanded = false
                        onFollow(player.userGuid)
                    }
                )
            }
        }
    }
}

@Composable
private fun MapStatusCard(
    mapTitle: String,
    playerCount: Int,
    zoom: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xAA000000))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(mapTitle, color = EldenGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Players: $playerCount", color = Color.White, fontSize = 10.sp)
            Text("Zoom: ${"%.2f".format(zoom)}", color = Color.Gray, fontSize = 9.sp)
        }
    }
}

private fun DrawScope.drawMapBackground(
    mapSpec: MapSpec,
    tileSet: MapTileSet?,
    tileCache: MapTileImageCache,
    cameraCenter: Offset,
    zoom: Float
) {
    drawRect(Color(0xFF141A14))

    val topLeftWorld = mapSpec.offset
    val bottomRightWorld = mapSpec.offset + Offset(mapSpec.width, mapSpec.height)
    val topLeft = worldToScreen(topLeftWorld, size, cameraCenter, zoom)
    val bottomRight = worldToScreen(bottomRightWorld, size, cameraCenter, zoom)
    val mapRectSize = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)

    if (tileSet == null || tileSet.tiles.isEmpty()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF31412F), Color(0xFF171D17)),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = max(size.width, size.height)
            ),
            topLeft = topLeft,
            size = mapRectSize
        )
    } else {
        val factorX = mapSpec.width / tileSet.totalPixelWidth
        val factorY = mapSpec.height / tileSet.totalPixelHeight

        val colOffsets = FloatArray(tileSet.columns)
        val rowOffsets = FloatArray(tileSet.rows)
        for (i in 1 until tileSet.columns) colOffsets[i] = colOffsets[i - 1] + tileSet.colWidths[i - 1]
        for (i in 1 until tileSet.rows) rowOffsets[i] = rowOffsets[i - 1] + tileSet.rowHeights[i - 1]

        tileSet.tiles.forEach { tile ->
            val tileWorldTopLeft = mapSpec.offset + Offset(
                colOffsets[tile.col] * factorX,
                rowOffsets[tile.row] * factorY
            )
            val tileWorldBottomRight = tileWorldTopLeft + Offset(
                tile.pixelWidth * factorX,
                tile.pixelHeight * factorY
            )
            val tileTopLeft = worldToScreen(tileWorldTopLeft, size, cameraCenter, zoom)
            val tileBottomRight = worldToScreen(tileWorldBottomRight, size, cameraCenter, zoom)
            val tileWidth = (tileBottomRight.x - tileTopLeft.x).toInt()
            val tileHeight = (tileBottomRight.y - tileTopLeft.y).toInt()
            if (
                tileWidth > 0 &&
                tileHeight > 0 &&
                tileBottomRight.x >= 0f &&
                tileBottomRight.y >= 0f &&
                tileTopLeft.x <= size.width &&
                tileTopLeft.y <= size.height
            ) {
                val image = tileCache.get(tile, tileWidth, tileHeight) ?: return@forEach
                drawImage(
                    image = image,
                    dstOffset = androidx.compose.ui.unit.IntOffset(tileTopLeft.x.toInt(), tileTopLeft.y.toInt()),
                    dstSize = IntSize(tileWidth, tileHeight)
                )
            }
        }
    }

    drawRect(
        color = EldenGold.copy(alpha = 0.35f),
        topLeft = topLeft,
        size = mapRectSize,
        style = Stroke(width = 2f)
    )

    val gridStep = if (zoom > 0.3f) 500f else 1000f
    var gx = topLeftWorld.x
    while (gx <= bottomRightWorld.x) {
        val start = worldToScreen(Offset(gx, topLeftWorld.y), size, cameraCenter, zoom)
        val end = worldToScreen(Offset(gx, bottomRightWorld.y), size, cameraCenter, zoom)
        drawLine(Color(0x224A5E4A), start, end, strokeWidth = 1f)
        gx += gridStep
    }
    var gy = topLeftWorld.y
    while (gy <= bottomRightWorld.y) {
        val start = worldToScreen(Offset(topLeftWorld.x, gy), size, cameraCenter, zoom)
        val end = worldToScreen(Offset(bottomRightWorld.x, gy), size, cameraCenter, zoom)
        drawLine(Color(0x224A5E4A), start, end, strokeWidth = 1f)
        gy += gridStep
    }
}

private fun DrawScope.drawRoundTable(image: ImageBitmap, cameraCenter: Offset, zoom: Float) {
    val center = worldToScreen(RoundTableMapPos, size, cameraCenter, zoom)
    val baseSide = 500f
    val side = baseSide * zoom
    val topLeft = center - Offset(side * 0.5f, side * 0.5f)
    drawImage(
        image = image,
        dstOffset = androidx.compose.ui.unit.IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
        dstSize = IntSize(side.toInt().coerceAtLeast(1), side.toInt().coerceAtLeast(1))
    )
    drawRect(
        color = EldenGold.copy(alpha = 0.55f),
        topLeft = topLeft,
        size = Size(side, side),
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawPlayer(player: PlayerPosition, cameraCenter: Offset, zoom: Float) {
    val worldPos = if (player.mapInstance == MapInstance.MainMap &&
        player.x in RoundTableWorldRect.left..RoundTableWorldRect.right &&
        player.y in RoundTableWorldRect.top..RoundTableWorldRect.bottom) {
        RoundTableMapPos
    } else {
        Offset(player.x, player.y)
    }
    val screenPos = worldToScreen(worldPos, size, cameraCenter, zoom)
    if (screenPos.x !in -80f..size.width + 80f || screenPos.y !in -80f..size.height + 80f) return

    val teamColor = if (player.team in TeamColors.indices) TeamColors[player.team] else Color.White
    val alpha = if (player.isUnderground) 0.45f else 1f
    val markerSize = (28f * max(0.75f, min(1.8f, zoom * 2.2f))).coerceIn(18f, 42f)

    rotate(player.angle, screenPos) {
        val path = Path().apply {
            moveTo(screenPos.x, screenPos.y - markerSize)
            lineTo(screenPos.x - markerSize * 0.55f, screenPos.y + markerSize * 0.45f)
            lineTo(screenPos.x, screenPos.y + markerSize * 0.15f)
            lineTo(screenPos.x + markerSize * 0.55f, screenPos.y + markerSize * 0.45f)
            close()
        }
        drawPath(path, teamColor.copy(alpha = alpha), style = Fill)
        drawPath(path, Color.Black.copy(alpha = 0.8f), style = Stroke(width = 2f))
    }

    drawCircle(
        color = Color.Black.copy(alpha = 0.85f),
        radius = markerSize * 0.35f,
        center = screenPos
    )
    drawCircle(
        color = teamColor.copy(alpha = alpha),
        radius = markerSize * 0.26f,
        center = screenPos
    )

    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = teamColor.toArgb()
            textSize = 13f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
        }
        drawText(player.nick, screenPos.x, screenPos.y + markerSize + 16f, paint)
    }
}

private fun worldToScreen(world: Offset, viewport: Size, cameraCenter: Offset, zoom: Float): Offset {
    return Offset(
        (world.x - cameraCenter.x) * zoom + viewport.width * 0.5f,
        (world.y - cameraCenter.y) * zoom + viewport.height * 0.5f
    )
}

private fun screenToWorld(screen: Offset, viewport: IntSize, cameraCenter: Offset, zoom: Float): Offset {
    return Offset(
        (screen.x - viewport.width * 0.5f) / zoom + cameraCenter.x,
        (screen.y - viewport.height * 0.5f) / zoom + cameraCenter.y
    )
}

private fun loadMapTiles(assetManager: AssetManager, path: String): MapTileSet? {
    val files = assetManager.list(path)
        ?.filter { it.endsWith(".jpg", ignoreCase = true) || it.endsWith(".jpeg", ignoreCase = true) || it.endsWith(".png", ignoreCase = true) }
        ?.sorted()
        ?: return null

    val tiles = files.mapNotNull { file ->
        val match = Regex("""(\d+)x(\d+)\.(jpg|jpeg|png)""", RegexOption.IGNORE_CASE).matchEntire(file) ?: return@mapNotNull null
        val col = match.groupValues[1].toInt()
        val row = match.groupValues[2].toInt()
        val assetPath = "$path/$file"
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assetManager.open(assetPath).use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return@mapNotNull null
        MapTile(
            col = col,
            row = row,
            assetPath = assetPath,
            pixelWidth = options.outWidth,
            pixelHeight = options.outHeight
        )
    }

    val first = tiles.firstOrNull() ?: return null
    val columns = tiles.maxOf { it.col } + 1
    val rows = tiles.maxOf { it.row } + 1

    val colWidths = IntArray(columns)
    val rowHeights = IntArray(rows)
    tiles.forEach { tile ->
        colWidths[tile.col] = max(colWidths[tile.col], tile.pixelWidth)
        rowHeights[tile.row] = max(rowHeights[tile.row], tile.pixelHeight)
    }

    return MapTileSet(
        tiles = tiles,
        firstTileWidth = first.pixelWidth,
        firstTileHeight = first.pixelHeight,
        columns = columns,
        rows = rows,
        totalPixelWidth = colWidths.sum().toFloat(),
        totalPixelHeight = rowHeights.sum().toFloat(),
        colWidths = colWidths,
        rowHeights = rowHeights
    )
}
