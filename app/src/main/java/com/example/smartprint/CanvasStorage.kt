package com.example.smartprint

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class CanvasStorage(private val context: Context) {

    private val docsFile: File get() = File(context.filesDir, "docs.json")
    private fun canvasFile(id: String): File = File(context.filesDir, "canvas_$id.json")

    fun loadDocs(): List<CanvasDoc> {
        if (!docsFile.exists()) return emptyList()
        val txt = docsFile.readText()
        if (txt.isBlank()) return emptyList()
        val arr = JSONArray(txt)
        return (0 until arr.length()).map { i -> docFromJson(arr.getJSONObject(i)) }
    }

    fun saveDocs(docs: List<CanvasDoc>) {
        val arr = JSONArray()
        docs.forEach { arr.put(docToJson(it)) }
        docsFile.writeText(arr.toString())
    }

    fun createNewDocAutoNamed(existingDocs: List<CanvasDoc>): CanvasDoc {
        val nextN = nextPrintNumber(existingDocs)
        val now = System.currentTimeMillis()
        return CanvasDoc(
            id = UUID.randomUUID().toString(),
            name = "Print $nextN",
            createdAt = now,
            updatedAt = now,
            isDeleted = false
        )
    }

    fun saveCanvasData(id: String, data: CanvasData) {
        val obj = JSONObject()
        obj.put("pixelsPerFoot", data.pixelsPerFoot)

        val linesArr = JSONArray()
        data.lines.forEach { ln ->
            val o = JSONObject()
            o.put("id", ln.id)
            o.put("sx", ln.sx)
            o.put("sy", ln.sy)
            o.put("ex", ln.ex)
            o.put("ey", ln.ey)
            o.put("thickness", ln.thickness)
            o.put("wallType", ln.wallType)
            o.put("order", ln.order)
            linesArr.put(o)
        }
        obj.put("lines", linesArr)

        val openingsArr = JSONArray()
        data.openings.forEach { op ->
            val o = JSONObject()
            o.put("id", op.id)
            o.put("wallId", op.wallId)
            o.put("type", op.type)
            o.put("centerT", op.centerT)
            o.put("widthFeet", op.widthFeet)
            o.put("order", op.order)
            openingsArr.put(o)
        }
        obj.put("openings", openingsArr)

        canvasFile(id).writeText(obj.toString())
    }

    fun loadCanvasData(id: String): CanvasData? {
        val f = canvasFile(id)
        if (!f.exists()) return null
        val txt = f.readText()
        if (txt.isBlank()) return null
        val obj = JSONObject(txt)

        val ppf = obj.optDouble("pixelsPerFoot", 80.0).toFloat()

        val linesArr = obj.optJSONArray("lines") ?: JSONArray()
        val lines = (0 until linesArr.length()).map { i ->
            val o = linesArr.getJSONObject(i)
            CanvasLineData(
                id = o.optString("id", UUID.randomUUID().toString()),
                sx = o.getDouble("sx").toFloat(),
                sy = o.getDouble("sy").toFloat(),
                ex = o.getDouble("ex").toFloat(),
                ey = o.getDouble("ey").toFloat(),
                thickness = o.optDouble("thickness", 12.0).toFloat(),
                wallType = o.optString("wallType", "INTERIOR"),
                order = o.optLong("order", (i + 1).toLong())
            )
        }

        val openingsArr = obj.optJSONArray("openings") ?: JSONArray()
        val openings = (0 until openingsArr.length()).map { i ->
            val o = openingsArr.getJSONObject(i)
            CanvasOpeningData(
                id = o.optString("id", UUID.randomUUID().toString()),
                wallId = o.optString("wallId", ""),
                type = o.optString("type", "DOOR"),
                centerT = o.optDouble("centerT", 0.5).toFloat(),
                widthFeet = o.optDouble("widthFeet", 3.0).toFloat(),
                order = o.optLong("order", (lines.size + i + 1).toLong())
            )
        }

        return CanvasData(
            pixelsPerFoot = ppf,
            lines = lines,
            openings = openings
        )
    }

    fun permanentDelete(id: String) {
        val f = canvasFile(id)
        if (f.exists()) f.delete()
    }

    private fun nextPrintNumber(docs: List<CanvasDoc>): Int {
        var best = 0
        docs.forEach { d ->
            val n = parsePrintNumber(d.name)
            if (n > best) best = n
        }
        return best + 1
    }

    private fun parsePrintNumber(name: String): Int {
        val parts = name.trim().split(" ")
        if (parts.size != 2) return 0
        if (!parts[0].equals("Print", ignoreCase = true)) return 0
        return parts[1].toIntOrNull() ?: 0
    }

    private fun docToJson(d: CanvasDoc): JSONObject =
        JSONObject()
            .put("id", d.id)
            .put("name", d.name)
            .put("createdAt", d.createdAt)
            .put("updatedAt", d.updatedAt)
            .put("isDeleted", d.isDeleted)

    private fun docFromJson(o: JSONObject): CanvasDoc =
        CanvasDoc(
            id = o.getString("id"),
            name = o.getString("name"),
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L),
            isDeleted = o.optBoolean("isDeleted", false)
        )
}