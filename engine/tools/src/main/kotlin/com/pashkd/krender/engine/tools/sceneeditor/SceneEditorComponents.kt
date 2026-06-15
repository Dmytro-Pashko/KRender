package com.pashkd.krender.engine.tools.sceneeditor

import com.pashkd.krender.engine.api.Component
import com.pashkd.krender.engine.editor.viewport.EditorViewportCameraComponent

/**
 * Marks runtime/editor support entities that must not be treated as scene content.
 */
class EditorOnlyComponent : Component

/**
 * Backward-compatible alias for the shared editor viewport camera marker.
 */
typealias SceneEditorCameraComponent = EditorViewportCameraComponent
