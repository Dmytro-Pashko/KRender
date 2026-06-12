package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.api.MaterialDebugMode
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelMaterialInfo
import com.pashkd.krender.engine.api.ModelTextureSlotInfo
import com.pashkd.krender.engine.api.TextureDebugComponent
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelViewerTextureChannelResolverTest {
    @Test
    fun `resolves roughness and metallic from metallic roughness slot with channel swizzles`() {
        val info =
            modelInfo(
                textureSlot(channel = "metallicRoughness", texturePath = "textures/mr.png", uvChannel = "TEXCOORD_1"),
            )

        val roughness =
            resolvedModelViewerDebugTextureRefs(
                info = info,
                mode = MaterialDebugMode.Roughness,
                selectedMaterialIndex = null,
                selectedTextureChannel = null,
            ).single()
        val metallic =
            resolvedModelViewerDebugTextureRefs(
                info = info,
                mode = MaterialDebugMode.Metallic,
                selectedMaterialIndex = null,
                selectedTextureChannel = null,
            ).single()
        val packed =
            resolvedModelViewerDebugTextureRefs(
                info = info,
                mode = MaterialDebugMode.MetallicRoughnessPacked,
                selectedMaterialIndex = null,
                selectedTextureChannel = null,
            ).single()

        assertEquals(TextureDebugComponent.G, roughness.component)
        assertEquals(TextureDebugComponent.B, metallic.component)
        assertEquals(TextureDebugComponent.RGB, packed.component)
        assertEquals("textures/mr.png", roughness.texture.id)
        assertEquals(1, roughness.texture.uvChannel)
    }

    @Test
    fun `resolves scalar occlusion and alpha components`() {
        val info =
            modelInfo(
                textureSlot(channel = "occlusion", texturePath = "textures/ao.png"),
                textureSlot(channel = "baseColor", texturePath = "textures/base.png"),
            )

        val occlusion =
            resolvedModelViewerDebugTextureRefs(
                info = info,
                mode = MaterialDebugMode.Occlusion,
                selectedMaterialIndex = null,
                selectedTextureChannel = null,
            ).single()
        val alpha =
            resolvedModelViewerDebugTextureRefs(
                info = info,
                mode = MaterialDebugMode.Alpha,
                selectedMaterialIndex = null,
                selectedTextureChannel = null,
            ).single()

        assertEquals(TextureDebugComponent.R, occlusion.component)
        assertEquals(TextureDebugComponent.A, alpha.component)
        assertEquals("textures/base.png", alpha.texture.id)
    }

    private fun textureSlot(
        channel: String,
        texturePath: String,
        uvChannel: String = "UV0",
    ): ModelTextureSlotInfo =
        ModelTextureSlotInfo(
            channel = channel,
            texturePath = texturePath,
            uvChannel = uvChannel,
            materialIndex = 0,
            materialId = "material",
        )

    private fun modelInfo(vararg slots: ModelTextureSlotInfo): ModelAssetInfo =
        ModelAssetInfo(
            path = "model.glb",
            format = "glTF",
            nodeCount = 1,
            meshCount = 1,
            meshPartCount = 1,
            materialCount = 1,
            vertexCount = 3,
            triangleCount = 1,
            size = null,
            vertexChannels = emptyList(),
            uvChannels = listOf("UV0", "UV1"),
            textureChannels = slots.map { slot -> slot.channel }.distinct(),
            textureCount = slots.map { slot -> slot.texturePath }.distinct().size,
            textureSlotCount = slots.size,
            hasSkeleton = false,
            boneCount = 0,
            boneWeightChannelCount = 0,
            animationCount = 0,
            animationNames = emptyList(),
            materials =
                listOf(
                    ModelMaterialInfo(
                        index = 0,
                        id = "material",
                        diffuseTexture = null,
                        normalTexture = null,
                        emissiveTexture = null,
                        textureSlots = slots.toList(),
                        baseColor = null,
                        opacity = null,
                    ),
                ),
        )
}
