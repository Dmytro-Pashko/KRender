package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.assets.AssetDescriptor
import java.io.File

enum class AssetHierarchyNodeKind {
    Directory,
    Asset,
}

data class AssetHierarchyNode(
    val kind: AssetHierarchyNodeKind,
    val name: String,
    val path: String,
    val asset: AssetDescriptor?,
    val children: List<AssetHierarchyNode>,
    val assetCount: Int,
    val sizeBytes: Long,
)

object AssetHierarchyBuilder {
    fun build(
        baseDir: File,
        assets: List<AssetDescriptor>,
    ): AssetHierarchyNode {
        val root = MutableDirectory(name = "Assets", path = "")
        if (baseDir.exists() && baseDir.isDirectory) {
            baseDir
                .walkTopDown()
                .onEnter { directory -> shouldEnterDirectory(baseDir, directory) }
                .filter { file -> file.isDirectory && file != baseDir }
                .forEach { directory ->
                    root.ensureDirectory(relativePath(baseDir, directory))
                }
        }

        assets
            .distinctBy(AssetDescriptor::path)
            .forEach { asset ->
                root.addAsset(assetBrowserNormalizePath(asset.path), asset)
            }

        return root.toNode()
    }

    private fun shouldEnterDirectory(
        baseDir: File,
        directory: File,
    ): Boolean {
        if (directory == baseDir) return true
        return !directory.name.startsWith(".") && !directory.isHidden
    }

    private fun relativePath(
        baseDir: File,
        file: File,
    ): String {
        val basePath = baseDir.toPath().toAbsolutePath().normalize()
        val filePath = file.toPath().toAbsolutePath().normalize()
        return assetBrowserNormalizePath(basePath.relativize(filePath).toString())
    }

    private class MutableDirectory(
        val name: String,
        val path: String,
    ) {
        private val directories = linkedMapOf<String, MutableDirectory>()
        private val assets = linkedMapOf<String, AssetDescriptor>()

        fun ensureDirectory(path: String): MutableDirectory {
            if (path.isBlank()) return this
            var current = this
            val segments = path.split('/').filter(String::isNotBlank)
            val currentPath = mutableListOf<String>()
            segments.forEach { segment ->
                currentPath += segment
                current =
                    current.directories.getOrPut(segment.lowercase()) {
                        MutableDirectory(segment, currentPath.joinToString("/"))
                    }
            }
            return current
        }

        fun addAsset(
            path: String,
            asset: AssetDescriptor,
        ) {
            val directoryPath = path.substringBeforeLast('/', "")
            val fileDirectory = ensureDirectory(directoryPath)
            fileDirectory.assets[path.lowercase()] = asset
        }

        fun toNode(): AssetHierarchyNode {
            val directoryNodes =
                directories.values
                    .map(MutableDirectory::toNode)
                    .sortedBy { it.name.lowercase() }
            val assetNodes =
                assets.values
                    .sortedBy { it.name.lowercase() }
                    .map { asset ->
                        AssetHierarchyNode(
                            kind = AssetHierarchyNodeKind.Asset,
                            name = asset.name,
                            path = assetBrowserNormalizePath(asset.path),
                            asset = asset,
                            children = emptyList(),
                            assetCount = 1,
                            sizeBytes = asset.sizeBytes,
                        )
                    }
            val children = directoryNodes + assetNodes
            return AssetHierarchyNode(
                kind = AssetHierarchyNodeKind.Directory,
                name = name,
                path = path,
                asset = null,
                children = children,
                assetCount = children.sumOf(AssetHierarchyNode::assetCount),
                sizeBytes = children.sumOf(AssetHierarchyNode::sizeBytes),
            )
        }
    }
}
