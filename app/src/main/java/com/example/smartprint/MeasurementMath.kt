package com.example.smartprint

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

object MeasurementMath {

    fun pixelDistance(a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    fun computePixelsPerFoot(
        referenceA: Offset,
        referenceB: Offset,
        realFeet: Float
    ): Float {
        if (realFeet <= 0f) return 1f
        return pixelDistance(referenceA, referenceB) / realFeet
    }

    fun computeMeasuredFeet(
        pointA: Offset,
        pointB: Offset,
        pixelsPerFoot: Float
    ): Float {
        if (pixelsPerFoot <= 0f) return 0f
        return pixelDistance(pointA, pointB) / pixelsPerFoot
    }
}