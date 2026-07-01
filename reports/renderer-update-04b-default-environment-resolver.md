# Renderer Update 04B — Default Environment Resolver

Added `GdxHdrEnvironmentResolver` in the LibGDX backend.

The resolver accepts a preset name or manifest path, maps the default preset to
`hdr/default/environment.json`, parses the v2 manifest, validates the active source and skybox,
and resolves source, skybox face, irradiance face, radiance mip/face, and BRDF LUT paths relative
to the manifest directory.

Missing generated IBL maps and a missing shared BRDF LUT produce warnings and remain non-fatal so
the renderer can use its procedural fallback.
