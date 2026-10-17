package com.example.smartprint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class EditorHistory {
    private var undoStack: List<EditorAction> = emptyList()
    private var redoStack: List<EditorAction> = emptyList()

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    fun record(action: EditorAction) {
        undoStack = undoStack + action
        redoStack = emptyList()
        refreshFlags()
    }

    fun popUndo(): EditorAction? {
        val action = undoStack.lastOrNull() ?: return null
        undoStack = undoStack.dropLast(1)
        refreshFlags()
        return action
    }

    fun popRedo(): EditorAction? {
        val action = redoStack.lastOrNull() ?: return null
        redoStack = redoStack.dropLast(1)
        refreshFlags()
        return action
    }

    fun pushRedo(action: EditorAction) {
        redoStack = redoStack + action
        refreshFlags()
    }

    fun pushUndo(action: EditorAction) {
        undoStack = undoStack + action
        refreshFlags()
    }

    fun clear() {
        undoStack = emptyList()
        redoStack = emptyList()
        refreshFlags()
    }

    private fun refreshFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
}