# Renderer Update 04G — Generated IBL Integration

Added `GdxGltfEnvironment`, a preset-driven GPU resource cache used by the glTF PBR preview path.

The default Model Viewer request now carries `environmentPreset = "default"` instead of a
hardcoded skybox texture. The backend resolves `hdr/default/environment.json`, uploads the
generated skybox and irradiance faces, uploads every explicit radiance mip level, and binds the
resolved shared BRDF LUT through gdx-gltf's PBR texture attribute.

Missing or invalid preset components are warned about and replaced by the existing procedural
gdx-gltf `IBLBuilder` output on a per-component basis. A failed preset therefore degrades to direct
and procedural lighting instead of failing the model render.
