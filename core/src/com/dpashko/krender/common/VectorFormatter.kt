package com.dpashko.krender.common

import com.badlogic.gdx.math.Vector3
import java.text.DecimalFormat

object VectorFormatter {

    private val FLOAT_FORMATTER = DecimalFormat("#.###")

    fun formatVector3(vector: Vector3): String {
        return "${FLOAT_FORMATTER.format(vector.x)}:${FLOAT_FORMATTER.format(vector.y)}:${FLOAT_FORMATTER.format(vector.z)}"
    }
}
