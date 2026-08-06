package com.pashkd.krender.engine.tools.textureatlaseditor

typealias NinePatchDocument = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchDocument
typealias NinePatchParser = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchParser
typealias NinePatchPixelData = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchPixelData
typealias NinePatchPixelReader = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchPixelReader
typealias NinePatchSegment = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchSegment
typealias NinePatchValidationIssue = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchValidationIssue
typealias NinePatchValidationSeverity = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchValidationSeverity

fun isNinePatchTexturePath(path: String): Boolean =
    com.pashkd.krender.engine.tools.common.ninepatch.isNinePatchTexturePath(path)
