package com.example.smartprint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class SmartPrintViewModel : ViewModel() {

    var pixelsPerFoot by mutableStateOf(40f)

    // 1 grid cell = 1 foot
    var gridStepPx by mutableStateOf(40f)

    var toolMode by mutableStateOf(ToolMode.DRAW_WALL)
    var currentDrawWallType by mutableStateOf(WallType.INTERIOR)
    var gridSnapEnabled by mutableStateOf(true)

    var walls by mutableStateOf(listOf<Wall>())
        private set

    var openings by mutableStateOf(listOf<Opening>())
        private set

    var selectedWallId by mutableStateOf<String?>(null)
    var selectedOpeningId by mutableStateOf<String?>(null)

    var zoomScale by mutableStateOf(1f)
    var panOffset by mutableStateOf(Offset.Zero)

    private var nextOrder by mutableLongStateOf(1L)

    private var currentStart: Offset? = null
    var currentEnd by mutableStateOf<Offset?>(null)

    private var editDragStartPoint: Offset? = null
    private var editOriginalWall: Wall? = null

    private var openingDragStartPoint: Offset? = null
    private var openingOriginal: Opening? = null

    private val history = EditorHistory()

    val canUndo: Boolean
        get() = history.canUndo

    val canRedo: Boolean
        get() = history.canRedo

    fun getCurrentStart(): Offset? = currentStart

    fun getSelectedWall(): Wall? {
        val id = selectedWallId ?: return null
        return walls.firstOrNull { it.id == id }
    }

    fun getSelectedOpening(): Opening? {
        val id = selectedOpeningId ?: return null
        return openings.firstOrNull { it.id == id }
    }

    fun openingsForWall(wallId: String): List<Opening> {
        return openings.filter { it.wallId == wallId }.sortedBy { it.centerT }
    }

    fun getSelectedWallLengthFeet(): Float? {
        val wall = getSelectedWall() ?: return null
        return wallLengthFeet(wall)
    }

    fun getCurrentWallLengthFeet(): Float? {
        val start = currentStart ?: return null
        val end = currentEnd ?: return null
        return lengthFeet(start, end, pixelsPerFoot)
    }

    fun wallLengthFeet(wall: Wall): Float {
        return lengthFeet(wall.start, wall.end, pixelsPerFoot)
    }

    fun updatePixelsPerFoot(value: Float) {
        pixelsPerFoot = value
        gridStepPx = value
    }

    fun toggleGridSnap(enabled: Boolean) {
        gridSnapEnabled = enabled
    }

    fun changeToolMode(mode: ToolMode) {
        toolMode = mode
        if (mode == ToolMode.PAN) {
            selectedWallId = null
            selectedOpeningId = null
        }
    }

    fun changeDrawWallType(type: WallType) {
        currentDrawWallType = type
    }

    fun worldToScreen(world: Offset): Offset {
        return Offset(
            world.x * zoomScale + panOffset.x,
            world.y * zoomScale + panOffset.y
        )
    }

    fun screenToWorld(screen: Offset): Offset {
        return Offset(
            (screen.x - panOffset.x) / zoomScale,
            (screen.y - panOffset.y) / zoomScale
        )
    }

    fun startWall(pointWorld: Offset) {
        currentStart = snapPoint(pointWorld, gridSnapEnabled, gridStepPx)
        currentEnd = currentStart
        selectedWallId = null
        selectedOpeningId = null
    }

    fun updateWall(pointWorld: Offset) {
        val start = currentStart ?: return
        val raw = snapPoint(pointWorld, gridSnapEnabled, gridStepPx)
        currentEnd = axisLockPoint(start, raw)
    }

    fun endWall() {
        val start = currentStart
        val end = currentEnd

        if (start != null && end != null && start != end) {
            val wall = Wall(
                start = start,
                end = end,
                thickness = thicknessForType(currentDrawWallType),
                type = currentDrawWallType,
                order = takeNextOrder()
            )
            walls = walls + wall
            history.record(EditorAction.AddWall(wall))
        }

        currentStart = null
        currentEnd = null
    }

    fun onSelectTap(pointWorld: Offset) {
        if (trySelectOpeningAt(pointWorld)) return
        trySelectWallAt(pointWorld)
    }

    fun onSelectDragStart(pointWorld: Offset) {
        if (trySelectOpeningAt(pointWorld)) {
            val opening = getSelectedOpening() ?: return
            openingDragStartPoint = pointWorld
            openingOriginal = opening
            selectedWallId = opening.wallId
            editDragStartPoint = null
            editOriginalWall = null
            return
        }

        val selected = trySelectWallAt(pointWorld)
        if (!selected) {
            clearDragState()
            return
        }

        val wall = getSelectedWall() ?: return
        editDragStartPoint = pointWorld
        editOriginalWall = wall.copy()
        openingDragStartPoint = null
        openingOriginal = null
    }

    fun onSelectDrag(pointWorld: Offset) {
        if (selectedOpeningId != null && openingOriginal != null && openingDragStartPoint != null) {
            dragSelectedOpening(pointWorld)
            return
        }

        val wallId = selectedWallId ?: return
        val dragStart = editDragStartPoint ?: return
        val originalWall = editOriginalWall ?: return

        val totalDelta = pointWorld - dragStart

        var newStart = originalWall.start + totalDelta
        var newEnd = originalWall.end + totalDelta

        if (gridSnapEnabled) {
            val snappedStart = snapPoint(newStart, true, gridStepPx)
            val snapAdjustment = snappedStart - newStart
            newStart = snappedStart
            newEnd = newEnd + snapAdjustment
        }

        walls = walls.map { wall ->
            if (wall.id == wallId) wall.copy(start = newStart, end = newEnd) else wall
        }
    }

    fun onSelectDragEnd() {
        commitWallMoveIfNeeded()
        commitOpeningMoveIfNeeded()
        clearDragState()
    }

    fun trySelectWallAt(pointWorld: Offset): Boolean {
        if (walls.isEmpty()) {
            selectedWallId = null
            return false
        }

        val toleranceWorld = 28f / zoomScale
        var bestWall: Wall? = null
        var bestDistance = Float.MAX_VALUE

        walls.forEach { wall ->
            val d = distancePointToSegment(pointWorld, wall.start, wall.end)
            if (d <= toleranceWorld && d < bestDistance) {
                bestDistance = d
                bestWall = wall
            }
        }

        selectedWallId = bestWall?.id
        if (bestWall != null) selectedOpeningId = null
        return bestWall != null
    }

    fun trySelectOpeningAt(pointWorld: Offset): Boolean {
        if (openings.isEmpty()) {
            selectedOpeningId = null
            return false
        }

        val toleranceWorld = 22f / zoomScale
        var bestOpening: Opening? = null
        var bestDistance = Float.MAX_VALUE

        openings.forEach { opening ->
            val wall = walls.firstOrNull { it.id == opening.wallId } ?: return@forEach
            val wallLength = distance(wall.start, wall.end)
            if (wallLength <= 1f) return@forEach

            val halfWidthWorld = opening.widthFeet * pixelsPerFoot / 2f
            val center = lerpWorld(wall.start, wall.end, opening.centerT)
            val dir = normalizeSafe(wall.end - wall.start)
            val a = center - dir * halfWidthWorld
            val b = center + dir * halfWidthWorld

            val d = distancePointToSegment(pointWorld, a, b)
            if (d <= toleranceWorld && d < bestDistance) {
                bestDistance = d
                bestOpening = opening
            }
        }

        selectedOpeningId = bestOpening?.id
        if (bestOpening != null) selectedWallId = bestOpening.wallId
        return bestOpening != null
    }

    fun setSelectedWallType(type: WallType) {
        val wall = getSelectedWall() ?: return
        if (wall.type == type) return

        val newThickness = thicknessForType(type)

        walls = walls.map {
            if (it.id == wall.id) it.copy(type = type, thickness = newThickness) else it
        }

        history.record(
            EditorAction.ChangeWallType(
                wallId = wall.id,
                beforeType = wall.type,
                beforeThickness = wall.thickness,
                afterType = type,
                afterThickness = newThickness
            )
        )
    }

    fun setSelectedWallLength(lengthFeet: Float) {
        val wall = getSelectedWall() ?: return

        val dx = wall.end.x - wall.start.x
        val dy = wall.end.y - wall.start.y
        val angle = atan2(dy, dx)
        val lengthPx = lengthFeet * pixelsPerFoot

        val newEnd = Offset(
            wall.start.x + cos(angle) * lengthPx,
            wall.start.y + sin(angle) * lengthPx
        )

        if (wall.end == newEnd) return

        walls = walls.map {
            if (it.id == wall.id) it.copy(end = newEnd) else it
        }

        history.record(
            EditorAction.SetWallLength(
                wallId = wall.id,
                beforeStart = wall.start,
                beforeEnd = wall.end,
                afterStart = wall.start,
                afterEnd = newEnd
            )
        )
    }

    fun placeOpeningAt(pointWorld: Offset, type: OpeningType) {
        val selected = trySelectWallAt(pointWorld)
        if (!selected) return

        val wall = getSelectedWall() ?: return
        val wallLengthWorld = distance(wall.start, wall.end)
        if (wallLengthWorld <= 1f) return

        val widthFeet = when (type) {
            OpeningType.DOOR -> 3f
            OpeningType.WINDOW -> 4f
        }

        val widthWorld = widthFeet * pixelsPerFoot
        val halfT = (widthWorld / 2f) / wallLengthWorld
        if (halfT >= 0.45f) return

        val projectedT = projectionT(pointWorld, wall.start, wall.end)
        val centerT = projectedT.coerceIn(halfT + 0.02f, 1f - halfT - 0.02f)

        val overlaps = openingsForWall(wall.id).any { existing ->
            val existingHalfT = (existing.widthFeet * pixelsPerFoot / 2f) / wallLengthWorld
            val minA = centerT - halfT
            val maxA = centerT + halfT
            val minB = existing.centerT - existingHalfT
            val maxB = existing.centerT + existingHalfT
            maxA > minB && maxB > minA
        }

        if (overlaps) return

        val opening = Opening(
            wallId = wall.id,
            type = type,
            centerT = centerT,
            widthFeet = widthFeet,
            order = takeNextOrder()
        )

        openings = openings + opening
        selectedOpeningId = opening.id
        selectedWallId = wall.id

        history.record(EditorAction.AddOpening(opening))
    }

    fun dragSelectedOpening(pointWorld: Offset) {
        val opening = openingOriginal ?: return
        val wall = walls.firstOrNull { it.id == opening.wallId } ?: return

        val wallLength = distance(wall.start, wall.end)
        if (wallLength <= 1f) return

        val halfT = (opening.widthFeet * pixelsPerFoot / 2f) / wallLength
        var t = projectionT(pointWorld, wall.start, wall.end)

        val otherOpenings = openingsForWall(wall.id).filter { it.id != opening.id }

        var minT = halfT + 0.02f
        var maxT = 1f - halfT - 0.02f

        otherOpenings.forEach { other ->
            val otherHalfT = (other.widthFeet * pixelsPerFoot / 2f) / wallLength
            if (other.centerT < opening.centerT) {
                minT = maxOf(minT, other.centerT + otherHalfT + halfT + 0.02f)
            } else {
                maxT = minOf(maxT, other.centerT - otherHalfT - halfT - 0.02f)
            }
        }

        t = t.coerceIn(minT, maxT)

        openings = openings.map {
            if (it.id == opening.id) it.copy(centerT = t) else it
        }
    }

    fun setSelectedOpeningWidthFeet(widthFeet: Float) {
        val opening = getSelectedOpening() ?: return
        val wall = walls.firstOrNull { it.id == opening.wallId } ?: return
        val wallLength = distance(wall.start, wall.end)
        if (wallLength <= 1f) return

        val clampedWidthFeet = widthFeet.coerceAtLeast(1f)
        val halfT = (clampedWidthFeet * pixelsPerFoot / 2f) / wallLength
        if (halfT >= 0.45f) return

        val others = openingsForWall(wall.id).filter { it.id != opening.id }

        val minT = others.filter { it.centerT < opening.centerT }.maxOfOrNull {
            it.centerT + (it.widthFeet * pixelsPerFoot / 2f) / wallLength + halfT + 0.02f
        } ?: (halfT + 0.02f)

        val maxT = others.filter { it.centerT > opening.centerT }.minOfOrNull {
            it.centerT - (it.widthFeet * pixelsPerFoot / 2f) / wallLength - halfT - 0.02f
        } ?: (1f - halfT - 0.02f)

        val newCenterT = opening.centerT.coerceIn(minT, maxT)

        if (opening.widthFeet == clampedWidthFeet && opening.centerT == newCenterT) return

        openings = openings.map {
            if (it.id == opening.id) it.copy(widthFeet = clampedWidthFeet, centerT = newCenterT) else it
        }

        history.record(
            EditorAction.ResizeOpening(
                openingId = opening.id,
                beforeWidthFeet = opening.widthFeet,
                beforeCenterT = opening.centerT,
                afterWidthFeet = clampedWidthFeet,
                afterCenterT = newCenterT
            )
        )
    }

    fun deleteSelectedOpening() {
        val id = selectedOpeningId ?: return
        val opening = openings.firstOrNull { it.id == id } ?: return

        openings = openings.filterNot { it.id == id }
        selectedOpeningId = null

        history.record(EditorAction.DeleteOpening(opening))
    }

    fun undo() {
        val action = history.popUndo() ?: return
        applyUndo(action)
        history.pushRedo(action)
    }

    fun redo() {
        val action = history.popRedo() ?: return
        applyRedo(action)
        history.pushUndo(action)
    }

    fun clear() {
        if (walls.isEmpty() && openings.isEmpty()) return

        history.record(
            EditorAction.ClearCanvas(
                previousWalls = walls,
                previousOpenings = openings
            )
        )

        walls = emptyList()
        openings = emptyList()
        selectedWallId = null
        selectedOpeningId = null
        currentStart = null
        currentEnd = null
        clearDragState()
    }

    fun resetView() {
        zoomScale = 1f
        panOffset = Offset.Zero
    }

    fun onPan(pan: Offset) {
        panOffset = panOffset + pan
    }

    fun onZoom(centroidScreen: Offset, zoomChange: Float) {
        val oldScale = zoomScale
        val newScale = (oldScale * zoomChange).coerceIn(0.5f, 4f)
        val worldAtCentroidBefore = screenToWorld(centroidScreen)

        zoomScale = newScale
        panOffset = Offset(
            centroidScreen.x - worldAtCentroidBefore.x * zoomScale,
            centroidScreen.y - worldAtCentroidBefore.y * zoomScale
        )
    }

    fun onPanZoom(
        centroid: Offset,
        pan: Offset,
        zoomChange: Float
    ) {
        if (zoomChange != 1f) onZoom(centroid, zoomChange)
        if (pan != Offset.Zero) onPan(pan)
    }

    fun exportData(): CanvasData {
        return CanvasData(
            pixelsPerFoot = pixelsPerFoot,
            lines = walls.map { it.toCanvasLineData() },
            openings = openings.map { it.toCanvasOpeningData() }
        )
    }

    fun importData(data: CanvasData) {
        pixelsPerFoot = data.pixelsPerFoot
        gridStepPx = data.pixelsPerFoot

        walls = data.lines.map { it.toWall() }

        val validWallIds = walls.map { it.id }.toSet()
        openings = data.openings.mapNotNull { it.toOpeningOrNull(validWallIds) }

        recomputeNextOrder()

        selectedWallId = null
        selectedOpeningId = null
        currentStart = null
        currentEnd = null
        clearDragState()
        history.clear()
    }

    private fun takeNextOrder(): Long {
        val value = nextOrder
        nextOrder += 1L
        return value
    }

    private fun recomputeNextOrder() {
        val maxWall = walls.maxOfOrNull { it.order } ?: 0L
        val maxOpening = openings.maxOfOrNull { it.order } ?: 0L
        nextOrder = maxOf(maxWall, maxOpening) + 1L
    }

    private fun commitWallMoveIfNeeded() {
        val originalWall = editOriginalWall
        val currentWall = getSelectedWall()

        if (originalWall != null && currentWall != null && originalWall.id == currentWall.id) {
            if (originalWall.start != currentWall.start || originalWall.end != currentWall.end) {
                history.record(
                    EditorAction.MoveWall(
                        wallId = currentWall.id,
                        beforeStart = originalWall.start,
                        beforeEnd = originalWall.end,
                        afterStart = currentWall.start,
                        afterEnd = currentWall.end
                    )
                )
            }
        }
    }

    private fun commitOpeningMoveIfNeeded() {
        val originalOpening = openingOriginal
        val currentOpening = getSelectedOpening()

        if (originalOpening != null && currentOpening != null && originalOpening.id == currentOpening.id) {
            if (originalOpening.centerT != currentOpening.centerT) {
                history.record(
                    EditorAction.MoveOpening(
                        openingId = currentOpening.id,
                        beforeT = originalOpening.centerT,
                        afterT = currentOpening.centerT
                    )
                )
            }
        }
    }

    private fun clearDragState() {
        editDragStartPoint = null
        editOriginalWall = null
        openingDragStartPoint = null
        openingOriginal = null
    }

    private fun applyUndo(action: EditorAction) {
        when (action) {
            is EditorAction.AddWall -> {
                walls = walls.filterNot { it.id == action.wall.id }
                val removedOpeningIds = openings.filter { it.wallId == action.wall.id }.map { it.id }.toSet()
                openings = openings.filterNot { it.wallId == action.wall.id }

                if (selectedWallId == action.wall.id) selectedWallId = null
                if (selectedOpeningId in removedOpeningIds) selectedOpeningId = null
            }

            is EditorAction.DeleteWall -> {
                walls = walls + action.wall
                openings = openings + action.attachedOpenings
            }

            is EditorAction.MoveWall -> {
                walls = walls.map {
                    if (it.id == action.wallId) it.copy(start = action.beforeStart, end = action.beforeEnd) else it
                }
            }

            is EditorAction.ChangeWallType -> {
                walls = walls.map {
                    if (it.id == action.wallId) {
                        it.copy(type = action.beforeType, thickness = action.beforeThickness)
                    } else it
                }
            }

            is EditorAction.SetWallLength -> {
                walls = walls.map {
                    if (it.id == action.wallId) it.copy(start = action.beforeStart, end = action.beforeEnd) else it
                }
            }

            is EditorAction.AddOpening -> {
                openings = openings.filterNot { it.id == action.opening.id }
                if (selectedOpeningId == action.opening.id) selectedOpeningId = null
            }

            is EditorAction.DeleteOpening -> {
                openings = openings + action.opening
            }

            is EditorAction.MoveOpening -> {
                openings = openings.map {
                    if (it.id == action.openingId) it.copy(centerT = action.beforeT) else it
                }
            }

            is EditorAction.ResizeOpening -> {
                openings = openings.map {
                    if (it.id == action.openingId) {
                        it.copy(
                            widthFeet = action.beforeWidthFeet,
                            centerT = action.beforeCenterT
                        )
                    } else it
                }
            }

            is EditorAction.ClearCanvas -> {
                walls = action.previousWalls
                openings = action.previousOpenings
            }
        }
    }

    private fun applyRedo(action: EditorAction) {
        when (action) {
            is EditorAction.AddWall -> {
                walls = walls + action.wall
            }

            is EditorAction.DeleteWall -> {
                walls = walls.filterNot { it.id == action.wall.id }
                openings = openings.filterNot { it.wallId == action.wall.id }

                if (selectedWallId == action.wall.id) selectedWallId = null
                if (action.attachedOpenings.any { it.id == selectedOpeningId }) selectedOpeningId = null
            }

            is EditorAction.MoveWall -> {
                walls = walls.map {
                    if (it.id == action.wallId) it.copy(start = action.afterStart, end = action.afterEnd) else it
                }
            }

            is EditorAction.ChangeWallType -> {
                walls = walls.map {
                    if (it.id == action.wallId) {
                        it.copy(type = action.afterType, thickness = action.afterThickness)
                    } else it
                }
            }

            is EditorAction.SetWallLength -> {
                walls = walls.map {
                    if (it.id == action.wallId) it.copy(start = action.afterStart, end = action.afterEnd) else it
                }
            }

            is EditorAction.AddOpening -> {
                openings = openings + action.opening
            }

            is EditorAction.DeleteOpening -> {
                openings = openings.filterNot { it.id == action.opening.id }
                if (selectedOpeningId == action.opening.id) selectedOpeningId = null
            }

            is EditorAction.MoveOpening -> {
                openings = openings.map {
                    if (it.id == action.openingId) it.copy(centerT = action.afterT) else it
                }
            }

            is EditorAction.ResizeOpening -> {
                openings = openings.map {
                    if (it.id == action.openingId) {
                        it.copy(
                            widthFeet = action.afterWidthFeet,
                            centerT = action.afterCenterT
                        )
                    } else it
                }
            }

            is EditorAction.ClearCanvas -> {
                walls = emptyList()
                openings = emptyList()
                selectedWallId = null
                selectedOpeningId = null
            }
        }
    }
}