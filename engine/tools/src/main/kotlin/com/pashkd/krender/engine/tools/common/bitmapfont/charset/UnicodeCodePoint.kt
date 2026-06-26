package com.pashkd.krender.engine.tools.common.bitmapfont.charset

@JvmInline
value class UnicodeCodePoint(val value: Int) {
    val isBmp: Boolean get() = value in 0..0xFFFF
    override fun toString(): String = "U+${value.toString(16).uppercase().padStart(4, '0')}"
}
