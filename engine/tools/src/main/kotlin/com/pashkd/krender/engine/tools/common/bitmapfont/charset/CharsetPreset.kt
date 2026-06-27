package com.pashkd.krender.engine.tools.common.bitmapfont.charset

enum class CharsetPreset(
    val displayName: String,
) {
    ENGLISH("English"),
    SYMBOLS("Symbols"),
    UKRAINIAN_CYRILLIC("Ukrainian Cyrillic"),
    ENGLISH_SYMBOLS_UKRAINIAN_CYRILLIC("English + Symbols + Ukrainian Cyrillic"),
    CUSTOM("Custom"),
}

object CharsetBuilder {
    private val ENGLISH_UPPER = ('A'..'Z').map { UnicodeCodePoint(it.code) }
    private val ENGLISH_LOWER = ('a'..'z').map { UnicodeCodePoint(it.code) }
    private val DIGITS = ('0'..'9').map { UnicodeCodePoint(it.code) }
    private val BASIC_SYMBOLS = " !\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~".map { UnicodeCodePoint(it.code) }

    private val UKRAINIAN_CYRILLIC_UPPER = "АБВГҐДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯ".map { UnicodeCodePoint(it.code) }
    private val UKRAINIAN_CYRILLIC_LOWER = "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя".map { UnicodeCodePoint(it.code) }

    fun build(preset: CharsetPreset): List<UnicodeCodePoint> =
        when (preset) {
            CharsetPreset.ENGLISH -> english()
            CharsetPreset.SYMBOLS -> symbols()
            CharsetPreset.UKRAINIAN_CYRILLIC -> ukrainianCyrillic()
            CharsetPreset.ENGLISH_SYMBOLS_UKRAINIAN_CYRILLIC -> english() + symbols() + ukrainianCyrillic()
            CharsetPreset.CUSTOM -> emptyList()
        }.distinctBy { it.value }.sortedBy { it.value }

    fun buildFromCustom(characters: String): List<UnicodeCodePoint> = characters.map { UnicodeCodePoint(it.code) }.distinctBy { it.value }.sortedBy { it.value }

    fun buildCombined(
        preset: CharsetPreset,
        customCharacters: String,
    ): List<UnicodeCodePoint> {
        val presetChars = build(preset)
        val customChars = buildFromCustom(customCharacters)
        return (presetChars + customChars).distinctBy { it.value }.sortedBy { it.value }
    }

    private fun english(): List<UnicodeCodePoint> = ENGLISH_UPPER + ENGLISH_LOWER + DIGITS

    private fun symbols(): List<UnicodeCodePoint> = BASIC_SYMBOLS

    private fun ukrainianCyrillic(): List<UnicodeCodePoint> = UKRAINIAN_CYRILLIC_UPPER + UKRAINIAN_CYRILLIC_LOWER
}
