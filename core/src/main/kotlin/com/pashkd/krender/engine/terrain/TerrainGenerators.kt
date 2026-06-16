package com.pashkd.krender.engine.terrain

/**
 * Terrain heightfield generator contract.
 *
 * Generators mutate [TerrainData] directly so generated results can be edited,
 * serialized, and converted to mesh data by the same downstream pipeline.
 */
interface TerrainGenerator {
    /**
     * Stable generator identifier for tooling and future presets.
     */
    val id: String

    /**
     * Applies generated values to [data].
     */
    fun generate(data: TerrainData)
}

/**
 * Fills the whole heightmap with one constant height.
 */
class FlatTerrainGenerator(
    private val baseHeight: Float = 0f,
) : TerrainGenerator {
    override val id: String = "flat"

    /**
     * Writes [baseHeight] into every height sample.
     */
    override fun generate(data: TerrainData) {
        for (y in 0 until data.height) {
            for (x in 0 until data.width) {
                data.setHeight(x, y, baseHeight)
            }
        }
    }
}

/**
 * Placeholder for future Perlin-based terrain generation.
 */
class PerlinNoiseGenerator : TerrainGenerator {
    override val id: String = "perlin"

    /**
     * Reserved extension point for Perlin noise generation.
     */
    override fun generate(data: TerrainData) {
        // TODO: Integrate real noise once the engine gets a shared noise module.
    }
}

/**
 * Placeholder for future Simplex-based terrain generation.
 */
class SimplexNoiseGenerator : TerrainGenerator {
    override val id: String = "simplex"

    /**
     * Reserved extension point for Simplex noise generation.
     */
    override fun generate(data: TerrainData) {
        // TODO: Integrate simplex noise when generator dependencies land in the engine.
    }
}

/**
 * Placeholder for future octave/fractal terrain generation.
 */
class FractalNoiseGenerator : TerrainGenerator {
    override val id: String = "fractal"

    /**
     * Reserved extension point for layered fractal noise generation.
     */
    override fun generate(data: TerrainData) {
        // TODO: Layer octave-based generation on top of the future noise generators.
    }
}
