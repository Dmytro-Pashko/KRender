@file:Suppress("MatchingDeclarationName")

package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.pashkd.krender.engine.api.DrawModel

/** Composite cache key that distinguishes model instances by entity and asset id. */
internal data class ModelCacheKey(
    val entityId: Long,
    val modelPath: String,
)

internal fun applyTransform(
    instance: ModelInstance,
    command: DrawModel,
) {
    val transform = command.transform
    instance.transform.idt()
    instance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
    instance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
    instance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
    instance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
    instance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)
}
