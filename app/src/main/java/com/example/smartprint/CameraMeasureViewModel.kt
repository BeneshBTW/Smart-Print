package com.example.smartprint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

class CameraMeasureViewModel {

    var imageUri by mutableStateOf<String?>(null)

    var referencePointA by mutableStateOf<Offset?>(null)
    var referencePointB by mutableStateOf<Offset?>(null)

    var measurePointA by mutableStateOf<Offset?>(null)
    var measurePointB by mutableStateOf<Offset?>(null)

    var referenceFeet by mutableStateOf("3.0")

    var pixelsPerFoot by mutableStateOf<Float?>(null)
    var measuredFeet by mutableStateOf<Float?>(null)

    fun onImageChosen(uri: String) {
        imageUri = uri
        clearPoints()
    }

    fun setReferencePoint(point: Offset) {
        if (referencePointA == null) {
            referencePointA = point
        } else if (referencePointB == null) {
            referencePointB = point
            computeReferenceScale()
        }
    }

    fun setMeasurePoint(point: Offset) {
        if (measurePointA == null) {
            measurePointA = point
        } else if (measurePointB == null) {
            measurePointB = point
            computeMeasuredDistance()
        }
    }

    fun computeReferenceScale() {
        val a = referencePointA
        val b = referencePointB
        val feet = referenceFeet.toFloatOrNull()

        if (a != null && b != null && feet != null && feet > 0f) {
            pixelsPerFoot = MeasurementMath.computePixelsPerFoot(a, b, feet)
        }
    }

    fun computeMeasuredDistance() {
        val a = measurePointA
        val b = measurePointB
        val ppf = pixelsPerFoot

        if (a != null && b != null && ppf != null) {
            measuredFeet = MeasurementMath.computeMeasuredFeet(a, b, ppf)
        }
    }

    fun clearPoints() {
        referencePointA = null
        referencePointB = null
        measurePointA = null
        measurePointB = null
        measuredFeet = null
        pixelsPerFoot = null
    }
}