package com.example.smartprint

import androidx.compose.ui.geometry.Offset
import java.util.UUID

enum class ToolMode {
    SELECT_WALL,
    DRAW_WALL,
    PLACE_DOOR,
    PLACE_WINDOW,
    PAN
}

enum class WallType {
    INTERIOR,
    EXTERIOR
}

enum class OpeningType {
    DOOR,
    WINDOW
}

data class Wall(
    val id: String = UUID.randomUUID().toString(),
    val start: Offset,
    val end: Offset,
    val thickness: Float = 12f,
    val type: WallType = WallType.INTERIOR,
    val order: Long = 0L
)

data class Opening(
    val id: String = UUID.randomUUID().toString(),
    val wallId: String,
    val type: OpeningType,
    val centerT: Float,
    val widthFeet: Float,
    val order: Long = 0L
)