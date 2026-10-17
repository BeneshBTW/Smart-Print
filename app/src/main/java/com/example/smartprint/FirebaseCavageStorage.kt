package com.example.smartprint

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class FirebaseCanvasStorage {

    private val db = FirebaseFirestore.getInstance()

    suspend fun saveCanvasToCloud(doc: CanvasDoc, data: CanvasData): Boolean {
        return try {
            val payload = hashMapOf(
                "id" to doc.id,
                "name" to doc.name,
                "createdAt" to doc.createdAt,
                "updatedAt" to System.currentTimeMillis(),
                "isDeleted" to doc.isDeleted,
                "pixelsPerFoot" to data.pixelsPerFoot,
                "lines" to data.lines.map {
                    hashMapOf(
                        "id" to it.id,
                        "sx" to it.sx,
                        "sy" to it.sy,
                        "ex" to it.ex,
                        "ey" to it.ey,
                        "thickness" to it.thickness,
                        "wallType" to it.wallType,
                        "order" to it.order
                    )
                },
                "openings" to data.openings.map {
                    hashMapOf(
                        "id" to it.id,
                        "wallId" to it.wallId,
                        "type" to it.type,
                        "centerT" to it.centerT,
                        "widthFeet" to it.widthFeet,
                        "order" to it.order
                    )
                }
            )

            db.collection("smartprint_canvases")
                .document(doc.id)
                .set(payload)
                .await()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}