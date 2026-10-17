package com.example.smartprint

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

fun thicknessForType(type: WallType): Float {
    return when (type) {
        WallType.INTERIOR -> 12f
        WallType.EXTERIOR -> 18f
    }
}

fun snapPoint(point: Offset, enabled: Boolean, step: Float): Offset {
    if (!enabled) return point

    fun snapValue(v: Float): Float {
        return (v / step).roundToInt() * step
    }

    return Offset(
        snapValue(point.x),
        snapValue(point.y)
    )
}

fun axisLockPoint(start: Offset, current: Offset): Offset {
    val dx = current.x - start.x
    val dy = current.y - start.y

    return if (abs(dx) > abs(dy)) {
        Offset(current.x, start.y)
    } else {
        Offset(start.x, current.y)
    }
}

fun projectionT(point: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val ap = point - a
    val abLenSq = ab.x * ab.x + ab.y * ab.y
    if (abLenSq <= 0f) return 0f
    return (((ap.x * ab.x) + (ap.y * ab.y)) / abLenSq).coerceIn(0f, 1f)
}

fun distancePointToSegment(point: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val ap = point - a
    val abLenSq = ab.x * ab.x + ab.y * ab.y

    if (abLenSq == 0f) {
        val dx = point.x - a.x
        val dy = point.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    val t = ((ap.x * ab.x) + (ap.y * ab.y)) / abLenSq
    val clampedT = t.coerceIn(0f, 1f)

    val closest = Offset(
        a.x + ab.x * clampedT,
        a.y + ab.y * clampedT
    )

    val dx = point.x - closest.x
    val dy = point.y - closest.y
    return sqrt(dx * dx + dy * dy)
}

fun distance(a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
}

fun lerpWorld(a: Offset, b: Offset, t: Float): Offset {
    return Offset(
        a.x + (b.x - a.x) * t,
        a.y + (b.y - a.y) * t
    )
}

fun normalizeSafe(v: Offset): Offset {
    val len = sqrt(v.x * v.x + v.y * v.y)
    if (len <= 0.0001f) return Offset.Zero
    return Offset(v.x / len, v.y / len)
}

fun lengthFeet(start: Offset, end: Offset, pixelsPerFoot: Float): Float {
    return distance(start, end) / pixelsPerFoot
}