# Static Analysis

KRender now has a root static-analysis workflow for Kotlin formatting, static analysis, and existing JVM verification.

## Commands

Run the full check workflow from the repository root:

```bash
./scripts/static-analysis.sh
```

Run safe Kotlin auto-formatting first, then rerun verification:

```bash
./scripts/static-analysis.sh --fix
```

You can also run the Gradle tasks directly:

```bash
./gradlew ktlintCheck detekt test
./gradlew ktlintFormat
./gradlew staticAnalysis
./gradlew check
```

## What Runs

- `ktlint` checks Kotlin source formatting and style for `**/src/**/*.kt` and repository `.kts` files, excluding `build/`, `.gradle/`, and `generated/`.
- `detekt` checks Kotlin code quality, complexity, and code smells using `config/detekt/detekt.yml`.
- `test` runs the existing JVM test suite, including architecture verification such as `BackendBoundaryTest`.

## Reports

The wrapper script always writes a summary report to:

```text
build/reports/static-analysis/summary.md
```

Primary report outputs:

- `build/reports/detekt/detekt.html`
- `build/reports/detekt/detekt.md`
- `build/reports/detekt/detekt.xml`
- `build/reports/detekt/detekt.sarif`
- `core/build/reports/tests/test/index.html`
- `build/reports/static-analysis/logs/*.log`

## Detekt Baseline

Detekt uses:

```text
config/detekt/baseline.xml
```

The baseline is temporary technical debt for pre-existing findings. Keep `ignoreFailures = false` so new findings stay visible. Regenerate the baseline only when you intentionally accept the current finding set:

```bash
./gradlew detektBaseline
```

Reduce the baseline over time by fixing findings and then regenerating it from a cleaner codebase.

## Boundary Rules

Static analysis must not weaken the core/backend boundary. `BackendBoundaryTest` remains part of verification, and new allowlist entries should not be added just to make the workflow pass.

## CI Readiness

As of June 11, 2026, this repository does not contain `.github/workflows/*.yml`. When CI is added, run:

```bash
./scripts/static-analysis.sh
```

Keep static-analysis setup changes separate from large runtime or architecture refactors so failures stay attributable to the tooling rollout.
