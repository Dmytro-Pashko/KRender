# Renderer Update 14 — Default glTF / PBR Renderer

Model Viewer state now uses the named `DEFAULT_MODEL_VIEWER_RENDERER_MODE`, set to
`ModelViewerRendererMode.Pbr`.

Model Viewer does not currently serialize renderer preferences, so every newly created state—and
therefore every unset renderer selection—starts in glTF / PBR mode. The renderer selector reflects
that state and uses the labels `glTF / PBR` and `LibGDX / Legacy`.
