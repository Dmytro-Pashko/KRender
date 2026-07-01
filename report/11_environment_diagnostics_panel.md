# Step 11 — Environment Diagnostics Panel

## Goal

Display Environment validation issues inside the tool.

## Changes

- Added `EnvironmentDiagnosticsPanel` — shows issues grouped by severity (Errors, Warnings, Info)
- Registered in `EnvironmentEditorScene`

## Diagnostics UI structure

- Status summary line: "Status: Valid/WARNING/ERROR (N issue(s))"
- "Refresh Validation" button
- Issues grouped by severity: Errors → Warnings → Info
- Each issue shows: `[CODE] message` with optional related path

## Refresh behavior

- Validation runs on load and on "Refresh Validation" button click
- Issues update after save/reload via toolbar panel

## Severity mapping

- `IssueSeverity.Error` → "Errors" section
- `IssueSeverity.Warning` → "Warnings" section
- `IssueSeverity.Info` → "Info" section

## Files touched

- `engine/tools/.../environmenteditor/EnvironmentDiagnosticsPanel.kt` — new
- `engine/tools/.../environmenteditor/EnvironmentEditorScene.kt` — panel registration

## Validation

Command:

```bash
./gradlew :engine:tools:compileKotlin --no-daemon
```

Result:

```text
SUCCESS
```

## Limitations / Follow-ups

- No "Select related source" or "Open generated maps panel" cross-panel actions yet
- No copy-to-clipboard for issue messages
