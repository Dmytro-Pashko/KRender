package com.pashkd.krender.engine.terrain

interface TerrainGenerator {
    val id: String
    fun generate(data: TerrainData)
}

class FlatTerrainGenerator(
    private val baseHeight: Float = 0f,
) : TerrainGenerator {
    override val id: String = "flat"

    override fun generate(data: TerrainData) {
        for (y in 0 until data.height) {
            for (x in 0 until data.width) {
                data.setHeight(x, y, baseHeight)
            }
        }
    }
}

class PerlinNoiseGenerator : TerrainGenerator {
    override val id: String = "perlin"

    override fun generate(data: TerrainData) {
        // TODO: Integrate real noise once the engine gets a shared noise module.
    }
}

class SimplexNoiseGenerator : TerrainGenerator {
    override val id: String = "simplex"

    override fun generate(data: TerrainData) {
        // TODO: Integrate simplex noise when generator dependencies land in the engine.
    }
}

class FractalNoiseGenerator : TerrainGenerator {
    override val id: String = "fractal"

    override fun generate(data: TerrainData) {
        // TODO: Layer octave-based generation on top of the future noise generators.
    }
}
