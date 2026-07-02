## Step

Added concise preview status/debug information to the Preview panel.

## Status info now shown

- Preview Mode: Material Spheres
- Environment name
- Environment id/path
- Validation status
- Skybox availability
- Irradiance availability
- Radiance availability
- BRDF LUT availability
- Active fallback mode

## Notes

- This step keeps the status block concise and avoids adding debug render channels.
- Detailed validation issues still belong in the Diagnostics panel.

## Compilation command

`./gradlew :engine:tools:compileKotlin --no-daemon`

## Result

Compilation succeeded for `:engine:tools:compileKotlin`.
