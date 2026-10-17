package com.example.smartprint

data class CanvasDoc(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean
)

data class CanvasLineData(
    val id: String,
    val sx: Float,
    val sy: Float,
    val ex: Float,
    val ey: Float,
    val thickness: Float,
    val wallType: String,
    val order: Long
)

data class CanvasOpeningData(
    val id: String,
    val wallId: String,
    val type: String,
    val centerT: Float,
    val widthFeet: Float,
    val order: Long
)

data class CanvasData(
    val pixelsPerFoot: Float,
    val lines: List<CanvasLineData>,
    val openings: List<CanvasOpeningData> = emptyList()
)