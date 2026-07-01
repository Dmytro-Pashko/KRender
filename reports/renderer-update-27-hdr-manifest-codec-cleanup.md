# Step 27 — HDR manifest codec cleanup

## Goal

Replace the hand-written `HdrEnvironmentManifestCodec` (~200 lines of manual JSON
decode/encode logic + ~30 lines of private helper extensions) with standard
`kotlinx.serialization` `@Serializable` annotations. Keep the manifest schema, validation,
and public API intact.

## Files changed

- `build.gradle.kts` — added `kotlin-serialization` compiler plugin to buildscript classpath.
- `core/build.gradle.kts` — applied `org.jetbrains.kotlin.plugin.serialization` plugin.
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/hdr/HdrEnvironmentManifest.kt` — annotated all manifest data classes and enums with `@Serializable`; replaced the hand-written codec body with `Json.decodeFromString` / `Json.encodeToString`.

No consumer changes required — `HdrEnvironmentManifestCodec.decode()` and `.encode()` retain
the same signatures.

## What was removed

- 13 private decode/encode methods inside `HdrEnvironmentManifestCodec` (`decodeSource`,
  `decodeSourceVariant`, `decodeSkybox`, `decodeIrradiance`, `decodeRadiance`,
  `decodeBrdfLut`, `decodeDefaults`, `encodeSource`, `encodeSourceVariant`, `encodeSkybox`,
  `encodeIrradiance`, `encodeRadiance`, `encodeBrdfLut`, `encodeBrdfLut`, `encodeDefaults`).
- 11 private file-level helper extensions (`requiredObject`, `optionalObject`,
  `requiredArray`, `requiredString`, `optionalString`, `requiredInt`, `optionalInt`,
  `requiredDouble`, `requiredBoolean`, `requiredEnum`, `optionalEnum`, `requiredStringList`,
  `toJsonArray`).
- 16 unused imports (`JsonArray`, `JsonObject`, `JsonPrimitive`, `buildJsonArray`,
  `buildJsonObject`, `contentOrNull`, `doubleOrNull`, `intOrNull`, `booleanOrNull`,
  `jsonArray`, `jsonObject`, `jsonPrimitive`, `put`, `KRenderJson`).

Net: **390 → 214 lines** (−176 lines, −45%).

## What replaced it

- `@Serializable` annotation on all 12 manifest data classes and enums.
- `= null` default added to `HdrEnvironmentManifest.skybox` (was nullable without default;
  now optional during deserialization, matching the old codec's `optionalObject` behavior).
- `HdrEnvironmentManifestCodec` reduced to a thin wrapper:
  - Private `Json` instance with `encodeDefaults = true` to preserve full-field output.
  - `decode()` delegates to `Json.decodeFromString<HdrEnvironmentManifest>()` with a
    try-catch wrapping serialization errors for readability.
  - `encode()` delegates to `Json.encodeToString()`.
- `HdrManifestJson` private val — a dedicated `Json` config matching the KRender Pretty
  conventions but independent of `KRenderJson.Pretty` (avoids coupling and allows
  `encodeDefaults = true` without affecting other formats).

## Validation
- Command: `./gradlew :core:compileKotlin :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin :core:test :core:ktlintCheck`
- Result: all passed (exit code 0).

## Commit
- Hash: 61dbda0
- Message: `assets: replace handwritten hdr manifest codec with serialization`
