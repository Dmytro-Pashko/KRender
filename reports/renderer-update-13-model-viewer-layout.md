# Renderer Update 13 — Model Viewer Layout

Reorganized Viewport Control into four sections in the required order:

1. Camera Control
2. Display Mode / Display Options
3. Renderer Selector
4. Renderer Options

Three horizontal separators make the boundaries explicit. Reset Camera and Frame Model now live
with camera interaction, while Display Mode, Grid, Axes, Bounds, Grid Size, and Grid Extent are
grouped above the isolated renderer selector. Ambient and environment controls remain visible only
inside the applicable renderer options.
