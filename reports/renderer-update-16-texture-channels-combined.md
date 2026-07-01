# Renderer Update 16 — Combined Texture Channel

Renamed the backend-neutral `MaterialDebugMode.None` value to `Combined`.

Both the renderer options and Texture Channels selectors now show exactly the primary material
channel list: Combined, Base Color, Normal, Metallic, Roughness, Occlusion, Emissive, and Alpha.
The packed metallic/roughness diagnostic remains supported internally, and UV Checker remains a
separate dedicated control rather than appearing as a material channel.

Combined is the default state and continues to mean that no debug override is active, so the final
material result is rendered with all supported channels together.
