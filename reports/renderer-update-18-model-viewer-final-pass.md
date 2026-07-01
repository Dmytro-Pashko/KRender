# Renderer Update 18 — Model Viewer Final Pass

The final audit confirmed:

- Viewport Control contains exactly four ordered sections: Camera Control, Display Mode / Display
  Options, Renderer Selector, and Renderer Options.
- Three separators divide those four sections.
- glTF / PBR is the named default renderer mode.
- Grid, Axes, Bounds, Grid Size, and Grid Extent appear before the renderer selector.
- Only `GltfPbrRendererOptionsPanel` or `LegacyRendererOptionsPanel` is drawn at one time.
- Combined replaces None in the enum, default state, and both material-channel selectors.
- Both selectors expose the requested eight primary material channels.
- Every interactive control in Viewport Control, Model Viewer Control, and Texture Channels has a
  practical hover hint, including dynamic combo entries and UV/texture selectors.
- Gamma and sRGB controls clearly identify shader-resource recreation, and tone mapping clearly
  identifies its current non-live post-process status.

The stale Model Viewer system test fixture was adjusted to the new Legacy ambient-light model, but
tests were not run per the roadmap instruction. The Model Viewer agent documentation now reflects
manifest-driven HDR resolution and the default PBR renderer.
