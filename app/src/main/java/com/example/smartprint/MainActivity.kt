package com.example.smartprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

private enum class Screen { Gallery, Editor, CameraMeasure }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                SmartPrintRoot()
            }
        }
    }

    @Composable
    private fun SmartPrintRoot() {
        val storage = remember { CanvasStorage(this) }

        var screen by rememberSaveable { mutableStateOf(Screen.Gallery) }
        var docs by remember { mutableStateOf(storage.loadDocs()) }

        var activeDocId by rememberSaveable { mutableStateOf<String?>(null) }
        var vm by remember { mutableStateOf<SmartPrintViewModel?>(null) }

        fun refreshDocs() {
            docs = storage.loadDocs()
        }

        fun saveActiveDoc(currentVm: SmartPrintViewModel?, docId: String?) {
            if (currentVm == null || docId == null) return

            val data = currentVm.exportData()
            storage.saveCanvasData(docId, data)

            val updatedDocs = storage.loadDocs().map {
                if (it.id == docId) {
                    it.copy(updatedAt = System.currentTimeMillis())
                } else it
            }

            storage.saveDocs(updatedDocs)
            docs = updatedDocs
        }

        when (screen) {
            Screen.Gallery -> GalleryScreen(
                docs = docs,

                onCreateNew = {
                    val newDoc = storage.createNewDocAutoNamed(docs)
                    val newDocs = docs + newDoc
                    storage.saveDocs(newDocs)

                    val emptyData = CanvasData(
                        pixelsPerFoot = 40f,
                        lines = emptyList()
                    )
                    storage.saveCanvasData(newDoc.id, emptyData)

                    docs = newDocs
                    activeDocId = newDoc.id

                    vm = SmartPrintViewModel().apply {
                        importData(emptyData)
                    }

                    screen = Screen.Editor
                },

                onOpen = { doc ->
                    activeDocId = doc.id

                    val data = storage.loadCanvasData(doc.id)
                        ?: CanvasData(40f, emptyList())

                    vm = SmartPrintViewModel().apply {
                        importData(data)
                    }

                    screen = Screen.Editor
                },

                onDelete = { doc ->
                    val updated = docs.map {
                        if (it.id == doc.id) {
                            it.copy(
                                isDeleted = true,
                                updatedAt = System.currentTimeMillis()
                            )
                        } else it
                    }

                    storage.saveDocs(updated)
                    refreshDocs()
                },

                onRestore = { doc ->
                    val updated = docs.map {
                        if (it.id == doc.id) {
                            it.copy(
                                isDeleted = false,
                                updatedAt = System.currentTimeMillis()
                            )
                        } else it
                    }

                    storage.saveDocs(updated)
                    refreshDocs()
                },

                onDeleteForever = { doc ->
                    val updated = docs.filterNot { it.id == doc.id }
                    storage.saveDocs(updated)
                    storage.permanentDelete(doc.id)
                    refreshDocs()
                },

                onOpenCameraMeasure = {
                    screen = Screen.CameraMeasure
                }
            )

            Screen.Editor -> {
                val currentVm = vm
                val id = activeDocId

                if (currentVm == null || id == null) {
                    screen = Screen.Gallery
                } else {
                    val activeDoc = docs.first { it.id == id }

                    LaunchedEffect(
                        id,
                        currentVm.pixelsPerFoot,
                        currentVm.walls,
                        currentVm.openings
                    ) {
                        saveActiveDoc(currentVm, id)
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            saveActiveDoc(currentVm, id)
                        }
                    }

                    SmartPrintScreen(
                        vm = currentVm,
                        activeDoc = activeDoc,
                        onBack = {
                            saveActiveDoc(currentVm, id)
                            refreshDocs()
                            screen = Screen.Gallery
                        }
                    )
                }
            }

            Screen.CameraMeasure -> {
                CameraMeasureScreen(
                    onBack = {
                        screen = Screen.Gallery
                    }
                )
            }
        }
    }
}