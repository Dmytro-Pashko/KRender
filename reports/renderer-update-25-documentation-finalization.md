# Step 25 — Documentation finalization

## Goal

Update root-level and workflow documentation so the repository clearly presents glTF / PBR as the main 3D direction and explains the HDR / IBL environment asset pipeline.

## Files changed

- `README.md`
- `docs/assets/gltf-workflow.md`
- `docs/tools.md`
- `docs/agents/backend-abstraction.md`
- `docs/agents/rendering-pipeline.md`
- `docs/agents/tools/model-viewer.md`

## Architecture notes

- Added a root README section that frames glTF / PBR as the primary renderer direction and LibGDX / Legacy as fallback/inspection mode.
- Documented HDR environment manifests, source variants, generated cubemap assets, and shared BRDF LUT in the root docs.
- Refined `docs/assets/gltf-workflow.md` to match the current manifest-driven environment flow and current generator limitations.
- Updated both user-facing and agent-facing docs to remove stale preview-era class names and terminology.

## Validation

- Command: `rg -n "PbrPreviewView|GdxGltfPbrPreviewRenderer|glTF PBR preview|PBR preview" README.md docs/assets/gltf-workflow.md docs/tools.md docs/agents/backend-abstraction.md docs/agents/rendering-pipeline.md docs/agents/tools/model-viewer.md`
- Result: No matches

## Commit

- Hash: `8ec8eadb`
- Message: `docs: finalize pbr and hdr workflow documentation`
