package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.Component

/**
 * Marks runtime/editor support entities that must not be treated as scene content.
 */
class EditorOnlyComponent : Component

/**
 * Identifies the runtime camera used to inspect Scene Editor content.
 */
class SceneEditorCameraComponent : Component
