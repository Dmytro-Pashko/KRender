# KRender Desktop LWJGL3 macOS Host

`desktop-lwjgl3-macos` is the macOS desktop launcher for the KRender SDK.

## Purpose

- Targets macOS desktop builds and Gradle runs.
- Owns the `-XstartOnFirstThread` check and local JVM restart policy.
- Creates the LWJGL3 application after macOS first-thread startup is satisfied.
- Owns local desktop runtime composition and secondary JVM launchers.

## Dependencies

```text
desktop-lwjgl3-macos
  -> core
  -> engine:backend-gdx
  -> engine:tools
  -> engine:scene-player
  -> LibGDX LWJGL3 desktop libraries
```

## Commands

```sh
./gradlew :desktop-lwjgl3-macos:run
./gradlew :desktop-lwjgl3-macos:run -Pkrender.scene=scene-player -Pkrender.scene.path=scenes/example.krscene
./gradlew :desktop-lwjgl3-macos:jarMac
```

Launcher logic is split by platform so macOS first-thread startup, runtime composition,
secondary launchers, and packaging stay readable in one module.

Duplication of `DesktopMain`, `DesktopApplication`, `MacOsLwjgl3Launcher`, and the secondary
JVM launcher helpers is intentional. When changing duplicated launcher/bootstrap files, review and
synchronize the same change across `desktop-lwjgl3-win`, `desktop-lwjgl3-macos`, and
`desktop-lwjgl3-linux` so platform startup/configuration stays local without a misleading shared
launcher module.
