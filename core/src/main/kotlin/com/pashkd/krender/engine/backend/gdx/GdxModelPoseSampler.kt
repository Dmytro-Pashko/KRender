@file:Suppress("ReturnCount")

package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.ModelBonePose
import com.pashkd.krender.engine.api.Vec3
import net.mgsx.gltf.scene3d.utils.MaterialConverter
import net.mgsx.gltf.scene3d.scene.Scene as GltfScene

/**
 * Asset-scoped pose sampler used by metadata/skeleton preview paths.
 */
internal class GdxModelPoseSampler(
    private val assets: GdxAssetService,
) {
    private val poseSampledInstances = mutableMapOf<String, ModelInstance>()
    private val poseSampledAnimationControllers = mutableMapOf<String, AnimationController>()
    private val poseSampledGltfScenes = mutableMapOf<String, GltfScene>()

    fun sample(
        asset: AssetRef<ModelAsset>,
        animationName: String?,
        timeSeconds: Float,
        loop: Boolean,
    ): List<ModelBonePose> =
        when {
            asset.isGltf() -> sampleGltfSkeletonPose(asset, animationName, timeSeconds, loop)
            else -> sampleStaticModelSkeletonPose(asset, animationName, timeSeconds, loop)
        }

    fun clear(path: String) {
        poseSampledInstances -= path
        poseSampledAnimationControllers -= path
        poseSampledGltfScenes -= path
    }

    fun clear() {
        poseSampledInstances.clear()
        poseSampledAnimationControllers.clear()
        poseSampledGltfScenes.clear()
    }

    private fun sampleStaticModelSkeletonPose(
        asset: AssetRef<ModelAsset>,
        animationName: String?,
        timeSeconds: Float,
        loop: Boolean,
    ): List<ModelBonePose> {
        val model = assets.gdxModel(asset) ?: return emptyList()
        if (!supportsSkeletonSampling(model)) return emptyList()
        val instance = poseSampledInstances.getOrPut(asset.path) { ModelInstance(model) }
        val controller = poseSampledAnimationControllers.getOrPut(asset.path) { AnimationController(instance) }
        sampleAnimationPose(instance, controller, animationName, timeSeconds, loop)
        return extractBonePoses(instance.nodes)
    }

    private fun sampleGltfSkeletonPose(
        asset: AssetRef<ModelAsset>,
        animationName: String?,
        timeSeconds: Float,
        loop: Boolean,
    ): List<ModelBonePose> {
        val sceneAsset = assets.gltfScene(asset) ?: return emptyList()
        val model = sceneAsset.scene.model
        if (!supportsSkeletonSampling(model)) return emptyList()
        val scene =
            poseSampledGltfScenes.getOrPut(asset.path) {
                GltfScene(sceneAsset.scene).also(MaterialConverter::makeCompatible)
            }
        sampleAnimationPose(scene.modelInstance, scene.animationController, animationName, timeSeconds, loop)
        return extractBonePoses(scene.modelInstance.nodes)
    }

    private fun sampleAnimationPose(
        instance: ModelInstance,
        controller: AnimationController?,
        animationName: String?,
        timeSeconds: Float,
        loop: Boolean,
    ) {
        if (controller == null || instance.animations.isEmpty) {
            instance.calculateTransforms()
            return
        }
        controller.paused = false
        if (animationName.isNullOrBlank()) {
            controller.setAnimation(null as String?)
            controller.update(0f)
            return
        }
        val animation =
            instance.getAnimation(animationName) ?: run {
                controller.setAnimation(null as String?)
                controller.update(0f)
                return
            }
        controller.setAnimation(animationName, if (loop) -1 else 1, 1f, null)
        controller.current?.time = normalizedAnimationTime(animation, timeSeconds, loop)
        controller.update(0f)
    }

    private fun extractBonePoses(nodes: Array<Node>): List<ModelBonePose> {
        val collected = collectNodes(nodes)
        val indexByNode = mutableMapOf<Node, Int>()
        collected.forEachIndexed { index, node -> indexByNode[node] = index }
        val translation = Vector3()
        return collected.mapIndexed { index, node ->
            node.globalTransform.getTranslation(translation)
            ModelBonePose(
                boneIndex = index,
                name = node.id?.takeIf(String::isNotBlank),
                parentIndex = node.parent?.let(indexByNode::get),
                worldPosition = Vec3(translation.x, translation.y, translation.z),
            )
        }
    }
}

internal fun supportsSkeletonSampling(model: Model): Boolean {
    val nodes = collectNodes(model.nodes)
    val maxBonesPerPart = nodes.flatMap(::nodePartsOf).maxOfOrNull { part -> part.bones?.size ?: 0 } ?: 0
    return maxBonesPerPart > 0 || model.animations.size > 0
}
