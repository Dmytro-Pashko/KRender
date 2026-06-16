# KRender Desktop LWJGL3 Windows Host

`desktop-lwjgl3-win` is the Windows desktop launcher for the KRender SDK.

## Purpose

- Targets Windows desktop builds and Gradle runs.
- Owns the Windows LWJGL3 native extraction/temp-dir startup workaround.
- Creates the LWJGL3 application after Windows startup preparation.
- Owns local desktop runtime composition and secondary JVM launchers.

## Dependencies

```text
desktop-lwjgl3-win
  -> core
  -> engine:backend-gdx
  -> engine:tools
  -> engine:scene-player
  -> LibGDX LWJGL3 desktop libraries
```

## Commands

```powershell
.\gradlew.bat :desktop-lwjgl3-win:run
.\gradlew.bat :desktop-lwjgl3-win:run -Pkrender.scene=model-viewer -Pkrender.model.path=model/wool_boy_animated.glb
.\gradlew.bat :desktop-lwjgl3-win:jarWin
```

Launcher logic is split by platform so Windows startup workarounds, runtime composition,
secondary launchers, and packaging stay readable in one module.

Duplication of `DesktopMain`, `DesktopApplication`, `WinLwjgl3Launcher`, and the secondary
JVM launcher helpers is intentional. When changing duplicated launcher/bootstrap files, review and
synchronize the same change across `desktop-lwjgl3-win`, `desktop-lwjgl3-macos`, and
`desktop-lwjgl3-linux` so platform startup/configuration stays local without a misleading shared
launcher module.
