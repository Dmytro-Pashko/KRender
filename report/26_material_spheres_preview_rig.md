## Step

Added the backend-neutral Material Spheres preview rig definition.

## Changes

- Added `EnvironmentPreviewShapeType`.
- Added `EnvironmentPreviewObject` to describe:
  - object name
  - shape type
  - position
  - scale
  - radius
  - base color
  - metallic
  - roughness
- Added `MaterialSpheresPreviewRig` with:
  - preview asset path constant: `model/tests/MetalRoughSpheres.glb`
  - chrome sphere definition
  - matte gray sphere definition
  - metallic roughness ladder definitions
  - dielectric roughness ladder definitions
  - ground plane definition

## Notes

- The rig definition is tool-side and backend-neutral.
- The upcoming visual rendering step can reuse the existing glTF test asset for the sphere rows while still using this rig as the canonical preview layout/model description.

## Compilation command

`./gradlew :engine:tools:compileKotlin --no-daemon`

## Result

Compilation succeeded for `:engine:tools:compileKotlin`.
