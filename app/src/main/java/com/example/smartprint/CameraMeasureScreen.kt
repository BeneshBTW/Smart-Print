package com.example.smartprint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private enum class MeasureMode {
    Reference,
    Measure
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraMeasureScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm = remember { CameraMeasureViewModel() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(MeasureMode.Reference) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { photo ->
        if (photo != null) {
            bitmap = photo
            vm.onImageChosen("camera_photo")
            mode = MeasureMode.Reference
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            bitmap = loadBitmapFromUri(context, uri)
            vm.onImageChosen(uri.toString())
            mode = MeasureMode.Reference
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera Measure") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { cameraLauncher.launch(null) }) {
                    Text("Take Photo")
                }

                OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Text("Choose Image")
                }
            }

            Text(
                text = when (mode) {
                    MeasureMode.Reference -> "Step 1: Tap two points on a known distance."
                    MeasureMode.Measure -> "Step 2: Tap two points to estimate a distance."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = vm.referenceFeet,
                    onValueChange = {
                        vm.referenceFeet = it
                        vm.computeReferenceScale()
                        vm.computeMeasuredDistance()
                    },
                    label = { Text("Known distance (ft)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = { mode = MeasureMode.Measure },
                    enabled = vm.pixelsPerFoot != null
                ) {
                    Text("Measure")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFEFEFEF))
                    .pointerInput(bitmap, mode) {
                        detectTapGestures { tap ->
                            if (bitmap == null) return@detectTapGestures

                            when (mode) {
                                MeasureMode.Reference -> vm.setReferencePoint(tap)
                                MeasureMode.Measure -> vm.setMeasurePoint(tap)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val currentBitmap = bitmap

                if (currentBitmap == null) {
                    Text("Take or choose a photo to begin.")
                } else {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = "Measurement photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    MeasurementOverlay(vm = vm)
                }
            }

            Text(
                text = buildStatusText(vm),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.clearPoints()
                        mode = MeasureMode.Reference
                    }
                ) {
                    Text("Clear Points")
                }

                OutlinedButton(
                    onClick = {
                        vm.measurePointA = null
                        vm.measurePointB = null
                        vm.measuredFeet = null
                        mode = MeasureMode.Measure
                    },
                    enabled = vm.pixelsPerFoot != null
                ) {
                    Text("New Measurement")
                }
            }
        }
    }
}

@Composable
private fun MeasurementOverlay(vm: CameraMeasureViewModel) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val refA = vm.referencePointA
        val refB = vm.referencePointB
        val meaA = vm.measurePointA
        val meaB = vm.measurePointB

        if (refA != null) {
            drawCircle(Color.Blue, radius = 8f, center = refA)
        }

        if (refB != null) {
            drawCircle(Color.Blue, radius = 8f, center = refB)
            drawLine(Color.Blue, start = refA!!, end = refB, strokeWidth = 5f)
        }

        if (meaA != null) {
            drawCircle(Color.Red, radius = 8f, center = meaA)
        }

        if (meaB != null) {
            drawCircle(Color.Red, radius = 8f, center = meaB)
            drawLine(Color.Red, start = meaA!!, end = meaB, strokeWidth = 5f)
        }
    }
}

private fun buildStatusText(vm: CameraMeasureViewModel): String {
    return when {
        vm.pixelsPerFoot == null ->
            "Reference scale not set yet."

        vm.measuredFeet == null ->
            "Reference set. Now tap two points to measure."

        else ->
            "Estimated distance: ${"%.2f".format(vm.measuredFeet)} ft"
    }
}

private fun loadBitmapFromUri(context: Context, uri: android.net.Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}