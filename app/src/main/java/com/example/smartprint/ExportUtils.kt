package com.example.smartprint

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

object ExportUtils {

    fun exportCanvasToPdf(context: Context, vm: SmartPrintViewModel): Boolean {
        if (vm.walls.isEmpty()) return false

        val pageWidth = 792
        val pageHeight = 612
        val padding = 60f

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)

        val bounds = calculateBounds(vm.walls)
        val drawingWidth = bounds.maxX - bounds.minX
        val drawingHeight = bounds.maxY - bounds.minY

        val scale = min(
            (pageWidth - padding * 2) / drawingWidth.coerceAtLeast(1f),
            (pageHeight - padding * 2) / drawingHeight.coerceAtLeast(1f)
        )

        fun worldToPdf(point: Offset): Offset {
            val x = padding + (point.x - bounds.minX) * scale
            val y = padding + (point.y - bounds.minY) * scale
            return Offset(x, y)
        }

        drawTitle(canvas)

        vm.walls.forEach { wall ->
            drawWall(canvas, vm, wall, ::worldToPdf, scale)
        }

        pdfDocument.finishPage(page)

        val fileName = "SmartPrint_${timestamp()}.pdf"

        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/SmartPrint")
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    pdfDocument.writeTo(output)
                }
                pdfDocument.close()
                true
            } else {
                pdfDocument.close()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            false
        }
    }

    private fun drawTitle(canvas: Canvas) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 22f
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText("SmartPrint Export", 40f, 35f, paint)
    }

    private fun drawWall(
        canvas: Canvas,
        vm: SmartPrintViewModel,
        wall: Wall,
        worldToPdf: (Offset) -> Offset,
        scale: Float
    ) {
        val start = worldToPdf(wall.start)
        val end = worldToPdf(wall.end)

        val wallPaint = Paint().apply {
            isAntiAlias = true
            color = if (wall.type == WallType.EXTERIOR) Color.DKGRAY else Color.GRAY
            strokeWidth = (wall.thickness * scale).coerceIn(4f, 16f)
            strokeCap = Paint.Cap.SQUARE
        }

        canvas.drawLine(start.x, start.y, end.x, end.y, wallPaint)

        drawDimensionLabel(canvas, start, end, "%.2f ft".format(vm.wallLengthFeet(wall)))

        vm.openingsForWall(wall.id).forEach { opening ->
            drawOpening(canvas, wall, opening, vm.pixelsPerFoot, worldToPdf)
        }
    }

    private fun drawOpening(
        canvas: Canvas,
        wall: Wall,
        opening: Opening,
        pixelsPerFoot: Float,
        worldToPdf: (Offset) -> Offset
    ) {
        val wallLength = distance(wall.start, wall.end)
        if (wallLength <= 1f) return

        val dir = Offset(
            (wall.end.x - wall.start.x) / wallLength,
            (wall.end.y - wall.start.y) / wallLength
        )

        val center = lerp(wall.start, wall.end, opening.centerT)
        val halfWidth = opening.widthFeet * pixelsPerFoot / 2f

        val a = worldToPdf(center - dir * halfWidth)
        val b = worldToPdf(center + dir * halfWidth)

        val paint = Paint().apply {
            isAntiAlias = true
            color = if (opening.type == OpeningType.DOOR) Color.rgb(125, 90, 60) else Color.rgb(60, 120, 180)
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }

        canvas.drawLine(a.x, a.y, b.x, b.y, paint)
    }

    private fun drawDimensionLabel(canvas: Canvas, start: Offset, end: Offset, text: String) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(50, 80, 180)
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)

        val dx = end.x - start.x
        val dy = end.y - start.y

        val labelOffset = if (abs(dx) >= abs(dy)) {
            Offset(0f, -18f)
        } else {
            Offset(22f, 0f)
        }

        val labelPoint = mid + labelOffset

        val textWidth = paint.measureText(text)
        canvas.drawRoundRect(
            labelPoint.x - textWidth / 2f - 6f,
            labelPoint.y - 18f,
            labelPoint.x + textWidth / 2f + 6f,
            labelPoint.y + 6f,
            6f,
            6f,
            bgPaint
        )

        canvas.drawText(text, labelPoint.x, labelPoint.y, paint)
    }

    private fun calculateBounds(walls: List<Wall>): Bounds {
        val points = walls.flatMap { listOf(it.start, it.end) }

        return Bounds(
            minX = points.minOf { it.x },
            maxX = points.maxOf { it.x },
            minY = points.minOf { it.y },
            maxY = points.maxOf { it.y }
        )
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun distance(a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun lerp(a: Offset, b: Offset, t: Float): Offset {
        return Offset(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t
        )
    }

    private operator fun Offset.plus(other: Offset): Offset {
        return Offset(x + other.x, y + other.y)
    }

    private operator fun Offset.minus(other: Offset): Offset {
        return Offset(x - other.x, y - other.y)
    }

    private operator fun Offset.times(value: Float): Offset {
        return Offset(x * value, y * value)
    }

    private data class Bounds(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    )
}