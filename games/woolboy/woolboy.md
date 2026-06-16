# Woolboy Desktop App

Woolboy now ships as a **standalone KRender SDK client application** instead of an in-core sandbox scene.

## Module layout

```text
core/                  # KRender engine / SDK
desktop-lwjgl3-win/    # Windows KRender desktop host application
desktop-lwjgl3-macos/  # macOS KRender desktop host application
desktop-lwjgl3-linux/  # Linux KRender desktop host application
android/               # Android launcher
games/
  woolboy/             # Woolboy gameplay/client module + bundled runtime assets
apps/
  woolboy-desktop/     # Standalone desktop launcher + executable JAR task
```

Gradle project paths:

- `:core`
- `:desktop-lwjgl3-win`
- `:desktop-lwjgl3-macos`
- `:desktop-lwjgl3-linux`
- `:android`
- `:games:woolboy`
- `:apps:woolboy-desktop`

Dependency direction:

```text
games:woolboy -> core
apps:woolboy-desktop -> games:woolboy
apps:woolboy-desktop -> core + desktop backend libraries
```

Module responsibilities:

- `core` keeps the shared engine API, scene/runtime systems, LibGDX backend adapter, editor tooling, and JVM tests.
- `games:woolboy` owns Woolboy-specific gameplay code and the curated runtime assets required by the demo.
- `apps:woolboy-desktop` owns the desktop bootstrap, default scene selection, runtime UI actor-factory wiring, and the executable JAR task.

Key source locations:

```text
games/woolboy/src/main/kotlin/                    # Woolboy gameplay code
games/woolboy/src/main/resources/assets/woolboy/ # Woolboy bundled runtime assets
apps/woolboy-desktop/src/main/kotlin/            # Desktop launcher/bootstrap code
```

## Woolboy assets

Woolboy runtime assets live under:

```text
games/woolboy/src/main/resources/assets/woolboy/
```

They are bundled inside the standalone Woolboy JAR. The Woolboy app does **not** package the full repository root `assets/` folder.

Module-local build outputs are generated under `games/woolboy/build/` and `apps/woolboy-desktop/build/`.

The bundled asset subtree includes the Woolboy scene/model/UI content plus the small set of runtime dependencies it needs
(terrain material library, terrain textures, runtime skin files, and skybox data) under `assets/woolboy/...`.

## Build the executable JAR

```powershell
Set-Location "D:\Projects\KRender SDK"
.\gradlew.bat :apps:woolboy-desktop:woolboyJar
```

Expected output:

```text
apps/woolboy-desktop/build/libs/woolboy-demo.jar
```

## Run the JAR

```powershell
Set-Location "D:\Projects\KRender SDK"
java -jar apps/woolboy-desktop/build/libs/woolboy-demo.jar
```

The JAR starts Woolboy by default and does not require `-Dkrender.scene=woolboy`.

## Validation notes

- The standalone app is built with `:apps:woolboy-desktop:woolboyJar` and produces `apps/woolboy-desktop/build/libs/woolboy-demo.jar`.
- The packaged JAR is expected to contain `assets/woolboy/...` entries rather than the full root editor/demo asset tree.
- Existing KRender editor/tool modules continue to build separately from the Woolboy app.

## Notes

- Woolboy is intended to demonstrate KRender SDK as an SDK and Woolboy as a separate client application.
- Woolboy unit tests are intentionally **not** added in this modularization task.
- Editor tools are **not** bundled into the Woolboy app.
- No JAR minimization is enabled yet.
- No ProGuard/R8 or shrinker setup is added in this task.
- No coverage threshold changes are introduced for Woolboy.
