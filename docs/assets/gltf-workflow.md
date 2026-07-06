# glTF Asset Workflow

KRender loads `.gltf` and `.glb` models through the LibGDX backend and renders them through the
glTF / PBR path powered by gdx-gltf. Keep model texture references relative and include every
external buffer and texture when moving a `.gltf` asset into the project.

glTF / GLB are the primary 3D model formats for the current KRender workflow. The Legacy
LibGDX renderer remains available as a fallback and inspection path, but new 3D workflow changes
should target glTF / PBR first.

## Environment / IBL Layout

KRender PBR environments live in:

```text
environments/<environment_name>/
```

The default environment is:

```text
environments/default/
```

Current default environment files:

```text
environments/default/default.environment.json
```

Generated files:

```text
environments/default/generated/skybox/{face}.ktx
environments/default/generated/irradiance/irradiance.ktx
environments/default/generated/radiance/radiance_mip_00.ktx
environments/default/generated/radiance/radiance_mip_01.ktx
environments/default/generated/radiance/radiance_mip_02.ktx
environments/default/generated/radiance/radiance_mip_03.ktx
environments/default/generated/radiance/radiance_mip_04.ktx
shared/pbr/brdf_lut.ktx
```

IBL runtime resources:

- diffuse IBL = irradiance cubemap
- specular IBL = prefiltered radiance cubemap with mip levels
- BRDF LUT = shared lookup texture, not environment-specific

### Manifest schema version 1

Every Environment uses `<environment_name>.environment.json` with schema
`krender.environment` and version `1`. The manifest stores:

- runtime settings such as exposure, rotation, diffuse/specular intensity, and background mode;
- skybox / irradiance / radiance / BRDF LUT resource references;
- no persisted HDR/EXR source metadata.

All resource paths resolve relative to the manifest directory. Generated IBL resources may be absent temporarily; Environment Editor and Model
Viewer should stay functional and report clear warnings when full PBR inputs are missing.

### Add an environment

1. Create `assets/environments/<environment_name>/<environment_name>.environment.json`.
2. Define resource targets for skybox, irradiance, radiance, and BRDF LUT.
3. Generate skybox / irradiance / radiance from any external HDR/EXR source through Environment Editor when needed.
4. Import an existing BRDF LUT texture.
5. Open the manifest in Environment Editor for validation and runtime/background tuning.
6. Select the Environment asset in Model Viewer, Scene Editor, Scene Player, or game/runtime code.

From the repository root, generate the complete default environment with:

```bash
./gradlew :engine:backend-gdx:generateHdrEnvironment --args="generate-hdr-env environments/default/default.environment.json --all"
```

On Windows, use `.\gradlew.bat` in place of `./gradlew`. The Gradle task uses `assets/` as its
working directory.

## Supported material/debug channels

The current Model Viewer channel workflow supports:

- Combined
- Base Color
- Normal
- Metallic
- Roughness
- Occlusion
- Emissive
- Alpha
- UV Checker

## Current limitations

- The current HDR environment generator is MVP-quality:
  - irradiance uses strong downsampling + blur
  - radiance uses progressive mip downsampling + blur
- The generator produces stable committed assets, but it is not yet a physically exact cosine/GGX implementation.
- Direct EXR/HDR convolution is not the current default path; the generator currently works from the cubemap-cross/derived-face workflow first.

## Current unsupported or deferred features

- No physically exact cosine-weighted irradiance convolution yet
- No GGX importance-sampled radiance prefilter yet
- No per-environment BRDF LUT generation; BRDF LUT remains shared by design
- Environment generation is still outside the current Environment Editor MVP

The current generator is intentionally an MVP approximation. A future implementation can replace
these operations with physically correct cosine-weighted irradiance convolution and GGX
importance-sampled radiance prefiltering without changing schema v2.
