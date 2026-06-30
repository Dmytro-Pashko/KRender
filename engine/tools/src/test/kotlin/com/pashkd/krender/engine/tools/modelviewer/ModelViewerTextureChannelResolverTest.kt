package com.pashkd.krender.engine.tools.modelviewer

import com.pashkd.krender.engine.api.MaterialDebugMode
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelMaterialInfo
import com.pashkd.krender.engine.api.ModelTextureSlotInfo
import com.pashkd.krender.engine.api.TextureDebugComponent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `resolves all debug channels from mixed gltf-style slots`() {
        val info =
            modelInfo(
                textureSlot(channel = "baseColor", texturePath = "textures/base.png"),
                textureSlot(channel = "normal", texturePath = "textures/normal.png", uvChannel = "UV1"),
                textureSlot(channel = "emissive", texturePath = "textures/emissive.png"),
                textureSlot(channel = "occlusion", texturePath = "textures/orm.png"),
                textureSlot(channel = "metallicRoughness", texturePath = "textures/orm.png"),
            )

        assertDebugTexture(info, MaterialDebugMode.BaseColor, "textures/base.png", TextureDebugComponent.RGB, 0)
        assertDebugTexture(info, MaterialDebugMode.Normal, "textures/normal.png", TextureDebugComponent.RGB, 1)
        assertDebugTexture(info, MaterialDebugMode.Emission, "textures/emissive.png", TextureDebugComponent.RGB, 0)
        assertDebugTexture(info, MaterialDebugMode.Occlusion, "textures/orm.png", TextureDebugComponent.R, 0)
        assertDebugTexture(info, MaterialDebugMode.Roughness, "textures/orm.png", TextureDebugComponent.G, 0)
        assertDebugTexture(info, MaterialDebugMode.Metallic, "textures/orm.png", TextureDebugComponent.B, 0)
        assertDebugTexture(info, MaterialDebugMode.MetallicRoughnessPacked, "textures/orm.png", TextureDebugComponent.RGB, 0)
        assertDebugTexture(info, MaterialDebugMode.Alpha, "textures/base.png", TextureDebugComponent.A, 0)
    }

    @Test
    fun `matches aliases and respects selected material filter`() {
        val info =
            modelInfo(
                material(
                    index = 0,
                    id = "painted",
                    textureSlot(channel = "baseColorTexture", texturePath = "textures/base-a.png"),
                    textureSlot(channel = "metallicRoughnessTexture", texturePath = "textures/orm-a.png"),
                ),
                material(
                    index = 1,
                    id = "metal",
                    textureSlot(channel = "diffuse", texturePath = "textures/base-b.png"),
                    textureSlot(channel = "ORM", texturePath = "textures/orm-b.png"),
                ),
            )

        val filtered =
            resolvedModelViewerDebugTextureRefs(
                info = info,
                mode = MaterialDebugMode.Roughness,
                selectedMaterialIndex = 1,
                selectedTextureChannel = null,
            ).single()

        assertEquals("metal", filtered.materialId)
        assertEquals("textures/orm-b.png", filtered.texture.id)
        assertEquals(TextureDebugComponent.G, filtered.component)

        assertTrue(hasModelViewerTextureChannel(info, MaterialDebugMode.BaseColor, selectedMaterialIndex = 0))
        assertTrue(hasModelViewerTextureChannel(info, MaterialDebugMode.BaseColor, selectedMaterialIndex = 1))
        assertTrue(hasModelViewerTextureChannel(info, MaterialDebugMode.Metallic, selectedMaterialIndex = 1))
        assertFalse(hasModelViewerTextureChannel(info, MaterialDebugMode.Emission, selectedMaterialIndex = null))
        assertEquals(
            "baseColorTexture",
            preferredModelViewerTextureChannel(info, MaterialDebugMode.BaseColor, selectedMaterialIndex = 0),
        )
    }

    @Test
    fun `selected texture channel overrides default mode aliases`() {
        val info =
            modelInfo(
                textureSlot(channel = "baseColor", texturePath = "textures/base.png"),
                textureSlot(channel = "alpha", texturePath = "textures/alpha.png"),
            )

        val selected =
            resolvedModelViewerDebugTextureRefs(
                info = info,
                mode = MaterialDebugMode.Alpha,
                selectedMaterialIndex = null,
                selectedTextureChannel = "alpha",
            ).single()

        assertEquals("textures/alpha.png", selected.texture.id)
        assertEquals(TextureDebugComponent.A, selected.component)
    }

    private fun textureSlot(
        channel: String,
        texturePath: String,
        uvChannel: String = "UV0",
        materialIndex: Int = 0,
        materialId: String = "material",
    ): ModelTextureSlotInfo =
        ModelTextureSlotInfo(
            channel = channel,
            texturePath = texturePath,
            uvChannel = uvChannel,
            materialIndex = materialIndex,
            materialId = materialId,
        )

    private fun material(
        index: Int,
        id: String,
        vararg slots: ModelTextureSlotInfo,
    ): ModelMaterialInfo =
        ModelMaterialInfo(
            index = index,
            id = id,
            diffuseTexture = null,
            normalTexture = null,
            emissiveTexture = null,
            textureSlots =
                slots.map { slot ->
                    slot.copy(materialIndex = index, materialId = id)
                },
            baseColor = null,
            opacity = null,
        )

    private fun modelInfo(vararg slots: ModelTextureSlotInfo): ModelAssetInfo =
        modelInfo(
            material(
                index = 0,
                id = "material",
                *slots,
            ),
        )

    private fun modelInfo(vararg materials: ModelMaterialInfo): ModelAssetInfo =
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
            textureChannels = materials.flatMap { material -> material.textureSlots }.map { slot -> slot.channel }.distinct(),
            textureCount =
                materials
                    .flatMap { material -> material.textureSlots }
                    .mapNotNull { slot -> slot.texturePath }
                    .distinct()
                    .size,
            textureSlotCount = materials.sumOf { material -> material.textureSlots.size },
            hasSkeleton = false,
            boneCount = 0,
            boneWeightChannelCount = 0,
            animationCount = 0,
            animationNames = emptyList(),
            materials = materials.toList(),
        )

    private fun assertDebugTexture(
        info: ModelAssetInfo,
        mode: MaterialDebugMode,
        expectedPath: String,
        expectedComponent: TextureDebugComponent,
        expectedUvChannel: Int,
    ) {
        val resolved =
            resolvedModelViewerDebugTextureRefs(
                info = info,
                mode = mode,
                selectedMaterialIndex = null,
                selectedTextureChannel = null,
            ).single()

        assertEquals(expectedPath, resolved.texture.id)
        assertEquals(expectedComponent, resolved.component)
        assertEquals(expectedUvChannel, resolved.texture.uvChannel)
    }
}
