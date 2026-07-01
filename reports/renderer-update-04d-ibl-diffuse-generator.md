# Renderer Update 04D — Simple IBL Diffuse Generator

Added `IblDiffuseGenerator` to produce the six manifest-defined irradiance PNG files.

The MVP generator prefers existing generated skybox faces and invokes the cubemap-cross splitter
when they are absent. Each face is bilinearly reduced to the configured size and receives four
strong clamped box-blur passes. After successful generation, the command sets
`irradiance.generated` to `true`.

Example:

```bash
./gradlew :engine:backend-gdx:generateHdrEnvironment --args="generate-hdr-env hdr/default/environment.json --irradiance"
```
