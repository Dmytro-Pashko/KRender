# Renderer Update 04C — Skybox Cubemap-Cross Splitter

Added a headless `CubemapCrossSplitter` and the `generateHdrEnvironment` Gradle command.

The splitter reads the manifest's 4x3 cubemap cross, validates divisibility and square face
dimensions, maps the conventional cross regions to the manifest face names, and writes all six
PNG files using `skybox.generatedFacesPath`.

Example:

```bash
./gradlew :engine:backend-gdx:generateHdrEnvironment --args="generate-hdr-env hdr/default/environment.json --skybox"
```
