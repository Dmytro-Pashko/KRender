## Step

Applied the current Environment asset and generated-map availability to the Material Spheres preview.

## Changes

- Extended `GltfRendererSettings` with:
  - `environmentCacheKey`
  - `skyboxIntensity`
  - `ambientIntensity`
- Updated the shared glTF renderer/backend path to:
  - reload environment presets when the preview cache key changes
  - apply live ambient intensity from editor state
  - apply live skybox intensity to the rendered skybox
- Added `EnvironmentPreviewAvailability` and controller helpers to:
  - inspect generated skybox / irradiance / radiance / BRDF LUT availability
  - decide whether the preview may show the skybox
  - describe fallback mode
  - build clear warning messages
- Updated the preview render system to build glTF renderer settings from the current editor environment state.
- Updated the Preview panel to show missing-resource fallback warnings instead of the old generic message.

## Environment fields applied

- `exposure`
- `rotationDegrees`
- `skyboxVisible`
- `skyboxIntensity`
- `diffuseIntensity`
- `specularIntensity`
- `backgroundMode`

## Missing-resource behavior

- Missing skybox now disables the skybox background for the preview and reports a neutral-background fallback.
- Missing irradiance, radiance, or BRDF LUT do not crash the preview.
- The Preview panel now surfaces specific warning messages for each missing generated resource.

## Compilation command

`./gradlew :core:compileKotlin :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin --no-daemon`

## Result

Compilation succeeded for `:core:compileKotlin :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin`.
