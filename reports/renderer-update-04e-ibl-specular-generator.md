# Renderer Update 04E — Simple IBL Specular Generator

Added `IblSpecularGenerator` for a stable MVP prefiltered radiance chain.

The generator reads or creates the six skybox faces, emits every manifest-defined mip and face,
and progressively reduces dimensions and increases blur with roughness. With the default
configuration this produces 60 PNG files from 512×512 at mip 0 through 1×1 at mip 9. Mip 0
remains the sharpest source. Successful generation sets `radiance.generated` to `true`.

Examples:

```bash
./gradlew :engine:backend-gdx:generateHdrEnvironment --args="generate-hdr-env hdr/default/environment.json --radiance"
./gradlew :engine:backend-gdx:generateHdrEnvironment --args="generate-hdr-env hdr/default/environment.json --all"
```

The combined command runs skybox splitting, diffuse irradiance generation, specular radiance
generation, and manifest flag updates.
