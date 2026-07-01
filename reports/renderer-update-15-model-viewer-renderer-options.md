# Renderer Update 15 — Renderer-Specific Option Panels

Added `GltfPbrRendererOptionsPanel` and `LegacyRendererOptionsPanel`. Viewport Control creates both
but draws only the panel matching the selected renderer.

The glTF / PBR panel exposes the environment preset, skybox, environment intensity, exposure,
environment rotation, tone-mapping preference, gamma correction, sRGB texture handling,
directional light enable/intensity/color/yaw/pitch, and the supported material-debug channels.
Environment rotation, gamma, sRGB, and directional lighting are wired to the backend. Gamma and
sRGB changes recreate the PBR shader manager as required. Tone mapping is explicitly marked as a
retained preference for a future post-processing pass because the current renderer has no
tone-mapping stage.

The Legacy panel exposes the MVP ambient and directional lighting controls. A dedicated
directional-light entity is synchronized only while the Legacy renderer is active.
