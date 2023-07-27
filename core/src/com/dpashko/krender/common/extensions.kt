package com.dpashko.krender.common

import com.badlogic.gdx.math.Vector3
import java.text.DecimalFormat

val df = DecimalFormat("#.###")

fun Vector3.format(): String {
    return "${df.format(this.x)}:${df.format(this.y)}:${df.format(this.z)}"
}