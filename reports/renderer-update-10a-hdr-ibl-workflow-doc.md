# Renderer Update 10A — HDR / IBL Workflow Documentation

Created `docs/assets/gltf-workflow.md` because the requested workflow document was not present on
the branch.

The guide documents the HDR preset directory layout, manifest schema version 2, multiple source
variants, generated skybox/irradiance/radiance files, shared BRDF LUT behavior, steps for adding a
new environment, and the complete Gradle generator command. It also records that the current
downsample-and-blur implementation is an MVP that can later be replaced by cosine and GGX
convolution without a schema change.
