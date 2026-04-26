package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.DynamicMesh
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CPU-side mesh representation generated from [TerrainData].
 */
data class TerrainMeshData(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val tangents: FloatArray? = null,
    val colors: FloatArray? = null,
) {
    /**
     * Number of vertices in the mesh.
     */
    val vertexCount: Int
        get() = positions.size / 3

    /**
     * Number of indexed triangles in the mesh.
     */
    val triangleCount: Int
        get() = indices.size / 3

    /**
     * Converts terrain mesh data to the backend-neutral runtime mesh format.
     */
    fun toDynamicMesh(): DynamicMesh =
        DynamicMesh(
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            tangents = tangents,
            colors = colors,
        )
}

/**
 * Builds renderable terrain meshes from terrain data.
 */
object TerrainMeshBuilder {
    /**
     * Generates positions, UVs, triangle indices, normals, and tangents for [data].
     */
    fun build(data: TerrainData): TerrainMeshData {
        val vertexCount = data.width * data.height
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        val colors = buildLayerColors(data)
        val indices = IntArray((data.width - 1) * (data.height - 1) * 6)

        var vertexOffset = 0
        var uvOffset = 0
        for (y in 0 until data.height) {
            for (x in 0 until data.width) {
                positions[vertexOffset] = data.localXAt(x)
                positions[vertexOffset + 1] = data.getHeight(x, y)
                positions[vertexOffset + 2] = data.localZAt(y)
                vertexOffset += 3

                uvs[uvOffset] = x.toFloat() / (data.width - 1).coerceAtLeast(1)
                uvs[uvOffset + 1] = y.toFloat() / (data.height - 1).coerceAtLeast(1)
                uvOffset += 2
            }
        }

        var indexOffset = 0
        for (y in 0 until data.height - 1) {
            for (x in 0 until data.width - 1) {
                val topLeft = y * data.width + x
                val topRight = topLeft + 1
                val bottomLeft = topLeft + data.width
                val bottomRight = bottomLeft + 1

                indices[indexOffset++] = topLeft
                indices[indexOffset++] = bottomLeft
                indices[indexOffset++] = topRight
                indices[indexOffset++] = topRight
                indices[indexOffset++] = bottomLeft
                indices[indexOffset++] = bottomRight
            }
        }

        accumulateNormals(positions, indices, normals)
        val tangents = accumulateTangents(positions, uvs, indices)

        return TerrainMeshData(
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            tangents = tangents,
            colors = colors,
        )
    }

    private fun buildLayerColors(data: TerrainData): FloatArray {
        val colors = FloatArray(data.width * data.height * 4)
        val visibleLayers = data.allLayers().filter { it.visible }
        var colorOffset = 0
        for (y in 0 until data.height) {
            for (x in 0 until data.width) {
                var r = 0f
                var g = 0f
                var b = 0f
                var a = 0f
                var totalWeight = 0f
                visibleLayers.forEach { layer ->
                    val weight = data.getLayerWeight(layer.id, x, y)
                    if (weight <= 0f) return@forEach
                    r += layer.color.r * weight
                    g += layer.color.g * weight
                    b += layer.color.b * weight
                    a += layer.color.a * weight
                    totalWeight += weight
                }

                if (totalWeight > 0f) {
                    colors[colorOffset++] = (r / totalWeight).coerceIn(0f, 1f)
                    colors[colorOffset++] = (g / totalWeight).coerceIn(0f, 1f)
                    colors[colorOffset++] = (b / totalWeight).coerceIn(0f, 1f)
                    colors[colorOffset++] = (a / totalWeight).coerceIn(0f, 1f)
                } else {
                    colors[colorOffset++] = BASE_FALLBACK_COLOR.r
                    colors[colorOffset++] = BASE_FALLBACK_COLOR.g
                    colors[colorOffset++] = BASE_FALLBACK_COLOR.b
                    colors[colorOffset++] = BASE_FALLBACK_COLOR.a
                }
            }
        }
        return colors
    }

    private fun accumulateNormals(
        positions: FloatArray,
        indices: IntArray,
        normals: FloatArray,
    ) {
        for (triangle in indices.indices step 3) {
            val index0 = indices[triangle]
            val index1 = indices[triangle + 1]
            val index2 = indices[triangle + 2]

            val ax = positions[index1 * 3] - positions[index0 * 3]
            val ay = positions[index1 * 3 + 1] - positions[index0 * 3 + 1]
            val az = positions[index1 * 3 + 2] - positions[index0 * 3 + 2]

            val bx = positions[index2 * 3] - positions[index0 * 3]
            val by = positions[index2 * 3 + 1] - positions[index0 * 3 + 1]
            val bz = positions[index2 * 3 + 2] - positions[index0 * 3 + 2]

            val nx = ay * bz - az * by
            val ny = az * bx - ax * bz
            val nz = ax * by - ay * bx

            addToVector(normals, index0, nx, ny, nz)
            addToVector(normals, index1, nx, ny, nz)
            addToVector(normals, index2, nx, ny, nz)
        }

        for (vertex in 0 until normals.size / 3) {
            normalizeVector(normals, vertex)
        }
    }

    private fun accumulateTangents(
        positions: FloatArray,
        uvs: FloatArray,
        indices: IntArray,
    ): FloatArray {
        val tangents = FloatArray((positions.size / 3) * 3)

        for (triangle in indices.indices step 3) {
            val i0 = indices[triangle]
            val i1 = indices[triangle + 1]
            val i2 = indices[triangle + 2]

            val p0x = positions[i0 * 3]
            val p0y = positions[i0 * 3 + 1]
            val p0z = positions[i0 * 3 + 2]
            val p1x = positions[i1 * 3]
            val p1y = positions[i1 * 3 + 1]
            val p1z = positions[i1 * 3 + 2]
            val p2x = positions[i2 * 3]
            val p2y = positions[i2 * 3 + 1]
            val p2z = positions[i2 * 3 + 2]

            val uv0x = uvs[i0 * 2]
            val uv0y = uvs[i0 * 2 + 1]
            val uv1x = uvs[i1 * 2]
            val uv1y = uvs[i1 * 2 + 1]
            val uv2x = uvs[i2 * 2]
            val uv2y = uvs[i2 * 2 + 1]

            val edge1x = p1x - p0x
            val edge1y = p1y - p0y
            val edge1z = p1z - p0z
            val edge2x = p2x - p0x
            val edge2y = p2y - p0y
            val edge2z = p2z - p0z

            val deltaUv1x = uv1x - uv0x
            val deltaUv1y = uv1y - uv0y
            val deltaUv2x = uv2x - uv0x
            val deltaUv2y = uv2y - uv0y

            val determinant = deltaUv1x * deltaUv2y - deltaUv2x * deltaUv1y
            if (abs(determinant) <= 1e-6f) continue

            val inverse = 1f / determinant
            val tangentX = inverse * (deltaUv2y * edge1x - deltaUv1y * edge2x)
            val tangentY = inverse * (deltaUv2y * edge1y - deltaUv1y * edge2y)
            val tangentZ = inverse * (deltaUv2y * edge1z - deltaUv1y * edge2z)

            addToVector(tangents, i0, tangentX, tangentY, tangentZ)
            addToVector(tangents, i1, tangentX, tangentY, tangentZ)
            addToVector(tangents, i2, tangentX, tangentY, tangentZ)
        }

        for (vertex in 0 until tangents.size / 3) {
            normalizeVector(tangents, vertex)
        }

        return tangents
    }

    private fun addToVector(
        buffer: FloatArray,
        vertexIndex: Int,
        x: Float,
        y: Float,
        z: Float,
    ) {
        val base = vertexIndex * 3
        buffer[base] += x
        buffer[base + 1] += y
        buffer[base + 2] += z
    }

    private fun normalizeVector(buffer: FloatArray, vertexIndex: Int) {
        val base = vertexIndex * 3
        val x = buffer[base]
        val y = buffer[base + 1]
        val z = buffer[base + 2]
        val length = sqrt(x * x + y * y + z * z)
        if (length <= 1e-6f) {
            buffer[base] = 0f
            buffer[base + 1] = 1f
            buffer[base + 2] = 0f
            return
        }

        buffer[base] = x / length
        buffer[base + 1] = y / length
        buffer[base + 2] = z / length
    }

    private val BASE_FALLBACK_COLOR = TerrainLayerColorDescriptor(0.38f, 0.48f, 0.30f, 1f)
}
