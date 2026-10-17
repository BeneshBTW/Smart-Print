package com.example.smartprint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class GalleryTab { Recent, Recycle }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    docs: List<CanvasDoc>,
    onCreateNew: () -> Unit,
    onOpen: (CanvasDoc) -> Unit,
    onDelete: (CanvasDoc) -> Unit,
    onRestore: (CanvasDoc) -> Unit,
    onDeleteForever: (CanvasDoc) -> Unit,
    onOpenCameraMeasure: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(GalleryTab.Recent) }

    val shownDocs = remember(docs, search, tab) {
        val base = when (tab) {
            GalleryTab.Recent -> docs.filter { !it.isDeleted }
            GalleryTab.Recycle -> docs.filter { it.isDeleted }
        }
        val filtered = if (search.isBlank()) base else base.filter { it.name.contains(search, true) }
        filtered.sortedByDescending { it.updatedAt }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SmartPrint") }) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                placeholder = { Text("Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == GalleryTab.Recent,
                    onClick = { tab = GalleryTab.Recent },
                    text = { Text("Recent") }
                )
                Tab(
                    selected = tab == GalleryTab.Recycle,
                    onClick = { tab = GalleryTab.Recycle },
                    text = { Text("Recycle") }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (tab == GalleryTab.Recent) {
                Text(
                    text = "Start a new canvas",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onCreateNew) { Text("+ New") }

                    OutlinedButton(onClick = onOpenCameraMeasure) {
                        Text("Camera Measure")
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            if (shownDocs.isEmpty()) {
                val msg = if (tab == GalleryTab.Recent)
                    "No saved canvases yet.\nTap + New to create your first design."
                else
                    "Recycle bin is empty."

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    shownDocs.forEach { doc ->
                        Card {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(doc.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Updated: ${doc.updatedAt}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!doc.isDeleted) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onOpen(doc) }) { Text("Open") }
                                        OutlinedButton(onClick = { onDelete(doc) }) { Text("Delete") }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onRestore(doc) }) { Text("Restore") }
                                        OutlinedButton(onClick = { onDeleteForever(doc) }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}