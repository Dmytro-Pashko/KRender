# Animation Viewer — Agent Context

> Read `AGENTS.md` first. This file covers only the Animation Viewer tool.

## Purpose

Inspect and play the animation clips of a single skinned/animated model, and visualize its
skeleton/pose. A focused sibling of the Model Viewer dedicated to animation and rigging.

## Current Implementation

- Scene: `engine/tools/.../engine/tools/animationviewer/AnimationViewerScene.kt`.
- Tool internals: `engine/tools/.../engine/tools/animationviewer/`.
- Launched as a separate JVM window by `Lwjgl3EditorToolLauncher.launchAnimationViewer(path)`
  (`krender.scene=animation-viewer`, `krender.model.path=<path>`). Not a default action in the
  Asset Browser (it is an "open with" alternative for models).

## Main Files

| File | Responsibility |
|---|---|
| `engine/tools/animationviewer/AnimationViewerScene.kt` | Builds camera/light/model, systems, and 7 ImGui panels. |
| `engine/tools/animationviewer/AnimationViewerState.kt` | Viewer state: selected clip, time, play state, speed, loop, skeleton options. |
| `engine/tools/animationviewer/AnimationViewerSystems.kt` | `AnimationViewerSystem`, `AnimationViewerModelRenderSystem`, `AnimationViewerSkeletonRenderSystem`, `AnimationViewerBoundingBoxSystem`, `AnimationViewerViewportGuideSystem`. |
| `engine/tools/animationviewer/AnimationViewerOperations.kt` | UI-driven playback/selection actions. |
| `engine/tools/animationviewer/AnimationViewerPanels.kt` | Panel implementations. |
| `engine/tools/animationviewer/AnimationViewerUiLayoutConfig.kt` | `AnimationViewerUiLayoutDefaults`. |
| `engine/animation/AnimationComponent.kt` | Animation component data. |

## Main Classes

| Class | Responsibility |
|---|---|
| `AnimationViewerScene` | Scene composition + first-frame/error logging. |
| `AnimationViewerState` | Source of truth for playback + skeleton display. |
| `AnimationViewerSystem` | Advances/applies playback state; handles exit. |
| `AnimationViewerModelRenderSystem` | Emits `DrawModel` with an `AnimationPlaybackView`. |
| `AnimationViewerSkeletonRenderSystem` | Emits skeleton lines from sampled `ModelBonePose`s. |

## UI Panels

`AnimationViewerToolbarPanel`, `AnimationViewerViewportPanel`, `AnimationViewerAnimationsPanel`,
`AnimationViewerPlaybackPanel`, `AnimationViewerSkeletonPanel`, `AnimationViewerLoadingPanel`,
`LogsPanel` (7 total).

## Engine Services Used

`engine.assets` (`modelInfo.animations`, `modelSkeleton`, `modelSkeletonPose`),
`engine.input` (camera), `engine.ui`, `engine.logger`/`engine.logs`, `engine.requestExit`.

## Data Flow

1. `requiredAssets` queues the model.
2. Playback state lives in `AnimationViewerState`; the playback panel drives time/speed/loop.
3. `AnimationViewerModelRenderSystem` submits `DrawModel` with an `AnimationPlaybackView`
   (clip name, time, loop, playing, speed); the backend samples the pose for rendering.
4. `AnimationViewerSkeletonRenderSystem` requests `AssetService.modelSkeletonPose(...)` and draws
   the bone hierarchy as lines (sampling uses dedicated asset-scoped caches, separate from the
   renderer's per-entity instance caches).

## Lifecycle

`show()` → load layout, build state/operations, create camera + ambient light + animated model
entity, add systems (guide, viewer, UI, camera, bounding box, model render, skeleton render).
`hide()` releases cursor capture. Uses `SceneConfigPresets.EditorTool`.

## Supported Asset Types

Animated/skinned models in glTF (`.gltf`/`.glb`) and libGDX (`.g3dj`/`.g3db`) formats. Static
models load but expose no clips.

## Current Features

- Animation clip list with selection (`ModelAnimationInfo`).
- Playback controls: play/pause, time scrub, speed, loop.
- Skeleton/pose visualization with backend-neutral `ModelBonePose` sampling.
- Bounds/grid/axes guides shared with the other viewers.

## Missing / Incomplete Features

- No animation blending or layering.
- No retargeting or editing — playback/inspection only.
- No timeline keyframe view.

## Known Problems

- Pose sampling for glTF vs static models takes different backend paths
  (`sampleGltfSkeletonPose` vs `sampleStaticModelSkeletonPose`); behavior can differ by format.
- Same verbose per-frame logging style as the Model Viewer.

## Extension Points

- Add a panel via `addPanel(uiSystem, name, panel)` in `createUiSystem`.
- Extend `AnimationPlaybackView` (core) + backend sampling to add playback features.
- Extend `ModelBonePose`/`ModelSkeletonInfo` (core) + backend extraction for richer skeleton data.

## Safe Change Rules

- Keep skeleton/animation data backend-neutral (`ModelBonePose`, `ModelSkeletonInfo`,
  `ModelAnimationInfo`); never reach into gdx-gltf types from the tool.
- Do not mutate the renderer's per-entity instance caches from sampling code — use the
  asset-scoped pose caches that `GdxModelPoseSampler` maintains.

## Recommended Improvements

- Unify common viewer scaffolding with the Model Viewer.
- Add a scrub timeline panel and clip looping markers.

## Related Code Patterns

- Mirrors Model Viewer composition closely; use it as the reference for shared concerns.
- Skeleton sampling caches: see `GdxModelPoseSampler`.
