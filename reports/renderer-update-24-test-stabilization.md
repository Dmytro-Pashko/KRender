# Step 24 — Test stabilization

## Goal

Check whether Model Viewer tests became stale or unstable during the PBR transition and isolate them only if needed.

## Files changed

- `reports/renderer-update-24-test-stabilization.md`

## Architecture notes

- Reviewed the current Model Viewer test surface and verified that the remaining tests are logic-focused JVM tests, not renderer/GL integration tests.
- No Model Viewer tests needed to be disabled or removed in this stabilization pass.
- The PBR migration risk remains concentrated in backend rendering behavior, which is intentionally validated through compilation and manual tool verification rather than GL-dependent JVM tests.

## Validation

- Command: `./gradlew.bat --no-daemon --console=plain :engine:tools:compileTestKotlin`
- Result: Passed

## Commit

- Hash: `<pending>`
- Message: `test: document model viewer test stabilization status`
