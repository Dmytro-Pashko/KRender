# glTF Asset Workflow

KRender loads `.gltf` and `.glb` models through the LibGDX backend and renders PBR previews through
gdx-gltf. Keep model texture references relative and include every external buffer and texture when
moving a `.gltf` asset into the project.

## HDR / IBL Environment Layout

KRender PBR environments live in:

```text
hdr/<environment_name>/
```

The default environment is:

```text
hdr/default/
```

Current default environment files:

```text
hdr/default/environment.json
hdr/default/skybox/default_skybox_studio.png
hdr/default/source/aerial-green-landscape-clouds_2K.exr
hdr/default/source/aerial-green-landscape-clouds_4K.exr
```

Generated files:

```text
hdr/default/skybox/generated/environment_{face}.png
hdr/default/irradiance/irradiance_{face}.png
hdr/default/radiance/radiance_{mip}_{face}.png
hdr/_common/brdf/brdfLUT.png
```

KRender environment manifests support multiple source variants:

```text
EXR 2K
EXR 4K
HDR 2K
HDR 4K
```

Only one source variant is active at a time.

IBL generated maps:

- diffuse IBL = irradiance cubemap
- specular IBL = prefiltered radiance cubemap with mip levels
- BRDF LUT = shared lookup texture, not environment-specific

### Manifest schema version 2

Every environment uses `environment.json` with schema `krender.hdr-environment` and version 2 or
newer. `source.variants` lists all available source files, while `source.activeVariant` selects one
variant by id. Source paths, skybox paths, generated face patterns, and the BRDF LUT path resolve
relative to the manifest directory.

The active source and cubemap-cross skybox must exist when the manifest is loaded. Irradiance and
radiance files may be absent before generation. The BRDF LUT path normally points to the shared
`hdr/_common/brdf/brdfLUT.png`; the renderer can fall back to the copy bundled with gdx-gltf.

### Add an environment

1. Create `assets/hdr/<environment_name>/environment.json`.
2. Add one or more equirectangular EXR or HDR files under `source/`.
3. Add a 4x3 cubemap-cross PNG under `skybox/`.
4. Copy the v2 manifest structure, assign unique source variant ids, and select `activeVariant`.
5. Keep the standard six face names: `negx`, `posx`, `negy`, `posy`, `negz`, `posz`.
6. Generate and commit the derived PNG assets.
7. Select the environment by passing its preset name through `PbrPreviewView.environmentPreset`.

From the repository root, generate the complete default environment with:

```bash
./gradlew :engine:backend-gdx:generateHdrEnvironment --args="generate-hdr-env hdr/default/environment.json --all"
```

On Windows, use `.\gradlew.bat` in place of `./gradlew`. The Gradle task uses `assets/` as its
working directory.

The current generator is an MVP approximation. Irradiance uses heavy downsampling and box blur;
radiance uses progressive mip downsampling and blur. It produces stable preview assets but is not
physically exact. A future implementation can replace these operations with cosine-weighted
irradiance convolution and GGX importance-sampled radiance prefiltering without changing schema v2.
