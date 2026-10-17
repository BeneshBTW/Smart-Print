package com.example.smartprint

import androidx.compose.ui.geometry.Offset

sealed class EditorAction {
    data class AddWall(val wall: Wall) : EditorAction()

    data class DeleteWall(
        val wall: Wall,
        val attachedOpenings: List<Opening>
    ) : EditorAction()

    data class MoveWall(
        val wallId: String,
        val beforeStart: Offset,
        val beforeEnd: Offset,
        val afterStart: Offset,
        val afterEnd: Offset
    ) : EditorAction()

    data class ChangeWallType(
        val wallId: String,
        val beforeType: WallType,
        val beforeThickness: Float,
        val afterType: WallType,
        val afterThickness: Float
    ) : EditorAction()

    data class SetWallLength(
        val wallId: String,
        val beforeStart: Offset,
        val beforeEnd: Offset,
        val afterStart: Offset,
        val afterEnd: Offset
    ) : EditorAction()

    data class AddOpening(val opening: Opening) : EditorAction()

    data class DeleteOpening(val opening: Opening) : EditorAction()

    data class MoveOpening(
        val openingId: String,
        val beforeT: Float,
        val afterT: Float
    ) : EditorAction()

    data class ResizeOpening(
        val openingId: String,
        val beforeWidthFeet: Float,
        val beforeCenterT: Float,
        val afterWidthFeet: Float,
        val afterCenterT: Float
    ) : EditorAction()

    data class ClearCanvas(
        val previousWalls: List<Wall>,
        val previousOpenings: List<Opening>
    ) : EditorAction()
}