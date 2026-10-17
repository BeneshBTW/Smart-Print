package com.example.smartprint

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

@Composable
fun SmartPrintCanvas(
    vm: SmartPrintViewModel,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 30f
            color = android.graphics.Color.DKGRAY
        }
    }

    val textBgPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(vm.toolMode) {
                when (vm.toolMode) {
                    ToolMode.SELECT_WALL -> {
                        detectTapGestures(
                            onTap = { offset ->
                                vm.onSelectTap(vm.screenToWorld(offset))
                            }
                        )
                    }

                    ToolMode.PLACE_DOOR -> {
                        detectTapGestures(
                            onTap = { offset ->
                                vm.placeOpeningAt(vm.screenToWorld(offset), OpeningType.DOOR)
                            }
                        )
                    }

                    ToolMode.PLACE_WINDOW -> {
                        detectTapGestures(
                            onTap = { offset ->
                                vm.placeOpeningAt(vm.screenToWorld(offset), OpeningType.WINDOW)
                            }
                        )
                    }

                    else -> {}
                }
            }
            .pointerInput(vm.toolMode) {
                when (vm.toolMode) {
                    ToolMode.PAN -> {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            vm.onPanZoom(centroid = centroid, pan = pan, zoomChange = zoom)
                        }
                    }

                    ToolMode.DRAW_WALL,
                    ToolMode.SELECT_WALL -> {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val worldPoint = vm.screenToWorld(offset)
                                when (vm.toolMode) {
                                    ToolMode.DRAW_WALL -> vm.startWall(worldPoint)
                                    ToolMode.SELECT_WALL -> vm.onSelectDragStart(worldPoint)
                                    else -> {}
                                }
                            },
                            onDrag = { change, _ ->
                                val worldPoint = vm.screenToWorld(change.position)
                                when (vm.toolMode) {
                                    ToolMode.DRAW_WALL -> vm.updateWall(worldPoint)
                                    ToolMode.SELECT_WALL -> vm.onSelectDrag(worldPoint)
                                    else -> {}
                                }
                            },
                            onDragEnd = {
                                when (vm.toolMode) {
                                    ToolMode.DRAW_WALL -> vm.endWall()
                                    ToolMode.SELECT_WALL -> vm.onSelectDragEnd()
                                    else -> {}
                                }
                            }
                        )
                    }

                    else -> {}
                }
            }
    ) {
        val oneFootWorld = vm.pixelsPerFoot
        val majorStepWorld = oneFootWorld * 4f

        val minorStepScreen = oneFootWorld * vm.zoomScale
        val drawMinorGrid = minorStepScreen >= 14f

        val minorGridColor = Color(0xFFEAEAEA)
        val majorGridColor = Color(0xFFD5D5D5)

        val worldLeft = vm.screenToWorld(Offset(0f, 0f)).x
        val worldTop = vm.screenToWorld(Offset(0f, 0f)).y
        val worldRight = vm.screenToWorld(Offset(size.width, size.height)).x
        val worldBottom = vm.screenToWorld(Offset(size.width, size.height)).y

        if (drawMinorGrid) {
            var xMinor = floor(worldLeft / oneFootWorld) * oneFootWorld
            while (xMinor <= worldRight) {
                val start = vm.worldToScreen(Offset(xMinor, worldTop))
                val end = vm.worldToScreen(Offset(xMinor, worldBottom))
                drawLine(color = minorGridColor, start = start, end = end, strokeWidth = 1f)
                xMinor += oneFootWorld
            }

            var yMinor = floor(worldTop / oneFootWorld) * oneFootWorld
            while (yMinor <= worldBottom) {
                val start = vm.worldToScreen(Offset(worldLeft, yMinor))
                val end = vm.worldToScreen(Offset(worldRight, yMinor))
                drawLine(color = minorGridColor, start = start, end = end, strokeWidth = 1f)
                yMinor += oneFootWorld
            }
        }

        var xMajor = floor(worldLeft / majorStepWorld) * majorStepWorld
        while (xMajor <= worldRight) {
            val start = vm.worldToScreen(Offset(xMajor, worldTop))
            val end = vm.worldToScreen(Offset(xMajor, worldBottom))
            drawLine(color = majorGridColor, start = start, end = end, strokeWidth = 1.2f)
            xMajor += majorStepWorld
        }

        var yMajor = floor(worldTop / majorStepWorld) * majorStepWorld
        while (yMajor <= worldBottom) {
            val start = vm.worldToScreen(Offset(worldLeft, yMajor))
            val end = vm.worldToScreen(Offset(worldRight, yMajor))
            drawLine(color = majorGridColor, start = start, end = end, strokeWidth = 1.2f)
            yMajor += majorStepWorld
        }

        val wallPoints = vm.walls.flatMap { listOf(it.start, it.end) }
        val planCenterWorld = if (wallPoints.isNotEmpty()) {
            val minX = wallPoints.minOf { it.x }
            val maxX = wallPoints.maxOf { it.x }
            val minY = wallPoints.minOf { it.y }
            val maxY = wallPoints.maxOf { it.y }
            Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
        } else {
            Offset.Zero
        }
        val planCenterScreen = vm.worldToScreen(planCenterWorld)

        vm.walls.forEach { wall ->
            val isSelectedWall = vm.selectedWallId == wall.id
            val startScreen = vm.worldToScreen(wall.start)
            val endScreen = vm.worldToScreen(wall.end)
            val wallStroke = wall.thickness * vm.zoomScale

            val wallColor = when {
                isSelectedWall -> Color(0xFF3F51B5)
                wall.type == WallType.EXTERIOR -> Color(0xFF4F4F4F)
                else -> Color(0xFF6A6A6A)
            }

            val openingsOnWall = vm.openingsForWall(wall.id)
            val wallLengthWorld = distance(wall.start, wall.end)

            if (openingsOnWall.isEmpty()) {
                drawLine(
                    color = wallColor,
                    start = startScreen,
                    end = endScreen,
                    strokeWidth = wallStroke,
                    cap = StrokeCap.Square
                )

                drawWallDimension(
                    start = startScreen,
                    end = endScreen,
                    text = "%.2f ft".format(vm.wallLengthFeet(wall)),
                    isSelected = isSelectedWall,
                    wallStroke = wallStroke,
                    planCenter = planCenterScreen,
                    labelPaint = labelPaint,
                    textBgPaint = textBgPaint
                )
            } else {
                var prevT = 0f

                openingsOnWall.forEach { opening ->
                    val halfT = (opening.widthFeet * vm.pixelsPerFoot / 2f) / wallLengthWorld
                    val openStartT = (opening.centerT - halfT).coerceIn(0f, 1f)
                    val openEndT = (opening.centerT + halfT).coerceIn(0f, 1f)

                    if (openStartT > prevT) {
                        val segStartWorld = lerpWorld(wall.start, wall.end, prevT)
                        val segEndWorld = lerpWorld(wall.start, wall.end, openStartT)

                        drawLine(
                            color = wallColor,
                            start = vm.worldToScreen(segStartWorld),
                            end = vm.worldToScreen(segEndWorld),
                            strokeWidth = wallStroke,
                            cap = StrokeCap.Square
                        )

                        drawWallDimension(
                            start = vm.worldToScreen(segStartWorld),
                            end = vm.worldToScreen(segEndWorld),
                            text = "%.2f ft".format(vm.wallLengthFeet(wall) * (openStartT - prevT)),
                            isSelected = isSelectedWall,
                            wallStroke = wallStroke,
                            planCenter = planCenterScreen,
                            labelPaint = labelPaint,
                            textBgPaint = textBgPaint
                        )
                    }

                    drawOpeningWorld(
                        wallStartWorld = wall.start,
                        wallEndWorld = wall.end,
                        opening = opening,
                        isSelected = vm.selectedOpeningId == opening.id,
                        wallStroke = wallStroke,
                        planCenterWorld = planCenterWorld,
                        worldToScreen = vm::worldToScreen,
                        pixelsPerFoot = vm.pixelsPerFoot
                    )

                    prevT = openEndT
                }

                if (prevT < 1f) {
                    val segStartWorld = lerpWorld(wall.start, wall.end, prevT)
                    val segEndWorld = wall.end

                    drawLine(
                        color = wallColor,
                        start = vm.worldToScreen(segStartWorld),
                        end = vm.worldToScreen(segEndWorld),
                        strokeWidth = wallStroke,
                        cap = StrokeCap.Square
                    )

                    drawWallDimension(
                        start = vm.worldToScreen(segStartWorld),
                        end = vm.worldToScreen(segEndWorld),
                        text = "%.2f ft".format(vm.wallLengthFeet(wall) * (1f - prevT)),
                        isSelected = isSelectedWall,
                        wallStroke = wallStroke,
                        planCenter = planCenterScreen,
                        labelPaint = labelPaint,
                        textBgPaint = textBgPaint
                    )
                }
            }
        }

        val currentStart = vm.getCurrentStart()
        val currentEnd = vm.currentEnd

        if (currentStart != null && currentEnd != null) {
            val start = vm.worldToScreen(currentStart)
            val end = vm.worldToScreen(currentEnd)
            val previewStroke = if (vm.currentDrawWallType == WallType.EXTERIOR) {
                18f * vm.zoomScale
            } else {
                12f * vm.zoomScale
            }

            drawLine(
                color = Color.Gray,
                start = start,
                end = end,
                strokeWidth = previewStroke,
                cap = StrokeCap.Square
            )

            drawWallDimension(
                start = start,
                end = end,
                text = "%.2f ft".format(vm.getCurrentWallLengthFeet() ?: 0f),
                isSelected = false,
                wallStroke = previewStroke,
                planCenter = planCenterScreen,
                labelPaint = labelPaint,
                textBgPaint = textBgPaint
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOpeningWorld(
    wallStartWorld: Offset,
    wallEndWorld: Offset,
    opening: Opening,
    isSelected: Boolean,
    wallStroke: Float,
    planCenterWorld: Offset,
    worldToScreen: (Offset) -> Offset,
    pixelsPerFoot: Float
) {
    val wallVecWorld = wallEndWorld - wallStartWorld
    val wallLengthWorld = distance(wallStartWorld, wallEndWorld)
    if (wallLengthWorld <= 1f) return

    val unitWorld = Offset(wallVecWorld.x / wallLengthWorld, wallVecWorld.y / wallLengthWorld)

    val halfWidthWorld = opening.widthFeet * pixelsPerFoot / 2f
    val centerWorld = lerpWorld(wallStartWorld, wallEndWorld, opening.centerT)
    val gapStartWorld = centerWorld - unitWorld * halfWidthWorld
    val gapEndWorld = centerWorld + unitWorld * halfWidthWorld

    val wallStartScreen = worldToScreen(wallStartWorld)
    val wallEndScreen = worldToScreen(wallEndWorld)
    val gapStartScreen = worldToScreen(gapStartWorld)
    val gapEndScreen = worldToScreen(gapEndWorld)

    val wallLengthScreen = distance(wallStartScreen, wallEndScreen)
    if (wallLengthScreen <= 1f) return

    val midWallWorld = Offset(
        (wallStartWorld.x + wallEndWorld.x) / 2f,
        (wallStartWorld.y + wallEndWorld.y) / 2f
    )

    val outwardNormalWorld = if (abs(wallVecWorld.x) >= abs(wallVecWorld.y)) {
        if (midWallWorld.y < planCenterWorld.y) Offset(0f, -1f) else Offset(0f, 1f)
    } else {
        if (midWallWorld.x < planCenterWorld.x) Offset(-1f, 0f) else Offset(1f, 0f)
    }

    val outwardScreen = worldToScreen(centerWorld + outwardNormalWorld) - worldToScreen(centerWorld)
    val outwardNormalScreen = normalizeSafe(outwardScreen)
    val inwardNormalScreen = outwardNormalScreen * -1f
    val gapWidthScreen = distance(gapStartScreen, gapEndScreen)

    val accent = if (isSelected) Color(0xFFD32F2F) else Color(0xFF7D6A58)
    val windowAccent = if (isSelected) Color(0xFFD32F2F) else Color(0xFF6E9CCF)

    when (opening.type) {
        OpeningType.DOOR -> {
            val hinge = gapStartScreen
            val leafEnd = hinge + inwardNormalScreen * gapWidthScreen

            drawLine(
                color = accent,
                start = hinge,
                end = leafEnd,
                strokeWidth = if (isSelected) 5f else 3f
            )

            val startAngle = angleDeg(gapEndScreen - hinge)
            val endAngle = angleDeg(leafEnd - hinge)
            val sweep = shortestSweep(startAngle, endAngle)

            drawArc(
                color = accent,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(hinge.x - gapWidthScreen, hinge.y - gapWidthScreen),
                size = Size(gapWidthScreen * 2f, gapWidthScreen * 2f),
                style = Stroke(width = if (isSelected) 3f else 2f)
            )
        }

        OpeningType.WINDOW -> {
            val offset = wallStroke * 0.18f
            val line1Start = gapStartScreen + inwardNormalScreen * offset
            val line1End = gapEndScreen + inwardNormalScreen * offset
            val line2Start = gapStartScreen + inwardNormalScreen * (offset + 6f)
            val line2End = gapEndScreen + inwardNormalScreen * (offset + 6f)

            drawLine(
                color = windowAccent,
                start = line1Start,
                end = line1End,
                strokeWidth = if (isSelected) 5f else 3f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = windowAccent,
                start = line2Start,
                end = line2End,
                strokeWidth = if (isSelected) 5f else 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWallDimension(
    start: Offset,
    end: Offset,
    text: String,
    isSelected: Boolean,
    wallStroke: Float,
    planCenter: Offset,
    labelPaint: Paint,
    textBgPaint: Paint
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length <= 0.001f) return

    val midWall = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)

    val normal = if (abs(dx) >= abs(dy)) {
        if (midWall.y < planCenter.y) Offset(0f, -1f) else Offset(0f, 1f)
    } else {
        if (midWall.x < planCenter.x) Offset(-1f, 0f) else Offset(1f, 0f)
    }

    val offsetDistance = (wallStroke / 2f) + 18f
    val dimStart = start + normal * offsetDistance
    val dimEnd = end + normal * offsetDistance

    val tickSize = 8f
    val dimColor = if (isSelected) Color(0xFF3F51B5) else Color(0xFF5B6FB8)

    drawLine(color = dimColor, start = dimStart, end = dimEnd, strokeWidth = 2f)

    val tickDir = if (abs(dx) >= abs(dy)) Offset(0f, 1f) else Offset(1f, 0f)

    drawLine(
        color = dimColor,
        start = dimStart - tickDir * tickSize,
        end = dimStart + tickDir * tickSize,
        strokeWidth = 2f
    )

    drawLine(
        color = dimColor,
        start = dimEnd - tickDir * tickSize,
        end = dimEnd + tickDir * tickSize,
        strokeWidth = 2f
    )

    val mid = Offset((dimStart.x + dimEnd.x) / 2f, (dimStart.y + dimEnd.y) / 2f)

    labelPaint.color =
        if (isSelected) android.graphics.Color.rgb(63, 81, 181)
        else android.graphics.Color.rgb(91, 111, 184)

    val textWidth = labelPaint.measureText(text)
    val textHeight = labelPaint.textSize
    val padX = 10f
    val padY = 6f

    drawContext.canvas.nativeCanvas.drawRoundRect(
        mid.x - textWidth / 2f - padX,
        mid.y - textHeight + 2f - padY,
        mid.x + textWidth / 2f + padX,
        mid.y + 6f + padY,
        8f,
        8f,
        textBgPaint
    )

    drawContext.canvas.nativeCanvas.drawText(text, mid.x, mid.y, labelPaint)
}

private fun angleDeg(v: Offset): Float {
    return Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble())).toFloat()
}

private fun shortestSweep(startDeg: Float, endDeg: Float): Float {
    var delta = endDeg - startDeg
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

private operator fun Offset.times(value: Float): Offset {
    return Offset(x * value, y * value)
}