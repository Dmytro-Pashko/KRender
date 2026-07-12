package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.IrradianceGenerationConfig
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class IrradianceConvolutionGeneratorTest {
    @Test
    fun defaultLdrMappingKeepsBrightHdrIrradianceBelowPureWhite() {
        val outputDirectory = createTempDir(prefix = "krender-irradiance-test")
        try {
            val source =
                HdrImage(
                    width = 2,
                    height = 1,
                    pixels =
                        floatArrayOf(
                            4f,
                            4f,
                            4f,
                            4f,
                            4f,
                            4f,
                        ),
                )

            IrradianceConvolutionGenerator().generate(
                source = source,
                outputDirectory = outputDirectory,
                outputFormat = "PNG",
                overwritePolicy = EnvironmentIblOverwritePolicy.Replace,
                config = IrradianceGenerationConfig(resolution = 4, sampleCount = 16),
            )

            val image = ImageIO.read(File(outputDirectory, "posx.png"))
            var maxChannel = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val rgb = image.getRGB(x, y)
                    maxChannel = maxOf(maxChannel, (rgb ushr 16) and 0xff, (rgb ushr 8) and 0xff, rgb and 0xff)
                }
            }
            assertTrue(maxChannel in 1..254)
        } finally {
            outputDirectory.deleteRecursively()
        }
    }
}
