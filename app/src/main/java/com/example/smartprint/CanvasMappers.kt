package com.example.smartprint

import androidx.compose.ui.geometry.Offset

fun Wall.toCanvasLineData(): CanvasLineData {
    return CanvasLineData(
        id = id,
        sx = start.x,
        sy = start.y,
        ex = end.x,
        ey = end.y,
        thickness = thickness,
        wallType = type.name,
        order = order
    )
}

fun Opening.toCanvasOpeningData(): CanvasOpeningData {
    return CanvasOpeningData(
        id = id,
        wallId = wallId,
        type = type.name,
        centerT = centerT,
        widthFeet = widthFeet,
        order = order
    )
}

fun CanvasLineData.toWall(): Wall {
    return Wall(
        id = id,
        start = Offset(sx, sy),
        end = Offset(ex, ey),
        thickness = thickness,
        type = wallType.toWallType(),
        order = order
    )
}

fun CanvasOpeningData.toOpeningOrNull(validWallIds: Set<String>): Opening? {
    if (wallId !in validWallIds) return null

    return Opening(
        id = id,
        wallId = wallId,
        type = type.toOpeningType(),
        centerT = centerT,
        widthFeet = widthFeet,
        order = order
    )
}

fun String.toOpeningType(): OpeningType {
    return when (uppercase()) {
        "WINDOW" -> OpeningType.WINDOW
        else -> OpeningType.DOOR
    }
}

fun String.toWallType(): WallType {
    return when (uppercase()) {
        "EXTERIOR" -> WallType.EXTERIOR
        else -> WallType.INTERIOR
    }
}