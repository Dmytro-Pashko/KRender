# Renderer Update 04A — Environment Manifest v2

Implemented the backend-neutral HDR environment manifest model, JSON codec, loader, and validator.

The v2 manifest supports multiple EXR or HDR source variants, selects one active variant, and
describes the cubemap-cross skybox, generated irradiance and radiance paths, shared BRDF LUT, and
renderer defaults. Validation requires schema version 2 or newer, unique source variant ids, an
existing active source, and an existing cubemap-cross source while allowing generated IBL maps and
the shared LUT to be absent before generation.

`assets/hdr/default/environment.json` now declares the existing 2K and 4K EXR sources.
