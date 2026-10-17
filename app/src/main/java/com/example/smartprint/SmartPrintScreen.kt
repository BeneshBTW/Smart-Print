package com.example.smartprint

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SmartPrintScreen(
    vm: SmartPrintViewModel,
    activeDoc: CanvasDoc,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val firebaseStorage = remember { FirebaseCanvasStorage() }

    var lengthInput by remember { mutableStateOf("") }
    var openingWidthInput by remember { mutableStateOf("") }
    var sidePanelOpen by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val selectedLength = vm.getSelectedWallLengthFeet()
    val selectedWall = vm.getSelectedWall()
    val selectedOpening = vm.getSelectedOpening()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6F8))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            SmartPrintTopBar(
                toolMode = vm.toolMode,
                vm = vm,
                onBack = onBack,
                onRequestClear = { showClearConfirm = true },
                onExportPdf = {
                    val success = ExportUtils.exportCanvasToPdf(context, vm)

                    Toast.makeText(
                        context,
                        if (success) "Exported PDF to Downloads/SmartPrint" else "Nothing to Export",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onCloudSave = {
                    CoroutineScope(Dispatchers.Main).launch {
                        val success = firebaseStorage.saveCanvasToCloud(
                            doc = activeDoc,
                            data = vm.exportData()
                        )

                        Toast.makeText(
                            context,
                            if (success) "Saved to Firebase Cloud" else "Cloud save failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clipToBounds()
                    .background(Color.White)
            ) {
                SmartPrintCanvas(
                    vm = vm,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        RightSidePanel(
            vm = vm,
            isOpen = sidePanelOpen,
            onToggle = { sidePanelOpen = !sidePanelOpen },
            onSetOpen = { sidePanelOpen = it },
            selectedWall = selectedWall,
            selectedOpening = selectedOpening,
            selectedLength = selectedLength,
            lengthInput = lengthInput,
            onLengthInputChange = { lengthInput = it },
            onApplyLength = {
                val value = lengthInput.toFloatOrNull()
                if (value != null && value > 0f) {
                    vm.setSelectedWallLength(value)
                    lengthInput = ""
                }
            },
            openingWidthInput = openingWidthInput,
            onOpeningWidthInputChange = { openingWidthInput = it },
            onApplyOpeningWidth = {
                val value = openingWidthInput.toFloatOrNull()
                if (value != null && value > 0f) {
                    vm.setSelectedOpeningWidthFeet(value)
                    openingWidthInput = ""
                }
            }
        )

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear canvas?") },
                text = { Text("This will remove all walls and openings. You can undo it afterward.") },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.clear()
                            showClearConfirm = false
                        }
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showClearConfirm = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SmartPrintTopBar(
    toolMode: ToolMode,
    vm: SmartPrintViewModel,
    onBack: () -> Unit,
    onRequestClear: () -> Unit,
    onExportPdf: () -> Unit,
    onCloudSave: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onBack) {
                    Text("Back")
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "SmartPrint",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = when (toolMode) {
                            ToolMode.DRAW_WALL -> "Draw Wall"
                            ToolMode.SELECT_WALL -> "Select / Edit"
                            ToolMode.PLACE_DOOR -> "Place Door"
                            ToolMode.PLACE_WINDOW -> "Place Window"
                            ToolMode.PAN -> "Pan / Zoom"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { vm.undo() },
                    enabled = vm.canUndo
                ) {
                    Text("Undo")
                }

                TextButton(
                    onClick = { vm.redo() },
                    enabled = vm.canRedo
                ) {
                    Text("Redo")
                }

                TextButton(
                    onClick = onRequestClear
                ) {
                    Text("Clear")
                }

                TextButton(
                    onClick = onExportPdf,
                    enabled = vm.walls.isNotEmpty()
                ) {
                    Text("Export PDF")
                }

                TextButton(
                    onClick = onCloudSave,
                    enabled = vm.walls.isNotEmpty()
                ) {
                    Text("Save Cloud")
                }

                TextButton(
                    onClick = { vm.resetView() }
                ) {
                    Text("Reset View")
                }
            }
        }
    }
}

@Composable
private fun BoxScope.RightSidePanel(
    vm: SmartPrintViewModel,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onSetOpen: (Boolean) -> Unit,
    selectedWall: Wall?,
    selectedOpening: Opening?,
    selectedLength: Float?,
    lengthInput: String,
    onLengthInputChange: (String) -> Unit,
    onApplyLength: () -> Unit,
    openingWidthInput: String,
    onOpeningWidthInputChange: (String) -> Unit,
    onApplyOpeningWidth: () -> Unit
) {
    val density = LocalDensity.current
    val panelWidthDp = 260.dp
    val handleWidthDp = 16.dp

    val panelWidthPx = with(density) { panelWidthDp.toPx() }
    val handleWidthPx = with(density) { handleWidthDp.toPx() }

    val closedOffsetPx = panelWidthPx - handleWidthPx
    val animatedOffsetX by animateFloatAsState(
        targetValue = if (isOpen) 0f else closedOffsetPx,
        animationSpec = tween(durationMillis = 220),
        label = "sidePanelOffset"
    )

    Surface(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(top = 110.dp, bottom = 18.dp)
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .width(panelWidthDp)
            .navigationBarsPadding()
            .pointerInput(isOpen) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        when {
                            totalDrag < -40f -> onSetOpen(true)
                            totalDrag > 40f -> onSetOpen(false)
                        }
                    },
                    onDragCancel = {}
                )
            },
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.White
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            SidePanelHandle(
                isOpen = isOpen,
                onToggle = onToggle
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Controls",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = when (vm.toolMode) {
                        ToolMode.DRAW_WALL -> "Draw walls with snap and axis lock."
                        ToolMode.SELECT_WALL -> "Select walls or openings to edit."
                        ToolMode.PLACE_DOOR -> "Tap a wall to place a 3 ft door."
                        ToolMode.PLACE_WINDOW -> "Tap a wall to place a 4 ft window."
                        ToolMode.PAN -> "Use gestures to pan and zoom."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider()

                Text(
                    text = "Tools",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SideToolButton(
                            selected = vm.toolMode == ToolMode.DRAW_WALL,
                            onClick = { vm.changeToolMode(ToolMode.DRAW_WALL) },
                            label = "Wall"
                        )
                        SideToolButton(
                            selected = vm.toolMode == ToolMode.SELECT_WALL,
                            onClick = { vm.changeToolMode(ToolMode.SELECT_WALL) },
                            label = "Select"
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SideToolButton(
                            selected = vm.toolMode == ToolMode.PLACE_DOOR,
                            onClick = { vm.changeToolMode(ToolMode.PLACE_DOOR) },
                            label = "Door"
                        )
                        SideToolButton(
                            selected = vm.toolMode == ToolMode.PLACE_WINDOW,
                            onClick = { vm.changeToolMode(ToolMode.PLACE_WINDOW) },
                            label = "Window"
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SideToolButton(
                            selected = vm.toolMode == ToolMode.PAN,
                            onClick = { vm.changeToolMode(ToolMode.PAN) },
                            label = "Pan"
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Scale: ${vm.pixelsPerFoot.toInt()} px = 1 ft",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )

                Slider(
                    value = vm.pixelsPerFoot,
                    onValueChange = { vm.updatePixelsPerFoot(it) },
                    valueRange = 10f..120f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Grid Snap",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = vm.gridSnapEnabled,
                        onCheckedChange = { vm.toggleGridSnap(it) }
                    )
                }

                Text(
                    text = "New Wall Type",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = vm.currentDrawWallType == WallType.INTERIOR,
                        onClick = { vm.changeDrawWallType(WallType.INTERIOR) },
                        label = { Text("Interior") }
                    )

                    FilterChip(
                        selected = vm.currentDrawWallType == WallType.EXTERIOR,
                        onClick = { vm.changeDrawWallType(WallType.EXTERIOR) },
                        label = { Text("Exterior") }
                    )
                }

                if (selectedWall != null || selectedOpening != null) {
                    HorizontalDivider()

                    Text(
                        text = "Inspector",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (selectedOpening != null) {
                    Text(
                        text = "Selected ${
                            selectedOpening.type.name.lowercase()
                                .replaceFirstChar { it.uppercase() }
                        }",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Set Width (ft)",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = openingWidthInput,
                            onValueChange = onOpeningWidthInputChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(onClick = onApplyOpeningWidth) {
                            Text("Apply")
                        }
                    }

                    OutlinedButton(
                        onClick = { vm.deleteSelectedOpening() }
                    ) {
                        Text("Delete")
                    }
                } else if (selectedWall != null) {
                    Text(
                        text = if (selectedLength != null) {
                            "Selected Wall Length: ${"%.2f".format(selectedLength)} ft"
                        } else {
                            "Selected Wall"
                        },
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Wall Type",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedWall.type == WallType.INTERIOR,
                            onClick = { vm.setSelectedWallType(WallType.INTERIOR) },
                            label = { Text("Interior") }
                        )

                        FilterChip(
                            selected = selectedWall.type == WallType.EXTERIOR,
                            onClick = { vm.setSelectedWallType(WallType.EXTERIOR) },
                            label = { Text("Exterior") }
                        )
                    }

                    Text(
                        text = "Set Wall Length (ft)",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = lengthInput,
                            onValueChange = onLengthInputChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(onClick = onApplyLength) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidePanelHandle(
    isOpen: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(16.dp)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FilledTonalButton(
            onClick = onToggle,
            modifier = Modifier
                .height(72.dp)
                .width(12.dp),
            shape = RoundedCornerShape(
                topStart = 10.dp,
                bottomStart = 10.dp,
                topEnd = 0.dp,
                bottomEnd = 0.dp
            )
        ) {
            Text(
                text = if (isOpen) "›" else "‹",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SideToolButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun SmartPrintScreenPreview() {
    MaterialTheme {
        SmartPrintScreen(
            vm = SmartPrintViewModel(),
            activeDoc = CanvasDoc(
                id = "preview",
                name = "Preview",
                createdAt = 0L,
                updatedAt = 0L,
                isDeleted = false
            ),
            onBack = {}
        )
    }
}