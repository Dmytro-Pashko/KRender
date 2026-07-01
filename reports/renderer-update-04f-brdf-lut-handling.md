# Renderer Update 04F — Shared BRDF LUT Handling

Added shared BRDF LUT export and resolution without creating environment-specific copies.

The generator can export gdx-gltf's bundled `net/mgsx/gltf/shaders/brdfLUT.png` resource to the
manifest's shared `hdr/_common/brdf/brdfLUT.png` path. The runtime resolver now checks, in order:

1. The manifest-relative BRDF LUT path.
2. The conventional shared project LUT.
3. The BRDF LUT bundled in the gdx-gltf classpath.

Only exhaustion of all three locations produces a warning.
