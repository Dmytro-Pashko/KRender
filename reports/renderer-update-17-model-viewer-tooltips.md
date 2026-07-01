# Renderer Update 17 — Model Viewer Tooltips

Added practical hover hints to every interactive control in Viewport Control, Model Viewer
Control, and Texture Channels.

Coverage includes toolbar and camera buttons, display toggles, sliders, text input, renderer and
display selectors, renderer-specific lighting and color controls, tone/gamma/sRGB settings,
material-channel selectors, channel availability entries, UV checker options, and dynamically
generated texture/UV selectors.

Tooltips identify units and live behavior where relevant, state renderer availability, call out
gamma/sRGB shader-resource recreation, and explicitly identify tone mapping as a stored preference
that is not yet applied by a post-process pass.
