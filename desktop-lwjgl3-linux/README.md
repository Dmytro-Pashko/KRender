# KRender Desktop LWJGL3 Linux Host

`desktop-lwjgl3-linux` is the Linux desktop launcher for the KRender SDK.

## Purpose

- Targets Linux desktop builds and Gradle runs.
- Owns the NVIDIA `__GL_THREADED_OPTIMIZATIONS=0` startup policy.
- Creates the LWJGL3 application after Linux startup preparation.
- Owns local desktop runtime composition and secondary JVM launchers.

## Dependencies

```text
desktop-lwjgl3-linux
  -> core
  -> engine:backend-gdx
  -> engine:tools
  -> engine:scene-player
  -> LibGDX LWJGL3 desktop libraries
```

## Commands

```sh
./gradlew :desktop-lwjgl3-linux:run
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=terrain-editor -Pkrender.terrain.path=terrains/terrain_02_small_flat.json
./gradlew :desktop-lwjgl3-linux:jarLinux
```

Launcher logic is split by platform so Linux driver startup policy, runtime composition,
secondary launchers, and packaging stay readable in one module.
